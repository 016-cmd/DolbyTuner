package com.zero16.dolbytuner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 打开本机的「省电策略 / 电池使用」页面。
 * 先试几个常见的系统电池页，全失败再退回本应用详情页（那里也能进省电策略）。
 */
internal fun openBatterySettings(ctx: Context): Boolean {
    val pkg = ctx.packageName
    val label = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.applicationInfo).toString()
    }.getOrDefault(pkg)

    val candidates = listOf(
        // 应用省电策略列表（可直接落到本应用）
        ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
        // 省电策略总列表
        ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainer"),
        // 电池与性能
        ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
    )
    for (c in candidates) {
        val ok = runCatching {
            ctx.startActivity(
                Intent().apply {
                    component = c
                    putExtra("package_name", pkg)
                    putExtra("package_label", label)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
        if (ok) return true
    }
    return runCatching {
        ctx.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", pkg, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}

/**
 * 打开后台类开关之后的引导：最近任务卡片上锁 + 省电策略改为无限制。
 * 不申请额外权限，只把系统里该点的两处说清楚。
 */
@Composable
internal fun KeepAliveGuideDialog(
    ctx: Context,
    accent: Color,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("让后台别被系统清掉", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        },
        text = {
            Column {
                Text(
                    "音效处理要跟着进程走，系统清理时最容易先杀后台。做下面两件事就稳得多：",
                    fontSize = 11.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Text("① 最近任务里把卡片上锁", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                Text(
                    "打开最近任务 → 找到 Dolby Tuner 的卡片 → 往下拉一下，卡片右上角出现小锁即可。",
                    fontSize = 10.sp, lineHeight = 16.sp, color = subInk(0.92f)
                )
                Spacer(Modifier.height(10.dp))
                Text("② 省电策略改为「无限制」", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                Text(
                    "设置 → 应用设置 → 应用管理 → Dolby Tuner → 省电策略 → 无限制。",
                    fontSize = 10.sp, lineHeight = 16.sp, color = subInk(0.92f)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "两项都不涉及 root，也不会额外耗电：只是别让系统在息屏后把服务清掉。",
                    fontSize = 9.sp, lineHeight = 15.sp, color = subInk(0.85f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (openBatterySettings(ctx)) onDismiss()
            }) {
                Text("去设置省电策略", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("知道了", fontSize = 12.sp) }
        }
    )
}
