package com.zero16.dolbytuner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 首次进入的引导页（4 屏）。
 * 风格和 APP 内一致：玻璃卡片 + 壁纸派生色，不写死任何彩色。
 */
private data class ObPage(
    val icon: ImageVector,
    val title: String,
    val sub: String,
    val bullets: List<String>
)

private val OB_PAGES = listOf(
    ObPage(
        icon = Icons.Default.Waves,
        title = "欢迎使用 Dolby Tuner",
        sub = "给小米 / Redmi / POCO 的杜比调音与音效增强，全程不需要 root。",
        bullets = listOf(
            "20 段 GEQ 实时写进杜比 DSP",
            "独立的音效页（DP）管线",
            "液态玻璃外观，颜色跟着壁纸走"
        )
    ),
    ObPage(
        icon = Icons.Default.GraphicEq,
        title = "杜比 GEQ · 20 段",
        sub = "直接调杜比自带的图形均衡器，退出 APP 也依然生效。",
        bullets = listOf(
            "47 Hz ~ 19.7 kHz，拖动即写入",
            "双击某一段可以归零",
            "把调好的声音存成预设，按设备自动套用"
        )
    ),
    ObPage(
        icon = Icons.Default.Tune,
        title = "音效页 · DynamicsProcessing",
        sub = "安卓原生音效管线，和杜比互不干扰，用来做更细的声音处理。",
        bullets = listOf(
            "图形 / 参数均衡、限幅、多段压缩",
            "等响补偿：小音量也不丢低频",
            "来电或微信通话时自动暂停，挂断恢复"
        )
    ),
    ObPage(
        icon = Icons.Default.BlurOn,
        title = "外观 · 液态玻璃",
        sub = "卡片是真正会折射的玻璃，颜色、圆角、厚度都能自己挑。",
        bullets = listOf(
            "取色自当前壁纸，换壁纸一起变",
            "清透 / 标准 / 浓厚 / 柔和 / 省电",
            "看不清小字时打开「增强文字可读性」"
        )
    )
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { OB_PAGES.size }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val accent = Appearance.accent.value

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
        ) {
            // 顶部：跳过
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "跳过",
                    fontSize = 12.sp,
                    color = subInk(0.85f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .jellyPress(Appearance.jelly.value > 0)
                        .clickable { onDone() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f)
            ) { index ->
                val p = OB_PAGES[index]
                Column(
                    Modifier.fillMaxSize().padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(p.icon, null, tint = accent, modifier = Modifier.size(38.dp))
                            }
                            Spacer(Modifier.height(18.dp))
                            Text(
                                p.title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                p.sub, fontSize = 12.sp, lineHeight = 19.sp,
                                color = subInk(0.9f)
                            )
                            Spacer(Modifier.height(16.dp))
                            p.bullets.forEach { b ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        Modifier
                                            .padding(top = 6.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        b, fontSize = 11.sp, lineHeight = 17.sp,
                                        color = cs.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 圆点
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(OB_PAGES.size) { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (on) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(if (on) accent else cs.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }
            }

            Button(
                onClick = {
                    if (pager.currentPage == OB_PAGES.lastIndex) onDone()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = if (accent.luminance() > 0.55f) androidx.compose.ui.graphics.Color(0xFF101014)
                    else androidx.compose.ui.graphics.Color.White
                )
            ) {
                Text(
                    if (pager.currentPage == OB_PAGES.lastIndex) "开始使用" else "下一步",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

