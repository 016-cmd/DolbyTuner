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

    /** AOSP 里 PresetReverb 的 effect type（不是实现 UUID） */
    private const val UUID_TYPE_PRESET = "47382d60-ddd8-11db-bf3a-0002a5d5c51b"
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

    /** 诊断信息：给界面显示用 */
    @Volatile
    var diag: String = ""
        private set

    private var fx: AudioEffect? = null
    private var last: Int = 3

    @Synchronized
    fun start(preset: Int): Boolean {
        stop()
        last = preset.coerceIn(0, PRESET_NAMES.size - 1)
        val f = newInsert()
        if (f == null) {
            diag = "创建失败：本机没有该效果或已被占用"
            status = "预设混响不可用 · 本机没有该效果或已被占用"
            return false
        }
        if (!applyPreset(f, last)) {
            runCatching { f.release() }
            diag = "建了但不吃参数（rc=$lastRc）"
            status = "预设混响写入失败"
            return false
        }
        runCatching { f.setEnabled(true) }
        fx = f
        running = true
        val ctrl = runCatching { f.hasControl() }.getOrDefault(false)
        diag = "控制权${if (ctrl) "✓" else "✗"} · rc=$lastRc"
        status = when {
            !ctrl -> "预设混响 · 等待控制权"
            lastRc == 0 -> "预设混响 · ${PRESET_NAMES[last]}"
            else -> "预设混响 · ${PRESET_NAMES[last]}（写入被拒 rc=$lastRc）"
        }
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

    /**
     * 反射创建预设混响：type 用 AOSP 的 PresetReverb 类型 UUID，
     * 实现 UUID 先试 insert 版，不行再试 aux 版。
     */
    private fun newInsert(): AudioEffect? {
        val candidates = listOf(
            UUID_TYPE_PRESET to UUID_PRE_INS,
            UUID_TYPE_PRESET to UUID_PRE_AUX
        )
        for ((typeS, implS) in candidates) {
            val fx = runCatching {
                val ctor = Class.forName("android.media.audiofx.AudioEffect").getConstructor(
                    UUID::class.java, UUID::class.java,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
                )
                ctor.isAccessible = true
                ctor.newInstance(
                    UUID.fromString(typeS), UUID.fromString(implS), 100, 0
                ) as AudioEffect
            }.getOrNull()
            if (fx != null) {
                Log.i(TAG, "created with impl=$implS")
                return fx
            }
        }
        return null
    }

    /** 上一次写入的返回码（0 = 成功），用于界面提示 */
    @Volatile
    var lastRc: Int = Int.MIN_VALUE
        private set

    /**
     * 写档位：0 = PARAM_PRESET，值 1..6。
     * AudioEffect 的 setParameter 有多个重载，逐个试。
     */
    private fun applyPreset(f: AudioEffect, index: Int): Boolean {
        val v = PRESET_VALUES[index]
        val cls = runCatching { Class.forName("android.media.audiofx.AudioEffect") }.getOrNull() ?: run {
            lastRc = Int.MIN_VALUE
            return false
        }
        val tries: List<() -> Int> = listOf(
            { cls.getMethod("setParameter", Int::class.javaPrimitiveType, IntArray::class.java, Int::class.javaPrimitiveType).invoke(f, 0, intArrayOf(v), 0) as Int },
            { cls.getMethod("setParameter", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(f, 0, v) as Int }
        )
        for (call in tries) {
            val rc = runCatching(call).getOrNull() ?: continue
            lastRc = rc
            if (rc == 0) return true
        }
        return false
    }
}