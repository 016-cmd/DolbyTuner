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
import android.media.AudioRecordingConfiguration
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
    private var recCallback: AudioManager.AudioRecordingCallback? = null
    private var telephonyInCall = false
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
        unregisterRecordingDetector()
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

    // ── 通话暂停：普通电话 + 微信/QQ 等 VoIP（靠音频模式判定） ──
    private fun syncPhoneListener() {
        val on = getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
            .getBoolean("call_mute_dp", false)
        if (on) {
            registerPhoneListener()
            registerRecordingDetector()
        } else {
            unregisterPhoneListener()
        unregisterRecordingDetector()
            unregisterRecordingDetector()
            applyCallMute(false)
        }
    }

    private fun registerPhoneListener() {
        if (phoneListener != null) return
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        @Suppress("DEPRECATION")
        val l = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                telephonyInCall = state == TelephonyManager.CALL_STATE_RINGING ||
                        state == TelephonyManager.CALL_STATE_OFFHOOK
                evaluateCallMute()
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

    /**
     * 微信/QQ 语音通话走的是 VoIP，TelephonyManager 不会报 OFFHOOK，
     * 只会把 AudioManager 切到 MODE_IN_COMMUNICATION 并占用麦克风。
     * 所以监听「录音配置变化」这一事件，再去看音频模式。
     */
    private fun registerRecordingDetector() {
        if (recCallback != null) return
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val cb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(
                configs: List<AudioRecordingConfiguration>
            ) {
                handler.removeCallbacks(voipConfirm)
                if (configs.isEmpty()) {
                    // 麦克风释放：大概率通话结束
                    evaluateCallMute()
                    return
                }
                val am2 = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val mode = am2?.mode ?: AudioManager.MODE_NORMAL
                if (mode == AudioManager.MODE_IN_CALL ||
                    mode == AudioManager.MODE_IN_COMMUNICATION
                ) {
                    applyCallMute(true)
                } else {
                    // 部分机型微信通话不切音频模式，只占麦克风：
                    // 6 秒后麦克风还在被占用才算通话（避开「按住说话」这类短录音）
                    handler.postDelayed(voipConfirm, 6000)
                }
            }
        }
        val ok = runCatching {
            am.registerAudioRecordingCallback(cb, handler)
            true
        }.getOrDefault(false)
        if (ok) recCallback = cb
    }

    private fun unregisterRecordingDetector() {
        val cb = recCallback ?: return
        recCallback = null
        runCatching {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.unregisterAudioRecordingCallback(cb)
        }
    }

    /** 兜底确认：麦克风被持续占用也算通话中 */
    private val voipConfirm = Runnable {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mode = am?.mode ?: AudioManager.MODE_NORMAL
        val inCall = mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION
        val recording = runCatching {
            @Suppress("DEPRECATION")
            am?.activeRecordingConfigurations?.isNotEmpty() == true
        }.getOrDefault(false)
        applyCallMute(telephonyInCall || inCall || recording)
    }

    /** 电话状态 + 音频模式，任一处于通话态就算在通话里 */
    private fun evaluateCallMute() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mode = am?.mode ?: AudioManager.MODE_NORMAL
        val voip = mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION
        applyCallMute(telephonyInCall || voip)
    }

    private fun applyCallMute(inCall: Boolean) {
        val prefs = getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("call_mute_dp", false)) {
            CallGuard.muted.value = false
            return
        }
        if (inCall) {
            if (!dpMutedByCall && DynProc.running) {
                dpMutedByCall = true
                scope.launch { runCatching { DynProc.stop() } }
            }
            // 通话中：记录里开着就在 UI 上显示为关
            CallGuard.muted.value = DpRestore.isOn(this)
        } else {
            if (dpMutedByCall) {
                dpMutedByCall = false
                scope.launch { runCatching { DpRestore.apply(this@DeviceAutomationService) } }
            }
            CallGuard.muted.value = false
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