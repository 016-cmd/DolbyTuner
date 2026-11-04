package com.zero16.dolbytuner

import android.media.audiofx.EnvironmentalReverb
import android.util.Log

/**
 * 空间环境混响（Android 原生 EnvironmentalReverb，I3DL2）
 *
 * 参数参考（逆向 SonicKeyframe 的 C1877ui / C2182zi）：
 *   roomLevel / roomHF / reflectionsLevel / reverbLevel  → 毫贝（mB，-9000…0）
 *   decayTime                                            → 毫秒
 *   decayHFRatio                                         → 千分比（100…2000）
 *   reflectionsDelay / reverbDelay                       → 毫秒
 *   diffusion / density                                  → 千分比（0…1000）
 */
object DynReverb {

    private const val TAG = "DynReverb"

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var status: String = "未开启"
        private set

    /** 顺序：room, roomHF, decay, decayHF, reflLvl, reflDelay, revLvl, revDelay, diffusion, density */
    val DEFAULTS = intArrayOf(-200, -100, 2200, 850, -800, 15, 300, 20, 1000, 1000)

    private var rv: EnvironmentalReverb? = null
    private var last: IntArray = DEFAULTS.copyOf()

    @Synchronized
    fun start(data: IntArray): Boolean {
        stop()
        last = data.copyOf()
        return try {
            val r = EnvironmentalReverb(Int.MAX_VALUE, 0)
            applyTo(r, last)
            r.setEnabled(true)
            rv = r
            running = true
            status = "混响已开启"
            Log.i(TAG, "reverb started")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "reverb start failed: ${t.message}")
            status = "开启失败 · 设备不支持"
            running = false
            false
        }
    }

    private fun applyTo(r: EnvironmentalReverb, d: IntArray) {
        runCatching { r.setRoomLevel(d[0].coerceIn(-9000, 0).toShort()) }
        runCatching { r.setRoomHFLevel(d[1].coerceIn(-9000, 0).toShort()) }
        runCatching { r.setDecayTime(d[2].coerceIn(100, 20000)) }
        runCatching { r.setDecayHFRatio(d[3].coerceIn(100, 2000).toShort()) }
        runCatching { r.setReflectionsLevel(d[4].coerceIn(-9000, 0).toShort()) }
        runCatching { r.setReflectionsDelay(d[5].coerceIn(0, 300).toInt()) }
        runCatching { r.setReverbLevel(d[6].coerceIn(-9000, 0).toShort()) }
        runCatching { r.setReverbDelay(d[7].coerceIn(0, 100).toInt()) }
        runCatching { r.setDiffusion(d[8].coerceIn(0, 1000).toShort()) }
        runCatching { r.setDensity(d[9].coerceIn(0, 1000).toShort()) }
    }

    @Synchronized
    fun set(data: IntArray) {
        val prev = last
        last = data.copyOf()
        val r = rv ?: return
        if (!runCatching { r.hasControl() }.getOrDefault(false)) {
            status = "被其他应用占用 · 未写入"
            return
        }
        // 只写变化项：拖动一个滑块只发 1 条参数，避免整页卡顿
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
        status = "混响已开启"
    }

    @Synchronized
    fun stop() {
        val r = rv
        rv = null
        running = false
        status = "未开启"
        if (r != null) {
            runCatching { r.setEnabled(false) }
            runCatching { r.release() }
        }
    }

    fun lastData(): IntArray = last.copyOf()
}