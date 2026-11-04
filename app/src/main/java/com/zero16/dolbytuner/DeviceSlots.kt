package com.zero16.dolbytuner

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * 设备槽位：扬声器 / 有线（耳机、USB）/ 蓝牙
 *
 * 只做三件事：
 *   1) 判断当前正在出声的输出来自哪一类设备
 *   2) 每类设备存一份音效页参数快照
 *   3) 插拔/切换设备时由界面决定要不要自动套用
 */
object DeviceSlots {

    const val SPEAKER = 0
    const val WIRED = 1
    const val BT = 2

    val NAMES = arrayOf("扬声器", "有线 / USB", "蓝牙")

    private fun key(slot: Int) = "slot_$slot"

    fun save(prefs: SharedPreferences, slot: Int, payload: String) {
        prefs.edit().putString(key(slot), payload).apply()
    }

    fun load(prefs: SharedPreferences, slot: Int): String? =
        prefs.getString(key(slot), null)

    fun clear(prefs: SharedPreferences, slot: Int) {
        prefs.edit().remove(key(slot)).apply()
    }

    fun has(prefs: SharedPreferences, slot: Int): Boolean =
        !prefs.getString(key(slot), null).isNullOrBlank()

    /** 当前输出设备归到哪个槽（有线优先，其次蓝牙，最后扬声器） */
    fun currentSlot(ctx: Context): Int {
        return runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val wired = intArrayOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL
            )
            val bt = intArrayOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER
            )
            when {
                outs.any { it.type in wired } -> WIRED
                outs.any { it.type in bt } -> BT
                else -> SPEAKER
            }
        }.getOrDefault(SPEAKER)
    }
}