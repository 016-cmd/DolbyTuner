package com.zero16.dolbytuner

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * 赞助弹窗：微信 / 支付宝两栏切换，下面是收款码图片。
 * 图片来自 assets/sponsor_wx.png 与 assets/sponsor_ali.jpg。
 */
@Composable
internal fun SponsorDialog(ctx: Context, accent: Color, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val wx = remember {
        runCatching {
            ctx.assets.open("sponsor_wx.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    val ali = remember {
        runCatching {
            ctx.assets.open("sponsor_ali.jpg").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    val bmp = if (tab == 0) wx else ali

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(Modifier.fillMaxWidth(), radius = Appearance.radius.value) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "赞助作者", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        Text(
                            "谢谢你请 Aoi 吃米饭 🍚",
                            fontSize = 11.sp, color = subInk(0.92f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close, contentDescription = "关闭",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SponsorTab("微信", tab == 0, accent) { tab = 0 }
                    Spacer(Modifier.width(10.dp))
                    SponsorTab("支付宝", tab == 1, accent) { tab = 1 }
                }

                Spacer(Modifier.height(12.dp))

                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = if (tab == 0) "微信收款码" else "支付宝收款码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        "收款码图片读取失败",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "扫码即可，金额随意 · 你的支持是这个项目继续做下去的动力",
                    fontSize = 9.sp, lineHeight = 15.sp, color = subInk(0.85f)
                )
            }
        }
    }
}

@Composable
private fun SponsorTab(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) accent else subInk(0.9f),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp)
    )
}