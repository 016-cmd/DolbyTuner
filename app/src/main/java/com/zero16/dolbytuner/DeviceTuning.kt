package com.zero16.dolbytuner

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

/** 当前播放设备（key 用于绑定，name 用于显示） */
data class OutputDevice(val key: String, val name: String)

/** 设备绑定：设备 key → 预设名 */
data class DeviceBinding(val key: String, val deviceName: String, val presetName: String)

/**
 * 检测当前正在出声的设备。
 *
 * 优先用 getAudioDevicesForAttributes（Android 13+，拿到的就是媒体实际路由），
 * 老系统退化为 getDevices 按优先级挑一个。
 */
fun detectOutputDevice(ctx: Context): OutputDevice {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return OutputDevice("type:unknown", "未知设备")

    val routed: List<AudioDeviceInfo> = if (Build.VERSION.SDK_INT >= 33) {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            am.getAudioDevicesForAttributes(attrs).orEmpty()
        }.getOrDefault(emptyList())
    } else emptyList()

    val all = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }
        .getOrDefault(emptyList())
        .filter { it.isSink }

    val dev = routed.firstOrNull { it.isSink }
        ?: all.firstOrNull { it.type in BT_TYPES }
        ?: all.firstOrNull { it.type in USB_TYPES }
        ?: all.firstOrNull { it.type in WIRED_TYPES }
        ?: all.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        ?: all.firstOrNull()
        ?: return OutputDevice("type:unknown", "未知设备")

    return toOutputDevice(dev)
}

private fun toOutputDevice(dev: AudioDeviceInfo): OutputDevice {
    val rawName = dev.productName?.toString()?.trim().orEmpty()
    return when (dev.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
            OutputDevice("builtin:speaker", "手机扬声器")
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ->
            OutputDevice("builtin:earpiece", "听筒")
        in WIRED_TYPES ->
            OutputDevice("wired:any", if (rawName.isBlank()) "有线耳机" else rawName)
        in BT_TYPES ->
            OutputDevice("bt:" + norm(rawName.ifBlank { "bluetooth" }), rawName.ifBlank { "蓝牙设备" })
        in USB_TYPES ->
            OutputDevice("usb:" + norm(rawName.ifBlank { "usb" }), rawName.ifBlank { "USB 音频设备" })
        else ->
            OutputDevice("type:${dev.type}", rawName.ifBlank { typeLabel(dev.type) })
    }
}

private fun norm(s: String) = s.trim().lowercase(Locale.ROOT)

private fun typeLabel(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "手机扬声器"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙设备"
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB 音频设备"
    else -> "音频设备"
}

private val BT_TYPES = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER
)
private val USB_TYPES = setOf(
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE
)
private val WIRED_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES
)

/** 绑定表存 SharedPreferences：{"key":{"name":"设备名","preset":"预设名"}} */
object DeviceTuningStore {
    private const val PREF = "dolby_tuner"
    private const val KEY = "device_bindings"

    fun load(ctx: Context): List<DeviceBinding> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().mapNotNull { k ->
                val o = obj.optJSONObject(k) ?: return@mapNotNull null
                val preset = o.optString("preset").ifBlank { return@mapNotNull null }
                DeviceBinding(k, o.optString("name", k), preset)
            }.toList()
        }.getOrDefault(emptyList())
    }

    fun find(ctx: Context, key: String): DeviceBinding? = load(ctx).firstOrNull { it.key == key }

    fun bind(ctx: Context, device: OutputDevice, presetName: String) {
        val map = load(ctx).associateBy { it.key }.toMutableMap()
        map[device.key] = DeviceBinding(device.key, device.name, presetName)
        write(ctx, map.values.toList())
    }

    fun unbind(ctx: Context, key: String) {
        write(ctx, load(ctx).filterNot { it.key == key })
    }

    private fun write(ctx: Context, list: List<DeviceBinding>) {
        val obj = JSONObject()
        list.forEach { b ->
            obj.put(b.key, JSONObject().apply {
                put("name", b.deviceName)
                put("preset", b.presetName)
            })
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, obj.toString()).apply()
    }
}

/** 当前设备状态：主层监听写入，GEQ 卡片读取 */
object DeviceState {
    var device by mutableStateOf(OutputDevice("", "未检测"))
    var bindings by mutableStateOf<List<DeviceBinding>>(emptyList())
    var changeTick by mutableIntStateOf(0)

    val binding: DeviceBinding?
        get() = bindings.firstOrNull { it.key == device.key }

    fun sync(ctx: Context) {
        device = detectOutputDevice(ctx)
        bindings = DeviceTuningStore.load(ctx)
    }

    fun notifyApplied() {
        changeTick++
    }
}