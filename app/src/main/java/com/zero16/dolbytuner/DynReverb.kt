package com.zero16.dolbytuner

import android.media.audiofx.AudioEffect
import android.media.audiofx.EnvironmentalReverb
import android.util.Log
import java.util.UUID

/**
 * 空间环境混响
 *
 * 关键结论（2026-11-04 实测）：
 *   Android 的 EnvironmentalReverb 类用的是 aux 版 UUID(4a387fc0…)，
 *   在 session 0（全局混音）上创建后就算 hasControl 为 true，也不会真的插进链路，
 *   所以参数怎么写都听不出来。
 *   系统里真正能插进链路的是 insert 版 UUID(c7a511a0…)，
 *   必须用 AudioEffect(typeUuid, insertUuid, priority, session) 直接建。
 *
 *   所以这里优先 insert 版 AudioEffect + setParameter，失败才退回原生类。
 *
 * 参数 ID 与 EnvironmentalReverb 的 PARAM_* 一致：
 *   0 room       1 roomHF     2 decay       3 decayHF
 *   4 reflLevel  5 reflDelay  6 revLevel    7 revDelay
 *   8 diffusion  9 density
 */
object DynReverb {

    private const val TAG = "DynReverb"

    private const val UUID_ENV_AUX = "4a387fc0-8ab3-11df-8bad-0002a5d5c51b"
    private const val UUID_ENV_INS = "c7a511a0-a3bb-11df-860e-0002a5d5c51b"

    /** 顺序：room, roomHF, decay, decayHF, reflLvl, reflDelay, revLvl, revDelay, diffusion, density */
    val DEFAULTS = intArrayOf(-200, -100, 2200, 850, -800, 15, 300, 20, 1000, 1000)

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var status: String = "未开启"
        private set

    private var fx: AudioEffect? = null
    private var rv: EnvironmentalReverb? = null
    private var last: IntArray = DEFAULTS.copyOf()

    @Synchronized
    fun start(data: IntArray): Boolean {
        stop()
        last = data.copyOf()

        // ① 插入式混响（真正会进链路的那种）
        // AudioEffect 的构造函数在 SDK stub 里不可见，必须走反射
        val ins = newInsertReverb()

        if (ins != null) {
            if (writeAll(ins, last)) {
                ins.setEnabled(true)
                fx = ins
                running = true
                val ctrl = runCatching { ins.hasControl() }.getOrDefault(false)
                status = if (ctrl) "混响已开启 · 插入式" else "混响已开启 · 等待控制权"
                Log.i(TAG, "insert reverb ok, hasControl=$ctrl")
                return true
            }
            runCatching { ins.release() }
        }

        // ② 退回原生（aux 版，部分机型也能用）
        val r = runCatching { EnvironmentalReverb(100, 0) }.getOrNull()
        if (r != null) {
            applyTo(r, last)
            r.setEnabled(true)
            rv = r
            running = true
            status = "混响已开启 · 兼容模式"
            Log.i(TAG, "fallback EnvironmentalReverb ok")
            return true
        }

        status = "开启失败 · 设备不支持"
        running = false
        return false
    }

    /** 反射创建 insert 版混响 */
    private fun newInsertReverb(): AudioEffect? = runCatching {
        val cls = Class.forName("android.media.audiofx.AudioEffect")
        val ctor = cls.getConstructor(
            UUID::class.java, UUID::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )
        ctor.isAccessible = true
        ctor.newInstance(
            UUID.fromString(UUID_ENV_AUX),
            UUID.fromString(UUID_ENV_INS),
            100,
            0
        ) as AudioEffect
    }.getOrNull()

    /** 反射写参数（SDK stub 里 setParameter 同样不可见） */
    private fun sp(f: AudioEffect, id: Int, v: Int): Int = runCatching {
        f.javaClass.getMethod(
            "setParameter",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        ).invoke(f, id, v) as Int
    }.getOrDefault(-1)

    private fun clampParam(id: Int, v: Int): Int = when (id) {
        0, 1, 4, 6 -> v.coerceIn(-9000, 0)   // 各类电平 mB
        2 -> v.coerceIn(1, 20000)            // decay ms
        3 -> v.coerceIn(1, 2000)             // decayHF 千分比
        5 -> v.coerceIn(0, 300)              // reflectionsDelay ms
        7 -> v.coerceIn(0, 100)              // reverbDelay ms
        else -> v.coerceIn(0, 1000)          // diffusion / density
    }

    /** 写全部参数，返回是否至少成功写入一条 */
    private fun writeAll(f: AudioEffect, d: IntArray): Boolean {
        var ok = 0
        for (i in 0..9) {
            val rc = sp(f, i, clampParam(i, d[i]))
            if (rc == 0) ok++
        }
        Log.i(TAG, "writeAll ok=$ok/10")
        return ok > 0
    }

    private fun applyTo(r: EnvironmentalReverb, d: IntArray) {
        runCatching { r.setRoomLevel(d[0].coerceIn(-9000, 0).toShort()) }
        runCatching { r.setRoomHFLevel(d[1].coerceIn(-9000, 0).toShort()) }
        runCatching { r.setDecayTime(d[2].coerceIn(100, 20000)) }
        runCatching { r.setDecayHFRatio(d[3].coerceIn(100, 2000).toShort()) }
        runCatching { r.setReflectionsLevel(d[4].coerceIn(-9000, 0).toShort()) }
        runCatching { r.setReflectionsDelay(d[5].coerceIn(0, 300)) }
        runCatching { r.setReverbLevel(d[6].coerceIn(-9000, 0).toShort()) }
        runCatching { r.setReverbDelay(d[7].coerceIn(0, 100)) }
        runCatching { r.setDiffusion(d[8].coerceIn(0, 1000).toShort()) }
        runCatching { r.setDensity(d[9].coerceIn(0, 1000).toShort()) }
    }

    @Synchronized
    fun set(data: IntArray) {
        val prev = last
        last = data.copyOf()

        val f = fx
        if (f != null) {
            if (!runCatching { f.hasControl() }.getOrDefault(false)) {
                status = "混响已开启 · 等待控制权"
                return
            }
            // 只写变化项，拖动一个滑块只发一条参数
            for (i in 0..9) {
                if (prev[i] != data[i]) sp(f, i, clampParam(i, data[i]))
            }
            status = "混响已开启 · 插入式"
            return
        }

        val r = rv ?: return
        if (!runCatching { r.hasControl() }.getOrDefault(false)) {
            status = "被其他应用占用 · 未写入"
            return
        }
        if (prev[0] != data[0]) runCatching { r.setRoomLevel(data[0].coerceIn(-9000, 0).toShort()) }
        if (prev[1] != data[1]) runCatching { r.setRoomHFLevel(data[1].coerceIn(-9000, 0).toShort()) }
        if (prev[2] != data[2]) runCatching { r.setDecayTime(data[2].coerceIn(100, 20000)) }
        if (prev[3] != data[3]) runCatching { r.setDecayHFRatio(data[3].coerceIn(100, 2000).toShort()) }
        if (prev[4] != data[4]) runCatching { r.setReflectionsLevel(data[4].coerceIn(-9000, 0).toShort()) }
        if (prev[5] != data[5]) runCatching { r.setReflectionsDelay(data[5].coerceIn(0, 300)) }
        if (prev[6] != data[6]) runCatching { r.setReverbLevel(data[6].coerceIn(-9000, 0).toShort()) }
        if (prev[7] != data[7]) runCatching { r.setReverbDelay(data[7].coerceIn(0, 100)) }
        if (prev[8] != data[8]) runCatching { r.setDiffusion(data[8].coerceIn(0, 1000).toShort()) }
        if (prev[9] != data[9]) runCatching { r.setDensity(data[9].coerceIn(0, 1000).toShort()) }
        status = "混响已开启 · 兼容模式"
    }

    @Synchronized
    fun stop() {
        fx?.let { f ->
            runCatching { f.setEnabled(false) }
            runCatching { f.release() }
        }
        fx = null
        rv?.let { r ->
            runCatching { r.setEnabled(false) }
            runCatching { r.release() }
        }
        rv = null
        running = false
        status = "未开启"
    }

    /** 卡片标题后缀用的短状态（空串表示未开启） */
    val shortStatus: String
        get() = when {
            status.contains("插入式") -> "插入式"
            status.contains("兼容模式") -> "兼容模式"
            status.contains("等待控制权") -> "等待控制权"
            status.contains("失败") || status.contains("不支持") -> "不可用"
            else -> ""
        }

    fun lastData(): IntArray = last.copyOf()
}