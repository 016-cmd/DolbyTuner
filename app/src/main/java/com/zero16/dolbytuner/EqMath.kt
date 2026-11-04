package com.zero16.dolbytuner

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

/** PEQ 单段：频点 / 增益(dB) / Q */
data class PeqBand(
    val freqHz: Float = 1000f,
    val gainDb: Float = 0f,
    val q: Float = 1f
)

/**
 * DP 侧 EQ 曲线计算：
 *   GEQ 20 段 → 127 点（对数频率插值）
 *   PEQ 10 段 → 127 点（各段 peaking 响应叠加，Audio EQ Cookbook 幅度公式）
 */
object EqMath {

    /** 127 段中心频率：20Hz → 20kHz 等比 */
    val FREQ127 = FloatArray(127) { i ->
        (20.0 * 1000.0.pow(i / 126.0)).toFloat()
    }

    /**
     * 杜比 GEQ 的 20 段（Q4，1dB=16）→ 127 点 dB
     * @param q         段间过渡宽度（Q 越大越窄、越贴折线；越小越平滑）
     * @param masterDb  整体增益（叠加到全部频点）
     */
    fun geq20To127(
        q4: IntArray,
        q: Float = 1f,
        masterDb: Float = 0f
    ): FloatArray {
        val db = FloatArray(DapRealtime.BANDS) { (q4.getOrNull(it) ?: 0) / DapRealtime.Q4_PER_DB.toFloat() }
        val sigma = (0.35f / q.coerceIn(0.1f, 10f)).toDouble()
        val out = FloatArray(127)
        for (i in 0..126) {
            val lf = ln(FREQ127[i].toDouble())
            var acc = 0.0
            var wsum = 0.0
            for (k in db.indices) {
                val d = ln(DapRealtime.FREQS[k].toDouble()) - lf
                val wgt = Math.exp(-(d * d) / (2.0 * sigma * sigma))
                acc += db[k] * wgt
                wsum += wgt
            }
            out[i] = (if (wsum > 1e-9) (acc / wsum).toFloat() else 0f) + masterDb
        }
        return out
    }

    /** PEQ 若干段 → 127 点 dB（叠加） */
    fun peqTo127(bands: List<PeqBand>): FloatArray {
        val out = FloatArray(127)
        for (i in 0..126) {
            val f = FREQ127[i].toDouble()
            var acc = 0.0
            for (b in bands) {
                if (b.gainDb == 0f) continue
                acc += peakingDb(f, b.freqHz.toDouble(), b.gainDb.toDouble(), b.q.toDouble())
            }
            out[i] = acc.toFloat()
        }
        return out
    }

    /** 单段 peaking 的幅度响应（dB） */
    private fun peakingDb(f: Double, f0: Double, gainDb: Double, q: Double): Double {
        if (f0 <= 0.0 || q <= 0.0) return 0.0
        val a = 10.0.pow(gainDb / 40.0)
        val w = f / f0
        val t = 1.0 - w * w
        val alpha = w / q
        val num = t * t + alpha * alpha
        val den = t * t + (alpha / a) * (alpha / a)
        if (den <= 1e-12) return 0.0
        return 10.0 * log10(num / den)
    }

    private fun resample(freqs: IntArray, db: FloatArray): FloatArray {
        val out = FloatArray(127)
        val lf = DoubleArray(freqs.size) { ln(freqs[it].toDouble()) }
        for (i in 0..126) {
            val x = ln(FREQ127[i].toDouble())
            if (x <= lf[0]) { out[i] = db[0]; continue }
            if (x >= lf[lf.size - 1]) { out[i] = db[db.size - 1]; continue }
            var j = 0
            while (j < lf.size - 2 && lf[j + 1] < x) j++
            val t = ((x - lf[j]) / (lf[j + 1] - lf[j])).toFloat()
            out[i] = db[j] + (db[j + 1] - db[j]) * t
        }
        return out
    }
}