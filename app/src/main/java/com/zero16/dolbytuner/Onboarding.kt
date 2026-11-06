package com.zero16.dolbytuner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 介绍页数据 */
private data class ObPage(
    val icon: ImageVector,
    val title: String,
    val sub: String,
    val bullets: List<String>
)

private val INTRO_PAGES = listOf(
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
            "调好的声音可以存成预设，按设备自动套用"
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

/**
 * 首次进入引导：4 屏介绍 + 设备检查 + 记录 DSP 默认值。
 * 设备检查替代了原来的 GateDialog；DSP 记录替代了原来的启动弹窗。
 */
@Composable
fun OnboardingScreen(
    onCheck: () -> GateResult,
    baseCaptured: Boolean,
    onExit: () -> Unit,
    onDone: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val accent = Appearance.accent.value
    val inf = rememberInfiniteTransition(label = "ob")
    val pulse by inf.animateFloat(
        initialValue = 0.97f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "pulse"
    )
    val halo by inf.animateFloat(
        initialValue = 0.12f, targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "halo"
    )

    val total = INTRO_PAGES.size + 2
    val pager = rememberPagerState { total }
    val fix = remember { ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE) }

    // 机型检测只在「设备检查」这一页做：滑到该页才开始，不占冷启动时间
    var gate by remember { mutableStateOf<GateResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage == INTRO_PAGES.size && gate == null && !checking) {
            checking = true
            val r = withContext(Dispatchers.IO) { onCheck() }
            gate = r
            checking = false
        }
    }

    var busy by remember { mutableStateOf(false) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    var baseDone by remember { mutableStateOf(baseCaptured) }

    fun finish() {
        fix.edit().putBoolean("onboard_done", true)
            .putBoolean("dsp_base_prompted", true).apply()
        onDone()
    }

    fun runCapture() {
        busy = true
        errMsg = null
        scope.launch {
            val err = captureDspBaseline(ctx)
            if (err == null) {
                saveFx(ctx, DspBaseline.fx(ctx))
                fix.edit().putBoolean("dsp_base_prompted", true).apply()
                DeviceState.notifyApplied()
                baseDone = true
                busy = false
                finish()
            } else {
                errMsg = err
                busy = false
            }
        }
    }

    // 后台相关：和设置页同一套 setter
    var bootWrite by remember { mutableStateOf(fix.getBoolean("boot_writeback", false)) }
    var deviceService by remember { mutableStateOf(fix.getBoolean("device_service", false)) }
    var keepAlive by remember { mutableStateOf(fix.getBoolean("keepalive_job", false)) }
    var showBootDlg by remember { mutableStateOf(false) }
    var bootFail by remember { mutableStateOf(false) }
    var showDeviceGuide by remember { mutableStateOf(false) }
    var showKeepGuide by remember { mutableStateOf(false) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun startDeviceService() {
        runCatching { ctx.startForegroundService(Intent(ctx, DeviceAutomationService::class.java)) }
    }

    fun openAutostart(): Boolean = runCatching {
        ctx.startActivity(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrDefault(false)

    fun setBoot(on: Boolean) {
        bootWrite = on
        fix.edit().putBoolean("boot_writeback", on).apply()
        if (on) {
            bootFail = false
            showBootDlg = true
        }
    }

    fun setDev(on: Boolean) {
        deviceService = on
        fix.edit().putBoolean("device_service", on).apply()
        if (on) {
            showDeviceGuide = true
            if (Build.VERSION.SDK_INT >= 33) {
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            startDeviceService()
        } else {
            runCatching { ctx.stopService(Intent(ctx, DeviceAutomationService::class.java)) }
        }
    }

    fun setKeep(on: Boolean) {
        keepAlive = on
        fix.edit().putBoolean("keepalive_job", on).apply()
        if (on) {
            showKeepGuide = true
            KeepAliveJobService.schedule(ctx)
            startDeviceService()
        } else {
            KeepAliveJobService.cancel(ctx)
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp).navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (gate?.passed == true) {
                    Text(
                        "跳过", fontSize = 12.sp, color = subInk(0.85f),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .jellyPress(Appearance.jelly.value > 0)
                            .clickable { finish() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                // 检测没过就锁在这一页，别让人滑到「记录默认值」那一步
                userScrollEnabled = gate?.passed != false
            ) { idx ->
                val off = (pager.currentPage - idx) + pager.currentPageOffsetFraction
                Column(
                    Modifier.fillMaxSize().padding(vertical = 6.dp).graphicsLayer {
                        val d = kotlin.math.abs(off).coerceIn(0f, 1f)
                        alpha = 1f - d * 0.55f
                        val s = 1f - d * 0.07f
                        scaleX = s
                        scaleY = s
                        translationY = d * 30.dp.toPx()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when {
                                idx < INTRO_PAGES.size -> IntroBody(INTRO_PAGES[idx], accent, cs, pulse, halo)
                                idx == INTRO_PAGES.size -> CheckBody(
                                    gate = gate, checking = checking,
                                    accent = accent, cs = cs,
                                    pulse = pulse, halo = halo
                                )
                                else -> DspBody(
                                    accent = accent, cs = cs, busy = busy, errMsg = errMsg,
                                    pulse = pulse, halo = halo,
                                    bootWrite = bootWrite, deviceService = deviceService,
                                    keepAlive = keepAlive,
                                    onBoot = { setBoot(it) }, onDev = { setDev(it) },
                                    onKeep = { setKeep(it) }
                                )
                            }
                        }
                    }
                }
            }

            if (total > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(total) { i ->
                        val on = i == pager.currentPage
                        Box(
                            Modifier.padding(horizontal = 4.dp)
                                .size(width = if (on) 18.dp else 6.dp, height = 6.dp)
                                .clip(CircleShape)
                                .background(if (on) accent else cs.onSurfaceVariant.copy(alpha = 0.35f))
                        )
                    }
                }
            }

            if (gate?.passed == false) {
                Button(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("退出", fontSize = 14.sp) }
                Spacer(Modifier.height(14.dp))
                return@Column
            }

            if (pager.currentPage == total - 1) {
                Button(
                    onClick = { if (baseDone) finish() else runCapture() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = if (accent.luminance() > 0.55f) Color(0xFF101014) else Color.White
                    )
                ) {
                    Text(
                        when {
                            busy -> "检测中…"
                            baseDone -> "已检测过 DSP 默认参数"
                            else -> "检测并保存默认值"
                        },
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                if (baseDone) {
                    TextButton(onClick = { if (!busy) runCapture() }, modifier = Modifier.fillMaxWidth()) {
                        Text("重新检测一次", fontSize = 12.sp, color = accent)
                    }
                } else {
                    TextButton(onClick = { onExit() }, modifier = Modifier.fillMaxWidth()) {
                        Text("不执行检测 · 退出APP", fontSize = 12.sp, color = subInk())
                    }
                }
            } else {
                Button(
                    onClick = {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = if (accent.luminance() > 0.55f) Color(0xFF101014) else Color.White
                    )
                ) { Text("下一步", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showBootDlg) {
        AlertDialog(
            onDismissRequest = { showBootDlg = false },
            title = { Text("开机参数自动写回", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    if (bootFail) {
                        Text(
                            "无法自动打开自启动设置。", fontSize = 12.sp,
                            color = Appearance.colorLimit.value
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        "已开启。请允许 Dolby Tuner 自启动，否则重启后系统不会唤醒 APP 写回参数。",
                        fontSize = 11.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "路径：设置 → 应用设置 → 授权管理 → 自启动 → 允许 Dolby Tuner",
                        fontSize = 10.sp, color = subInk()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (openAutostart()) showBootDlg = false else bootFail = true
                }) {
                    Text("去设置", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBootDlg = false }) { Text("稍后", fontSize = 12.sp) }
            }
        )
    }

    if (showDeviceGuide) {
        DeviceListenGuideDialog(ctx = ctx, accent = accent) { showDeviceGuide = false }
    }

    if (showKeepGuide) {
        KeepAliveGuideDialog(ctx = ctx, accent = accent) { showKeepGuide = false }
    }
}

@Composable
private fun IntroBody(p: ObPage, accent: Color, cs: androidx.compose.material3.ColorScheme, pulse: Float, halo: Float) {
    Box(
        Modifier.size(76.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .clip(CircleShape).background(accent.copy(alpha = halo)),
        contentAlignment = Alignment.Center
    ) { Icon(p.icon, null, tint = accent, modifier = Modifier.size(38.dp)) }
    Spacer(Modifier.height(18.dp))
    Text(p.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(p.sub, fontSize = 12.sp, lineHeight = 19.sp, color = subInk(0.9f))
    Spacer(Modifier.height(16.dp))
    p.bullets.forEach { b ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 6.dp).size(5.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(9.dp))
            Text(b, fontSize = 11.sp, lineHeight = 17.sp, color = cs.onSurface)
        }
    }
}

@Composable
private fun CheckBody(
    gate: GateResult?, checking: Boolean,
    accent: Color, cs: androidx.compose.material3.ColorScheme,
    pulse: Float, halo: Float
) {
    val passed = gate?.passed == true
    val unknown = gate == null
    val warn = Color(0xFFEF4444)
    Box(
        Modifier.size(76.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .clip(CircleShape)
            .background((if (unknown) cs.onSurfaceVariant else if (passed) accent else warn).copy(alpha = halo)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            when {
                unknown -> Icons.Default.Search
                passed -> Icons.Default.CheckCircle
                else -> Icons.Default.Warning
            },
            null,
            tint = if (unknown) cs.onSurfaceVariant else if (passed) accent else warn,
            modifier = Modifier.size(38.dp)
        )
    }
    Spacer(Modifier.height(18.dp))
    Text("设备检查", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
    Spacer(Modifier.height(8.dp))
    if (unknown) {
        Text(
            if (checking) "正在检查这台设备…" else "滑到这一页才开始检查。",
            fontSize = 11.sp, lineHeight = 17.sp, color = subInk(0.9f)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "只读取机型、系统版本与杜比引擎信息，不改动任何参数。",
            fontSize = 10.sp, lineHeight = 16.sp, color = subInk(0.75f)
        )
        return
    }
    Text(gate!!.deviceLine, fontSize = 11.sp, lineHeight = 17.sp, color = subInk(0.9f))
    Spacer(Modifier.height(14.dp))
    if (passed) {
        Text(
            "这台设备可以正常运行杜比实时层与音效页。点「下一步」继续。",
            fontSize = 11.sp, lineHeight = 17.sp, color = cs.onSurface
        )
    } else {
        gate.reasons.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(5.dp).clip(CircleShape).background(warn))
                Spacer(Modifier.width(9.dp))
                Text(r, fontSize = 11.sp, lineHeight = 17.sp, color = cs.onSurface)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("当前设备不满足运行条件，无法继续。", fontSize = 11.sp, color = subInk(0.9f))
    }
}

@Composable
private fun DspBody(
    accent: Color, cs: androidx.compose.material3.ColorScheme, busy: Boolean, errMsg: String?,
    pulse: Float, halo: Float,
    bootWrite: Boolean, deviceService: Boolean, keepAlive: Boolean,
    onBoot: (Boolean) -> Unit, onDev: (Boolean) -> Unit, onKeep: (Boolean) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(76.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(CircleShape).background(accent.copy(alpha = halo)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Tune, null, tint = accent, modifier = Modifier.size(38.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("音效页 · 实时 DSP", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "音效页是一套自己跑的实时处理：图形 / 参数均衡、输出增益、等比增益、限幅器、多段压缩、等响补偿。参数直接进 DSP，拖动就听得见。",
            fontSize = 11.sp, lineHeight = 18.sp, color = cs.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "它跟着 APP 的音频处理会话走：切到后台也照常工作，但被系统清理或强制停止后就停了。想让改动一直生效，把下面三项打开。",
            fontSize = 11.sp, lineHeight = 18.sp, color = cs.onSurface
        )
        Spacer(Modifier.height(14.dp))

        ObSwitchRow(
            icon = Icons.Default.RestartAlt, checked = bootWrite, accent = accent, cs = cs,
            title = "开机参数自动写回",
            sub = "重启后自动把杜比参数与音效参数写回一次",
            onChange = onBoot
        )
        Spacer(Modifier.height(8.dp))
        ObSwitchRow(
            icon = Icons.Default.Tune, checked = deviceService, accent = accent, cs = cs,
            title = "后台设备监听",
            sub = "息屏时也按绑定的预设自动切换（需要通知权限）",
            onChange = onDev
        )
        Spacer(Modifier.height(8.dp))
        ObSwitchRow(
            icon = Icons.Default.Refresh, checked = keepAlive, accent = accent, cs = cs,
            title = "后台保活",
            sub = "被系统清掉后，由系统定时把服务拉回来",
            onChange = onKeep
        )

        Spacer(Modifier.height(10.dp))
        Text(
            "三项都在本机运行，不需要 root；只用到通知和通话状态这类必要权限。现在不打开也没关系，以后在「设置」里随时能改。",
            fontSize = 9.sp, lineHeight = 15.sp, color = subInk(0.85f),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Text("记录音效默认值", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            "每台手机的杜比默认参数都不一样。现在读一次本机 DSP 的当前音效参数，存成「重置」时的基准。",
            fontSize = 11.sp, lineHeight = 18.sp, color = cs.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "以后点重置：GEQ 归零、音效恢复成这套值。检测只读不改任何参数。",
            fontSize = 10.sp, lineHeight = 16.sp, color = subInk(0.9f)
        )
        errMsg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 10.sp, lineHeight = 16.sp, color = Color(0xFFEF4444))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ObSwitchRow(
    icon: ImageVector, checked: Boolean, accent: Color,
    cs: androidx.compose.material3.ColorScheme,
    title: String, sub: String, onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null, tint = if (checked) accent else cs.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = cs.onSurface)
            Text(sub, fontSize = 9.sp, lineHeight = 13.sp, color = subInk(0.85f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}