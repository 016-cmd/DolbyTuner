package com.zero16.dolbytuner

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

/** 打开本应用的系统「应用信息」页（设置 → 应用 → 应用管理 → 本应用）。 */
internal fun openAppSettings(ctx: Context): Boolean = runCatching {
    ctx.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", ctx.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
}.getOrDefault(false)

/** 打开本应用的通知设置页。 */
internal fun openNotificationSettings(ctx: Context): Boolean = runCatching {
    ctx.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            putExtra("app_package", ctx.packageName)
            putExtra("app_uid", ctx.applicationInfo.uid)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
    true
}.getOrDefault(false)

/**
 * 打开「后台设备监听」后的说明：讲清楚它在后台做什么、要一条常驻通知。
 */
@Composable
internal fun DeviceListenGuideDialog(
    ctx: Context,
    accent: Color,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("后台设备监听在做什么", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        },
        text = {
            Column {
                Text(
                    "打开后，即使 APP 在后台、屏幕熄灭，也会继续盯着当前的输出设备——有线耳机、蓝牙耳机、扬声器之间一切换，就自动套用你给这个设备绑定的预设。",
                    fontSize = 11.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Text("为什么需要一条通知", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                Text(
                    "系统要求这类后台监听必须以「前台服务」运行，所以通知栏会常驻一条 Dolby Tuner 的提示。它不是广告，也可以从那条通知直接关掉监听。",
                    fontSize = 10.sp, lineHeight = 16.sp, color = subInk(0.92f)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Android 13 起需要通知权限，没给的话监听照样跑，但通知栏看不到那条提示。",
                    fontSize = 9.sp, lineHeight = 15.sp, color = subInk(0.85f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (openNotificationSettings(ctx)) onDismiss()
            }) {
                Text("去开通知权限", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("知道了", fontSize = 12.sp) }
        }
    )
}

/**
 * 打开「后台保活」后的说明：系统清理 + 最近任务上锁 + 省电策略。
 * 按钮直接跳到本应用的应用信息页，用户在那里能找到省电策略。
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
                    "保活只负责「被清掉之后把服务拉回来」，但系统清理时最先杀的就是后台服务，所以还要做两件事让它少被杀：",
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
                    "在本应用的应用信息页 → 省电策略 → 选「无限制」。下面按钮直接跳到那一页。",
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
                if (openAppSettings(ctx)) onDismiss()
            }) {
                Text("去应用设置", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("知道了", fontSize = 12.sp) }
        }
    )
}