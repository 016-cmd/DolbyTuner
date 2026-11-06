package com.zero16.dolbytuner

/**
 * AutoEQ / GraphicEQ 文本解析，以及 EQ 强度缩放工具。
 *
 * 支持 AutoEq 导出文件的两种常见写法：
 *   GraphicEQ: 20 -1.5; 21 -1.4; 22 -1.6; ...
 *   20 -1.5
 *   21 -1.4
 *
 * 解析结果按对数频率插值到 127 个点，正好和 DynamicsProcessing 的 PreEq 对齐。
 */
object AutoEq {

    /** 解析为 127 点曲线（dB）；解析不出两个以上频点时返回 null */
    fun parse(text: String): FloatArray? {
        if (text.isBlank()) return null
        var s = text
        val gi = s.indexOf("GraphicEQ", ignoreCase = true)
        if (gi >= 0) s = s.substring(gi + "GraphicEQ".length)
        s = s.substringAfter(':', s)

        val pts = ArrayList<Pair<Float, Float>>()
        val segs = s.split(';', '\n', ',')
        for (seg in segs) {
            val t = seg.trim().split(' ', '\t').filter { it.isNotBlank() }
            if (t.size < 2) continue
            val f = t[0].toFloatOrNull() ?: continue
            val g = t[1].toFloatOrNull() ?: continue
            if (f <= 0f) continue
            pts.add(f to g)
        }
        if (pts.size < 2) return null
        pts.sortBy { it.first }

        val out = FloatArray(127)
        for (i in 0 until 127) {
            out[i] = interp(pts, EqMath.FREQ127[i].toDouble()).coerceIn(-30f, 30f)
        }
        return out
    }

    /** 对数频率线性插值，两端外推取最近点 */
    private fun interp(pts: List<Pair<Float, Float>>, f: Double): Float {
        if (f <= pts.first().first) return pts.first().second
        if (f >= pts.last().first) return pts.last().second
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            if (f >= a.first && f <= b.first) {
                val x0 = kotlin.math.ln(a.first.toDouble())
                val x1 = kotlin.math.ln(b.first.toDouble())
                val x = kotlin.math.ln(f)
                val t = if (x1 - x0 < 1e-9) 0.0 else (x - x0) / (x1 - x0)
                return (a.second + (b.second - a.second) * t).toFloat()
            }
        }
        return pts.last().second
    }

    /** 127 点曲线 → 逗号分隔字符串（存偏好/预设用） */
    fun toCsv(c: FloatArray): String = c.joinToString(",") { String.format("%.2f", it) }

    /** 逗号分隔 → 127 点曲线；点数不对返回 null */
    fun fromCsv(s: String): FloatArray? {
        if (s.isBlank()) return null
        val a = s.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (a.size != 127) return null
        return a.toFloatArray()
    }

    /** 整体强度缩放：pct = 100 表示原曲线，0 表示完全平直，最大 150% */
    fun scale(c: FloatArray, pct: Int): FloatArray {
        val k = pct.coerceIn(0, 150) / 100f
        if (k == 1f) return c
        return FloatArray(c.size) { c[it] * k }
    }
}
