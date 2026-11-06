package com.zero16.dolbytuner

import kotlin.math.log10
import kotlin.math.pow

/**
 * ISO 226:2003 等响曲线
 *
 * 数据表按 ISO 226:2003 标准等响曲线整理（29 个频点：αf / Lu / Tf）
 *   o = 频率 29 点（20 Hz … 12.5 kHz）
 *   p = αf（0.532 … 0.301）
 *   q = Lu（-31.6 … -3.1）
 *   r = Tf（78.5 … 12.3）
 *
 * 公式（ISO 226:2003 标准式）：
 *
 *   Lp = (10/αf) * log10( 0.00447 * (10^(phon/40) - 1.15)
 *                         + (10^((Tf+Lu)/10 - 9) * 0.4)^αf )
 *        - Lu + 94
 *
 * 校验：f = 1000 Hz, phon = 80 → 80.01 dB（1 kHz 处等响曲线就是 phon 值）
 *
 * 等响补偿只用到差值：
 *   spl(f, phon) - spl(1000, phon)
 */
object Iso226 {

    val FREQ = floatArrayOf(
        20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
        200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
        2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f
    )

    private val AF = floatArrayOf(
        0.532f, 0.506f, 0.480f, 0.455f, 0.432f, 0.409f, 0.387f, 0.367f, 0.349f, 0.330f,
        0.315f, 0.301f, 0.288f, 0.276f, 0.267f, 0.259f, 0.253f, 0.250f, 0.246f, 0.244f,
        0.243f, 0.243f, 0.243f, 0.242f, 0.242f, 0.245f, 0.254f, 0.271f, 0.301f
    )

    private val LU = floatArrayOf(
        -31.6f, -27.2f, -23.0f, -19.1f, -15.9f, -13.0f, -10.3f, -8.1f, -6.2f, -4.5f,
        -3.1f, -2.0f, -1.1f, -0.4f, 0f, 0.3f, 0.5f, 0f, -2.7f, -4.1f,
        -1.0f, 1.7f, 2.5f, 1.2f, -2.1f, -7.1f, -11.2f, -10.7f, -3.1f
    )

    private val TF = floatArrayOf(
        78.5f, 68.7f, 59.5f, 51.1f, 44.0f, 37.5f, 31.5f, 26.5f, 22.1f, 17.9f,
        14.4f, 11.4f, 8.6f, 6.2f, 4.4f, 3.0f, 2.2f, 2.4f, 3.5f, 1.7f,
        3.4f, 0.975f, 0.75f, 0.825f, 3.0f, 6.0f, 12.6f, 13.9f, 12.3f
    )

    /** 频率表插值（对数轴，超出范围钳到两端） */
    private fun interp(f: Float, table: FloatArray): Float {
        if (f <= FREQ[0]) return table[0]
        val last = FREQ.size - 1
        if (f >= FREQ[last]) return table[last]
        var i = 0
        while (i < last) {
            if (f >= FREQ[i] && f <= FREQ[i + 1]) {
                val lf = log10(f.toDouble())
                val l0 = log10(FREQ[i].toDouble())
                val l1 = log10(FREQ[i + 1].toDouble())
                val t = ((lf - l0) / (l1 - l0)).toFloat()
                return table[i] + (table[i + 1] - table[i]) * t
            }
            i++
        }
        return table[last]
    }

    /** 某频率在指定等响级（phon）下的声压级 dB SPL */
    fun splDb(freq: Float, phon: Float): Float {
        val af = interp(freq, AF)
        val lu = interp(freq, LU)
        val tf = interp(freq, TF)
        if (phon == 0f) return tf

        val x = 0.00447f * (10f.pow(phon / 40f) - 1.15f)
        val y = (10f.pow((tf + lu) / 10f - 9f) * 0.4f).pow(af)
        val z = x + y
        if (z <= 0f) return -120f
        return (10f / af) * log10(z.toDouble()).toFloat() - lu + 94f
    }

    /** 相对 1 kHz 的等响偏差（dB），用于均衡补偿 */
    fun relativeDb(freq: Float, phon: Float): Float =
        splDb(freq, phon) - splDb(1000f, phon)
}
