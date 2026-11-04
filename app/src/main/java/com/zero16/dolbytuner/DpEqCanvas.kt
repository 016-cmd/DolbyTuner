package com.zero16.dolbytuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ln

/**
 * DP 侧 EQ 曲线画布（自绘）：
 *  - X 轴按频率对数排布，只标少量刻度，不会像 127 点那样糊成一片
 *  - 纵向 ±20 dB，中间一条 0 dB 基线
 *
 * @param curve 127 点曲线（dB）
 * @param fMin / fMax 显示范围（GEQ 用 47…19700，PEQ 用 20…20000）
 */
@Composable
fun DpEqCanvas(
    curve: FloatArray,
    fMin: Float,
    fMax: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = onSurfaceVariant, fontSize = 8.sp)

    val ticks = listOf(50f, 100f, 250f, 500f, 1000f, 2000f, 5000f, 10000f, 20000f)
        .filter { it >= fMin * 0.95f && it <= fMax * 1.05f }

    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val padBottom = 14.dp.toPx()

        fun xOf(f: Float): Float {
            val t = (ln(f / fMin) / ln(fMax / fMin)).coerceIn(0f, 1f)
            return t * w
        }

        fun yOf(db: Float): Float {
            val t = ((db + 20f) / 40f).coerceIn(0f, 1f)
            return (h - padBottom) - t * (h - padBottom)
        }

        ticks.forEach { f ->
            val x = xOf(f)
            drawLine(
                onSurfaceVariant.copy(alpha = 0.16f),
                Offset(x, 0f), Offset(x, h - padBottom), strokeWidth = 1f
            )
            val txt = fmtFreq(f.toInt())
            val layout = measurer.measure(txt, labelStyle)
            drawText(
                layout,
                topLeft = Offset(
                    (x - layout.size.width / 2f).coerceIn(0f, w - layout.size.width),
                    h - padBottom + 2.dp.toPx()
                )
            )
        }

        val y0 = yOf(0f)
        drawLine(
            onSurfaceVariant.copy(alpha = 0.35f),
            Offset(0f, y0), Offset(w, y0), strokeWidth = 1.2f
        )

        if (curve.isNotEmpty()) {
            val path = Path()
            var started = false
            for (i in curve.indices) {
                val f = EqMath.FREQ127[i]
                if (f < fMin * 0.98f || f > fMax * 1.02f) continue
                val p = Offset(xOf(f), yOf(curve[i]))
                if (!started) {
                    path.moveTo(p.x, p.y)
                    started = true
                } else {
                    path.lineTo(p.x, p.y)
                }
            }
            drawPath(path, accent, style = Stroke(width = 2.2f))
        }
    }
}