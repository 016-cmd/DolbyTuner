package com.zero16.dolbytuner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 后台设备监听：息屏/切到别的 APP 时，插拔耳机或蓝牙连断仍然会自动套用对应预设。
 * 只有设置里打开「后台设备监听」才会常驻。
 */
class DeviceAutomationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var registered = false
    private var pending: Runnable? = null
    /** 通话期间临时停掉音效页（不写偏好，通话结束照原样恢复） */
    private var phoneListener: PhoneStateListener? = null
    private var dpMutedByCall = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = scheduleCheck()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = scheduleCheck()
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        register()
        // 服务一起来就把音效页按本地记录重建：APP 退出后 DP 仍然生效
        if (DpRestore.isOn(this)) {
            scope.launch { runCatching { DpRestore.apply(this@DeviceAutomationService) } }
        }
        syncPhoneListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!registered) register()
        syncPhoneListener()
        when (intent?.action) {
            // 设置页「重新下发监听通知」：按当前设备/绑定刷新一次常驻通知
            ACTION_REFRESH -> refreshNotice()
            // 开机广播 / 手动恢复：按本地记录把音效页整套写回
            ACTION_RESTORE -> scope.launch {
                runCatching { DpRestore.apply(this@DeviceAutomationService) }
            }
        }
        return START_STICKY
    }

    private fun refreshNotice() {
        scope.launch {
            runCatching {
                val device = detectOutputDevice(this@DeviceAutomationService)
                val binding = DeviceTuningStore.find(this@DeviceAutomationService, device.key)
                updateNotification(device.name, binding?.presetName ?: "未绑定（平直）")
            }
        }
    }

    override fun onDestroy() {
        pending?.let { handler.removeCallbacks(it) }
        unregisterPhoneListener()
        if (registered) {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            runCatching { am?.unregisterAudioDeviceCallback(callback) }
            registered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun register() {
        if (registered) return
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            am.registerAudioDeviceCallback(callback, handler)
            registered = true
        }
        scheduleCheck()
    }

    /** 设备事件往往连续来几个，延迟合并成一次 */
    private fun scheduleCheck() {
        pending?.let { handler.removeCallbacks(it) }
        val r = Runnable { checkAndApply() }
        pending = r
        handler.postDelayed(r, 500)
    }

    private fun checkAndApply() {
        val prefs = getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
        val keepAlive = prefs.getBoolean("keepalive_job", false)
        val callMute = prefs.getBoolean("call_mute_dp", false)
        if (!prefs.getBoolean("device_service", false) && !keepAlive && !callMute) {
            stopSelf()
            return
        }
        scope.launch {
            runCatching {
                val device = detectOutputDevice(this@DeviceAutomationService)
                val binding = DeviceTuningStore.find(this@DeviceAutomationService, device.key)
                // 通知永远先跟着「当前设备」走：就算这次不需要重新套用参数，
                // 通知也不能停留在上一个设备的名字上。
                updateNotification(device.name, binding?.presetName ?: "未绑定（平直）")
                if (prefs.getString("device_last_applied", null) == device.key) return@runCatching
                val preset = binding?.let { b ->
                    loadPresets(this@DeviceAutomationService).firstOrNull { it.name == b.presetName }
                }
                val err = if (preset != null) {
                    val r = applyPreset(this@DeviceAutomationService, preset)
                    // 预设里不含音效页参数时，把 DP 复位，避免沿用上一个设备的声音
                    if (r == null && preset.scope == SCOPE_DOLBY) {
                        resetDpOnly(this@DeviceAutomationService)
                    }
                    r
                } else {
                    // 未绑定设备：恢复平直 + 默认音效（音效页也跟着复位）
                    val r = resetToDefault(this@DeviceAutomationService)
                    if (r == null) resetDpOnly(this@DeviceAutomationService)
                    r
                }
                if (err == null) {
                    prefs.edit().putString("device_last_applied", device.key).apply()
                    updateNotification(device.name, preset?.name ?: "默认（平直）")
                }
            }
        }
    }

    // ── 通话监听：来电/通话中临时关掉音效页，挂断后按本地记录恢复 ──
    private fun syncPhoneListener() {
        val on = getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
            .getBoolean("call_mute_dp", false)
        if (on) registerPhoneListener() else unregisterPhoneListener()
    }

    private fun registerPhoneListener() {
        if (phoneListener != null) return
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        @Suppress("DEPRECATION")
        val l = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING,
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (!dpMutedByCall && DynProc.running) {
                            dpMutedByCall = true
                            scope.launch { runCatching { DynProc.stop() } }
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (dpMutedByCall) {
                            dpMutedByCall = false
                            scope.launch { runCatching { DpRestore.apply(this@DeviceAutomationService) } }
                        }
                    }
                }
            }
        }
        val ok = runCatching {
            tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            true
        }.getOrDefault(false)
        if (ok) phoneListener = l
    }

    private fun unregisterPhoneListener() {
        val l = phoneListener ?: return
        phoneListener = null
        runCatching {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            tm?.listen(l, PhoneStateListener.LISTEN_NONE)
        }
    }

    private fun ensureChannel(): NotificationChannel {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID, "设备自动切换", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "监听播放设备变化并按绑定套用预设"
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
        return ch
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Dolby Tuner · 设备监听中")
            .setContentText("插拔耳机 / 蓝牙连断时自动套用绑定预设")
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(deviceName: String, presetName: String) {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                NOTIFICATION_ID,
                Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("已切换：$deviceName")
                    .setContentText("套用预设：$presetName")
                    .setOngoing(true)
                    .build()
            )
        }
    }

    companion object {
        /** 设置页点「重新下发监听通知」时带的 action */
        const val ACTION_REFRESH = "com.zero16.dolbytuner.ACTION_REFRESH_NOTICE"
        /** 开机 / 手动：按本地记录写回音效页 */
        const val ACTION_RESTORE = "com.zero16.dolbytuner.ACTION_RESTORE_DP"
        private const val CHANNEL_ID = "device_automation"
        private const val NOTIFICATION_ID = 1001
    }
}