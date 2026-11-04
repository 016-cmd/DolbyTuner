package com.zero16.dolbytuner

import android.media.audiofx.AudioEffect
import android.util.Log
import java.util.UUID

/**
 * 预设混响（PresetReverb，插入式）
 *
 * 为什么单独做一套：
 *   EnvironmentalReverb（我们原来的 10 参数混响）在部分机型上插入链路后听感非常淡；
 *   系统 audio_effects.xml 里还有一条 reverb_pre_ins = 172cdf00-…，
 *   它对应系统自带的「预设混响」，只有 6 个档位但听感明显、稳定。
 *
 * 实现要点：
 *   · AOSP 的 PresetReverb 类写死了 aux 版 UUID(47382d60…)，这类机上不进链路，
 *     所以还是用 AudioEffect(insertUuid) 反射创建；
 *   · 档位参数 ID = 0（PARAM_PRESET），值 1..6：
 *     1 小房间 2 中房间 3 大房间 4 中厅 5 大厅 6 金属板
 */
object DynPresetReverb {

    private const val TAG = "DynPresetReverb"

    private const val UUID_PRE_AUX = "f29a1400-a3bb-11df-8ddc-0002a5d5c51b"
    private const val UUID_PRE_INS = "172cdf00-a3bc-11df-a72f-0002a5d5c51b"

    /** 显示名（与 PRESET_VALUES 一一对应） */
    val PRESET_NAMES = listOf("小房间", "中房间", "大房间", "中厅", "大厅", "金属板")

    /** 真正写给 DSP 的枚举值（AOSP PresetReverb.Presets：SMALLROOM=1 … PLATE=6） */
    private val PRESET_VALUES = intArrayOf(1, 2, 3, 4, 5, 6)

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var status: String = "未开启"
        private set

    private var fx: AudioEffect? = null
    private var last: Int = 3

    @Synchronized
    fun start(preset: Int): Boolean {
        stop()
        last = preset.coerceIn(0, PRESET_NAMES.size - 1)
        val f = newInsert()
        if (f == null) {
            status = "预设混响不可用 · 本机没有该效果"
            return false
        }
        if (!applyPreset(f, last)) {
            runCatching { f.release() }
            status = "预设混响写入失败"
            return false
        }
        runCatching { f.setEnabled(true) }
        fx = f
        running = true
        val ctrl = runCatching { f.hasControl() }.getOrDefault(false)
        status = if (ctrl) "预设混响 · ${PRESET_NAMES[last]}" else "预设混响 · 等待控制权"
        Log.i(TAG, "preset=$last hasControl=$ctrl")
        return true
    }

    @Synchronized
    fun setPreset(preset: Int) {
        val f = fx ?: return
        last = preset.coerceIn(0, PRESET_NAMES.size - 1)
        if (applyPreset(f, last)) status = "预设混响 · ${PRESET_NAMES[last]}"
    }

    @Synchronized
    fun stop() {
        runCatching { fx?.setEnabled(false) }
        runCatching { fx?.release() }
        fx = null
        running = false
        status = "未开启"
    }

    fun current(): Int = last

    /** 卡片标题用的短状态 */
    val shortStatus: String
        get() = when {
            status.startsWith("预设混响 · ") -> status.removePrefix("预设混响 · ")
            status.contains("等待控制权") -> "等待控制权"
            status.contains("不可用") || status.contains("失败") -> "不可用"
            else -> ""
        }

    /** 反射创建 insert 版预设混响 */
    private fun newInsert(): AudioEffect? = runCatching {
        val cls = Class.forName("android.media.audiofx.AudioEffect")
        val ctor = cls.getConstructor(
            UUID::class.java, UUID::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )
        ctor.isAccessible = true
        ctor.newInstance(
            UUID.fromString(UUID_PRE_AUX),
            UUID.fromString(UUID_PRE_INS),
            100,
            0
        ) as AudioEffect
    }.getOrNull()

    /** setParameter(0, intArrayOf(preset), 0)：0 = PARAM_PRESET */
    private fun applyPreset(f: AudioEffect, index: Int): Boolean = runCatching {
        val m = Class.forName("android.media.audiofx.AudioEffect").getMethod(
            "setParameter",
            Int::class.javaPrimitiveType, IntArray::class.java, Int::class.javaPrimitiveType
        )
        val rc = m.invoke(f, 0, intArrayOf(PRESET_VALUES[index]), 0) as Int
        rc == 0
    }.getOrDefault(false)
}