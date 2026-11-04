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

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = scheduleCheck()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = scheduleCheck()
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        register()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!registered) register()
        // 设置页「重新下发监听通知」：按当前设备/绑定刷新一次常驻通知
        if (intent?.action == ACTION_REFRESH) refreshNotice()
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
        if (!prefs.getBoolean("device_service", false)) {
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
                    applyPreset(this@DeviceAutomationService, preset)
                } else {
                    // 未绑定设备：恢复平直 + 默认音效
                    resetToDefault(this@DeviceAutomationService)
                }
                if (err == null) {
                    prefs.edit().putString("device_last_applied", device.key).apply()
                    updateNotification(device.name, preset?.name ?: "默认（平直）")
                }
            }
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
        private const val CHANNEL_ID = "device_automation"
        private const val NOTIFICATION_ID = 1001
    }
}