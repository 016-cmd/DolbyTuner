package com.zero16.dolbytuner

import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import android.app.Activity
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontFamily
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ALLOWED_BRANDS = setOf("xiaomi", "redmi", "poco")
/** 强调色：默认品牌绿，壁纸取色后会换成壁纸主色（图标 / 拉条 / 选中态都跟它走） */
internal var Accent by androidx.compose.runtime.mutableStateOf(Color(0xFF22C55E))

private fun sysProp(key: String): String = runCatching {
    val c = Class.forName("android.os.SystemProperties")
    (c.getMethod("get", String::class.java).invoke(null, key) as? String).orEmpty()
}.getOrDefault("")

/** 真实市场机型名（HyperOS 用 ro.product.marketname，取不到回退 Build.MODEL） */
private fun realModelName(): String {
    val keys = listOf(
        "ro.product.marketname", "ro.product.odm.marketname",
        "ro.product.vendor.marketname", "ro.product.system.marketname"
    )
    val mkt = keys.firstNotNullOfOrNull { k -> sysProp(k).takeIf { it.isNotBlank() } }
    return (mkt ?: "${Build.MANUFACTURER} ${Build.MODEL}").trim()
}

private data class GateResult(val passed: Boolean, val reasons: List<String>, val deviceLine: String)
private data class DockTab(val label: String, val icon: ImageVector, val resId: Int? = null)

class MainActivity : ComponentActivity() {

    private fun prop(key: String): String = runCatching {
        val p = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
        val s = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        s
    }.getOrDefault("")

    private fun evaluateGate(): GateResult {
        val reasons = mutableListOf<String>()
        val sdk = Build.VERSION.SDK_INT
        if (sdk < 36) reasons += "需要 Android 16（API 36）及以上，当前 API $sdk"
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val vendorBrand = prop("ro.product.vendor.brand").lowercase()
        val propBrand = prop("ro.product.brand").lowercase()
        val isXiaomi = listOf(brand, manufacturer, vendorBrand, propBrand).any { v ->
            ALLOWED_BRANDS.any { it == v } || v.contains("xiaomi") || v.contains("redmi") || v.contains("poco")
        }
        if (!isXiaomi) reasons += "仅支持小米 / Redmi / POCO，当前：${Build.MANUFACTURER} ${Build.BRAND}"
        return GateResult(
            reasons.isEmpty(), reasons,
            "${realModelName()} · Android ${Build.VERSION.RELEASE} (API $sdk)"
        )
    }

    override fun onPause() {
        // 只要正常进入过后台/退出，就清掉「会话未收尾」标记，
        // 避免用户「退出再进」时被误判成崩溃而关掉卡片真折射
        runCatching {
            getSharedPreferences("appearance", MODE_PRIVATE).edit()
                .putBoolean("liquid_session", false)
                .putInt("liquid_strikes", 0)
                .apply()
        }
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 液态玻璃崩溃保险：
        //   正常切后台会走 onPause → 清标记，所以「退出再进」不会被误关；
        //   只有连续两次启动都发现上次会话没收尾（真崩了），才自动关掉卡片真折射。
        runCatching {
            val ap = getSharedPreferences("appearance", MODE_PRIVATE)
            val strikes = if (ap.getBoolean("liquid_session", false))
                ap.getInt("liquid_strikes", 0) + 1 else 0
            if (strikes >= 2) {
                ap.edit()
                    .putBoolean("card_liquid", false)
                    .putInt("liquid_strikes", 0)
                    .apply()
            } else {
                ap.edit().putInt("liquid_strikes", strikes).apply()
            }
            ap.edit().putBoolean("liquid_session", true).apply()
        }
        // 诊断：读 103/106/107/112 的当前值（只读，不改任何东西）
        Thread {
            android.util.Log.i("DapDiag", "start")
            val r = runCatching {
                val ids = intArrayOf(103, 106, 107, 112)
                val sb = StringBuilder("diag ")
                ids.forEach { id ->
                    sb.append(id).append('=')
                        .append(DapRealtime.readParam(id)?.toString() ?: "null").append(' ')
                }
                android.util.Log.i("DapDiag", sb.toString())
            }
            android.util.Log.i(
                "DapDiag",
                "end ok=" + r.isSuccess + " err=" + (r.exceptionOrNull()?.toString() ?: "-")
            )
        }.start()
        // 后台隐藏：上次开着就继续让最近任务里看不到本 APP（服务照常跑）
        window.decorView.post {
            if (getSharedPreferences("dolby_tuner", MODE_PRIVATE)
                    .getBoolean("hide_recents", false)
            ) setHideInRecents(this, true)
        }
        val gate = evaluateGate()
        setContent {
            DolbyTunerTheme {
                if (gate.passed) MainApp(this, gate.deviceLine)
                else GateDialog(gate.reasons) { finishAffinity() }
            }
        }
    }
}

@Composable
private fun GateDialog(reasons: List<String>, onExit: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("无法运行", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("当前设备不满足运行条件", fontSize = 14.sp, color = subInk())
                Spacer(Modifier.height(14.dp))
                reasons.forEach {
                    Text("· $it", fontSize = 13.sp, lineHeight = 20.sp, color = subInk())
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = onExit, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("退出", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 主框架：内容区 + 底部液态玻璃 Dock（与 K90 标准版同一套实现） */
@Composable
private fun MainApp(activity: Activity, deviceLine: String) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val currentTab = pagerState.currentPage
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.red * 0.299f + colors.background.green * 0.587f + colors.background.blue * 0.114f < 0.5f
    val ctx = activity.applicationContext
    var deviceTick by remember { mutableIntStateOf(0) }
    // 前台状态：后台不做设备检测/刷新，回前台立刻补一次（省电）
    var inForeground by remember { mutableStateOf(true) }
    DisposableEffect(activity) {
        val owner = activity as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> inForeground = true
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> inForeground = false
                else -> {}
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    LaunchedEffect(inForeground) {
        if (inForeground) deviceTick++   // 回前台：立刻补一次刷新
    }

    // 设备监听放到主层：不管当前在 GEQ / 音效 / 设置，插拔都能及时响应
    DisposableEffect(Unit) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { deviceTick++ }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { deviceTick++ }
        }
        am?.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        onDispose { am?.unregisterAudioDeviceCallback(cb) }
    }

    LaunchedEffect(deviceTick, inForeground) {
        if (!inForeground) return@LaunchedEffect
        val d = withContext(Dispatchers.IO) { detectOutputDevice(ctx) }
        DeviceState.device = d
        DeviceState.bindings = withContext(Dispatchers.IO) { DeviceTuningStore.load(ctx) }
        val prefs = ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
        val prev = prefs.getString("device_last_seen", null)
        if (prev == null) {
            prefs.edit().putString("device_last_seen", d.key).apply()
            if (prefs.getString("device_last_applied", null) == null) {
                prefs.edit().putString("device_last_applied", d.key).apply()
            }
            return@LaunchedEffect
        }
        if (prev == d.key) return@LaunchedEffect
        prefs.edit().putString("device_last_seen", d.key).apply()

        val b = DeviceState.binding
        if (b != null) {
            val p = loadPresets(ctx).firstOrNull { it.name == b.presetName }
            if (p != null) {
                val err = applyPreset(ctx, p)
                // 预设里不含音效页参数时，把 DP 复位，避免沿用上一个设备的声音
                if (err == null && p.scope == SCOPE_DOLBY) resetDpOnly(ctx)
                if (err == null) {
                    prefs.edit().putString("device_last_applied", d.key).apply()
                    DeviceState.notifyApplied()
                }
            }
        } else {
            // 未绑定设备：自动恢复平直 + 默认音效，避免把上一个设备的调音带到外放
            val err = resetToDefault(ctx)
            if (err == null) {
                // 设备切换时音效页要跟着复位；单独点「重置」不会走到这里
                resetDpOnly(ctx)
                prefs.edit().putString("device_last_applied", d.key).apply()
                DeviceState.notifyApplied()
            }
        }
        // 前台自己处理完也通知后台服务刷新常驻通知，
        // 否则服务看到 device_last_applied 已更新就会跳过，通知停在旧设备名上
        if (prefs.getBoolean("device_service", false)) {
            runCatching {
                ctx.startForegroundService(
                    Intent(ctx, DeviceAutomationService::class.java)
                        .setAction(DeviceAutomationService.ACTION_REFRESH)
                )
            }
        }
    }
    // 首次启动：不同机型的杜比默认参数不同，先引导检测一次，把结果当成「重置」的基准
    val scope = rememberCoroutineScope()
    val bootPrefs = ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
    var showBasePrompt by remember {
        mutableStateOf(!DspBaseline.captured(ctx) && !bootPrefs.getBoolean("dsp_base_prompted", false))
    }
    var baseBusy by remember { mutableStateOf(false) }
    var baseMsg by remember { mutableStateOf<String?>(null) }

    if (showBasePrompt) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("先检测本机的杜比音效默认值", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "GEQ 出厂本来就是平直；但不同机型、不同 ROM，杜比出厂时的音效参数并不一样（环绕、人声增强、低音增强）。",
                        fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点下面的按钮会读取本机 DSP 当前的音效参数，记录下来作为「重置」时音效的基准；以后点重置，GEQ 归零、音效恢复成这套值。",
                        fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant
                    )

                    baseMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 11.sp, lineHeight = 17.sp, color = hintInk(Color(0xFFB91C1C), Color(0xFFF87171)))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !baseBusy,
                    onClick = {
                        baseBusy = true
                        baseMsg = null
                        scope.launch {
                            val err = captureDspBaseline(ctx)
                            baseBusy = false
                            if (err == null) {
                                saveFx(ctx, DspBaseline.fx(ctx))
                                bootPrefs.edit().putBoolean("dsp_base_prompted", true).apply()
                                showBasePrompt = false
                                DeviceState.notifyApplied()
                                android.widget.Toast.makeText(
                                    ctx, "已记录本机音效默认值", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                baseMsg = err
                            }
                        }
                    }
                ) { Text(if (baseBusy) "检测中…" else "检测 Dolby DSP参数", fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { activity.finishAffinity() }) { Text("退出APP", fontSize = 12.sp) }
            }
        )
    }
    // 启动动画：内容淡入 + 轻微放大上移；dock 从下方弹入
    val launchAnim = remember { Animatable(0f) }
    val dockAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (Appearance.animLaunch.value) {
            launchAnim.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            dockAnim.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        } else {
            launchAnim.snapTo(1f)
            dockAnim.snapTo(1f)
        }
    }

    var dockPos by remember { mutableStateOf(Offset.Zero) }
    var dockSize by remember { mutableStateOf(IntSize.Zero) }
    val dockDensity = LocalDensity.current
    val pageW = with(dockDensity) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val innerW = with(dockDensity) {
        (dockSize.width.toDp() - 20.dp).toPx()
    }
    val tabW = (innerW / 4f).coerceAtLeast(1f)

    Box(Modifier.fillMaxSize()) {
        // 无极拖动：长按 dock 后把横向位移直接喂给 Pager，
        // 页面 1:1 跟手，松手吸附到最近的一页。
        androidx.compose.runtime.CompositionLocalProvider(
            LocalCardEpoch provides pagerState.currentPage
        ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val p2 = launchAnim.value
                alpha = p2
                translationY = (1f - p2) * 22.dp.toPx()
                val s = 0.945f + 0.055f * p2
                scaleX = s
                scaleY = s
            }
        ) { page ->
            when (page) {
                0 -> GeqScreen(deviceLine)
                1 -> EffectsScreen()
                2 -> ConfigScreen()
                else -> SettingsScreen(activity)
            }
        }
        }

        Box(
            Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .height(62.dp)
                .graphicsLayer {
                    val p2 = dockAnim.value
                    alpha = p2
                    translationY = (1f - p2) * 64.dp.toPx()
                }
                .onGloballyPositioned {
                    dockPos = it.positionInRoot()
                    dockSize = it.size
                }
                .clip(RoundedCornerShape(28.dp))
                .dockSurface()
                .pointerInput(tabW, pageW) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            val ratio = if (tabW > 1f) pageW / tabW else 1f
                            pagerState.dispatchRawDelta(amount.x * ratio)
                        },
                        onDragEnd = {
                            val p = pagerState.currentPage +
                                    pagerState.currentPageOffsetFraction
                            scope.launch {
                                pagerState.animateScrollToPage(p.roundToInt().coerceIn(0, 3))
                            }
                        }
                    )
                }
        ) {
            val progress = pagerState.currentPage + pagerState.currentPageOffsetFraction
            // 指示器 = 整个 tab 格子（和点击区一样大）
            val thumbW = with(dockDensity) { (tabW - 2.dp.toPx()).coerceAtLeast(24.dp.toPx()) }
            val thumbH = with(dockDensity) { 54.dp.toPx() }
            Box(
                Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (10.dp.toPx() + (tabW - thumbW) / 2f).roundToInt(),
                            ((62.dp.toPx() - thumbH) / 2f).roundToInt()
                        )
                    }
                    // 用绘制位移代替布局位移：拖动时不再每帧重新布局
                    .graphicsLayer { translationX = progress * tabW }
                    .width(with(dockDensity) { thumbW.toDp() })
                    .height(with(dockDensity) { thumbH.toDp() })
                    .clip(RoundedCornerShape(27.dp))
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.14f)
                        else Color.Black.copy(alpha = 0.07f)
                    )
                    .border(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.14f)
                        else Color.Black.copy(alpha = 0.05f),
                        RoundedCornerShape(27.dp)
                    )
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    DockTab("Dolby", Icons.Default.Equalizer, R.drawable.dolby_mark),
                    DockTab("音效", Icons.Default.Tune),
                    DockTab("配置", Icons.Default.Bookmarks),
                    DockTab("设置", Icons.Default.Settings)
                )
                    .forEachIndexed { index, tab ->
                        val selected = currentTab == index
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.10f else 0.94f,
                            animationSpec = spring(
                                dampingRatio = 0.62f,
                                stiffness = 420f
                            ),
                            label = "iconScale"
                        )
                        val tint = if (selected) colors.primary
                        else subInk(0.85f)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .jellyPress(Appearance.jelly.value > 0).clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    scope.launch {
                                        if (Appearance.animPage.value) {
                                            pagerState.animateScrollToPage(index)
                                        } else {
                                            pagerState.scrollToPage(index)
                                        }
                                    }
                                }
                        ) {
                            if (tab.resId != null) {
                                Icon(
                                    painterResource(tab.resId), tab.label,
                                    Modifier.size(20.dp).graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                                    tint = tint
                                )
                            } else {
                                Icon(
                                    tab.icon, tab.label,
                                    Modifier.size(20.dp).graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                                    tint = tint
                                )
                            }
                            Text(tab.label, fontSize = 9.sp, lineHeight = 11.sp, color = tint)
                        }
                    }
            }
        }
    }
}

internal fun loadGeq(ctx: Context): IntArray {
    val raw = ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE).getString("geq", null)
        ?: return IntArray(DapRealtime.BANDS)
    val a = raw.split(",").mapNotNull { it.toIntOrNull() }
    return if (a.size == DapRealtime.BANDS) a.toIntArray() else IntArray(DapRealtime.BANDS)
}

internal fun saveGeq(ctx: Context, gains: IntArray) {
    ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
        .edit().putString("geq", gains.joinToString(",")).apply()
}

@Composable
private fun GeqScreen(deviceLine: String) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var curve by remember { mutableStateOf(loadGeq(ctx)) }
    // 音效页接管 EQ（dynproc.dp_debug）时，杜比 GEQ 只读；下方音效参数不受影响
    val dpPrefs = remember { ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE) }
    var dpTakeover by remember { mutableStateOf(dpPrefs.getBoolean("dp_debug", false)) }
    var fxStatus by remember { mutableStateOf("已同步 · DSP 实时参数") }
    var status by remember { mutableStateOf("正在连接 Dolby DSP…") }
    var dolbyOn by remember { mutableStateOf<Boolean?>(null) }
    val device = DeviceState.device
    val allBindings = DeviceState.bindings
    val deviceBinding = DeviceState.binding
    var showBindDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        dpTakeover = dpPrefs.getBoolean("dp_debug", false)
        val err = withContext(Dispatchers.IO) { DapRealtime.openSession() }
        if (err != null) {
            status = "打开失败：$err"
        } else {
            // 先按 DSP 中的真实曲线回读；读不到才把本地记录写下去
            val live = withContext(Dispatchers.IO) { DapRealtime.readLive() }
            if (live != null && live.size == DapRealtime.BANDS) {
                curve = live
                saveGeq(ctx, live)
                status = "已同步 · 当前为 DSP 中生效的曲线"
            } else {
                val e = withContext(Dispatchers.IO) { DapRealtime.writeLive(curve) }
                status = if (e == null) "已连接 · 20 段实时生效（DSP 不支持回读）" else "写入失败：$e"
            }
        }
        dolbyOn = withContext(Dispatchers.IO) { DapRealtime.dolbyState() }
    }
    DisposableEffect(Unit) { onDispose { DapRealtime.closeSession() } }

    // 设备切换由主层统一处理，这里只跟随结果刷新曲线
    LaunchedEffect(DeviceState.changeTick) {
        if (DeviceState.changeTick > 0) curve = loadGeq(ctx)
    }

    fun push(index: Int, q4: Int) {
        val next = curve.copyOf()
        next[index] = q4
        curve = next
        saveGeq(ctx, next)
        status = "写入中…"
        kotlinx.coroutines.MainScope().launch {
            val e = withContext(Dispatchers.IO) { DapRealtime.writeLive(next) }
            status = if (e == null) "已写入 · 全部 profile" else "写入失败：$e"
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp).padding(bottom = 96.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("Dolby Tuner", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(deviceLine, fontSize = 11.sp, color = subInk())
        Spacer(Modifier.height(10.dp))

        GlassSurface(
            Modifier.fillMaxWidth(),
            radius = Appearance.radius.value
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎧", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("当前设备", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(device.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("绑定预设：${deviceBinding?.presetName ?: "未绑定"}", fontSize = 11.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showBindDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                    ) { Text("绑定当前设备", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { DeviceState.sync(ctx); showManageDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                    ) { Text("管理绑定", fontSize = 11.sp) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "提示：切到未绑定的设备时会自动重置为平直（GEQ 归零 + 音效默认），不想丢调音就把外放也绑上预设。",
                    fontSize = 9.sp, lineHeight = 13.sp,
                    color = subInk(0.88f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassSurface(
            Modifier.fillMaxWidth(),
            radius = Appearance.radius.value
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Equalizer, null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("20 段图形均衡器", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "47 Hz ~ 19.7 kHz · 实时写入 Dolby DSP",
                            fontSize = 10.sp, color = subInk()
                        )
                    }
                    DolbyBadge(on = dolbyOn)
                }
                if (dpTakeover) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Appearance.tertiary.value.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Appearance.tertiary.value.copy(alpha = 0.45f))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Icon(Icons.Default.GraphicEq, null, tint = Appearance.tertiary.value, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "已被音效页接管：杜比 GEQ 已切平直，这一栏只读。下方的音效参数仍可正常调节。",
                                fontSize = 9.sp, lineHeight = 13.sp, color = subInk()
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                BandCurveCanvas(
                    points = DapRealtime.FREQS.mapIndexed { i, f -> f to curve[i] },
                    lineColor = Accent,
                    showDelta = false
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    items(DapRealtime.BANDS) { i ->
                        DapBandSlider(
                            freqLabel = if (DapRealtime.FREQS[i] >= 1000)
                                fmtFreq(DapRealtime.FREQS[i])
                            else "${DapRealtime.FREQS[i]}",
                            valueQ4 = curve[i],
                            onChange = { q4 -> push(i, q4) },
                            maxAbsQ4 = DapRealtime.MAX_BOOST_Q4,
                            trackHeight = 240.dp,
                            accent = Accent,
                            enabled = !dpTakeover,
                            trackColor = cs.outline.copy(alpha = 0.30f),
                            tickColor = cs.onSurfaceVariant,
                            valueColor = cs.onSurfaceVariant,
                            freqColor = cs.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Swipe, null,
                        tint = subInk(0.75f), modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "左右滑动查看 20 段 · 上下拖动调节 · 双击某段归零",
                        color = subInk(0.85f), fontSize = 9.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "单位：dB（1 段 = 1/16 dB）。合计建议不超过 +24 dB，超过 +10 dB 会标黄提示。",
                    color = subInk(0.85f), fontSize = 9.sp, lineHeight = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = cs.outline)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                status = "正在读取…"
                                val read = withContext(Dispatchers.IO) { DapRealtime.readCurve() }
                                if (read != null) {
                                    curve = read
                                    status = "已读取当前曲线"
                                } else {
                                    status = "读取失败：该机型不支持回读（setter-only）"
                                }
                                dolbyOn = withContext(Dispatchers.IO) { DapRealtime.dolbyState() }
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !dpTakeover,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("读取当前", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val flat = IntArray(DapRealtime.BANDS) { 0 }
                            curve = flat
                            saveGeq(ctx, flat)
                            status = "写入中…"
                            scope.launch {
                                val e = withContext(Dispatchers.IO) { DapRealtime.writeLive(flat) }
                                status = if (e == null) "已置为平直曲线（点保存才会持久化）" else "失败：$e"
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !dpTakeover,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB45309))
                    ) {
                        Icon(Icons.Default.HorizontalRule, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重置平直", fontSize = 11.sp)
                    }
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            status = withContext(Dispatchers.IO) { DapRealtime.probeRead() }
                        }
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        "读不到实时值？点这里诊断", fontSize = 9.sp,
                        color = subInk(0.9f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(status, fontSize = 10.sp, color = cs.onSurfaceVariant, maxLines = 2)

                Spacer(Modifier.height(18.dp))
                // 设备切换 / 外部应用预设后 changeTick++，用 key 强制重建音效页，
                // 让新预设里的 EQ 强度、AutoEQ 曲线等立刻反映到界面上
                androidx.compose.runtime.key(DeviceState.changeTick) {
                    FxSection(onStatus = { fxStatus = it })
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    fxStatus,
                    fontSize = 9.sp, color = subInk(0.88f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (showBindDialog) {
        val list = loadPresets(ctx)
        AlertDialog(
            onDismissRequest = { showBindDialog = false },
            title = { Text("绑定当前设备", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(device.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("选择一个预设，切到这台设备时自动套用", fontSize = 10.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (list.isEmpty()) {
                        Text("还没有预设，先在音效页保存一个", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    list.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().jellyPress(Appearance.jelly.value > 0).clickable {
                                DeviceTuningStore.bind(ctx, device, p.name)
                                DeviceState.sync(ctx)
                                showBindDialog = false
                                status = "正在套用：${p.name}…"
                                scope.launch {
                                    val err = applyPreset(ctx, p)
                                    curve = loadGeq(ctx)
                                    status = if (err == null) "已绑定并套用：${p.name}" else "套用失败：$err"
                                    if (err == null) ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
                                        .edit().putString("device_last_applied", device.key).apply()
                                }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("GEQ ${p.geq.count { it != 0 }} 段", fontSize = 9.sp, color = cs.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showBindDialog = false }) { Text("取消", fontSize = 12.sp) } }
        )
    }

    if (showManageDialog) {
        AlertDialog(
            onDismissRequest = { showManageDialog = false },
            title = { Text("管理绑定", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (allBindings.isEmpty()) {
                        Text("还没有任何设备绑定", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    allBindings.forEach { b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.deviceName, fontSize = 12.sp)
                                Text("→ ${b.presetName}", fontSize = 9.sp, color = cs.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                DeviceTuningStore.unbind(ctx, b.key)
                                DeviceState.sync(ctx)
                            }) { Text("删除", fontSize = 11.sp, color = hintInk(Color(0xFFB45309), Color(0xFFFBBF24))) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showManageDialog = false }) { Text("关闭", fontSize = 12.sp) } }
        )
    }
}
internal data class FxState(
    val hpVirtual: Boolean = false,
    val spVirtual: Boolean = false,
    val bassOn: Boolean = false,
    val bassGain: Int = 0,
    val bassCutoff: Int = 150,
    val bassWidth: Int = 0,
    val dialogOn: Boolean = false,
    val dialogAmt: Int = 4,
    // 虚拟低频（杜比 DAP 的 vbm / vbog / vbhm）
    val vbOn: Boolean = false,
    val vbAmt: Int = 160,
    val vbBlend: Int = 128,
    val vbHarm: Int = 96,
    val vbLow: Int = 100
)

/** 音效页（DynamicsProcessing）参数快照 */
internal class DpSnapshot(
    val curve: FloatArray,
    val eqTab: Int,
    val geqQ4: IntArray,
    val geqQ: Int,
    val geqMaster: Int,
    val peq: String,
    val gain: Int,
    val limOn: Boolean,
    val lim: IntArray,          // thr, atk, rel, rat, post, link
    val mbcOn: Boolean,
    val mbc: List<IntArray>,
    val postOn: Boolean,
    val post: IntArray,
    val elOn: Boolean,
    val elBase: Int,
    val elStrength: Int,
    val elMaxDb: Int,
    // 强度 / AutoEQ（v2.12.5 起，旧预设没有这几项就走默认值）
    val eqIntensity: Int = 100,
    val geqInt: IntArray = IntArray(DapRealtime.BANDS) { 100 },
    val aeq: String = ""
)

internal const val SCOPE_DOLBY = 0
internal const val SCOPE_DP = 1
internal const val SCOPE_BOTH = 2

internal fun dpPack(d: DpSnapshot): String = listOf(
    d.gain.toString(),
    if (d.limOn) "1" else "0",
    d.lim.joinToString(","),
    if (d.mbcOn) "1" else "0",
    d.mbc.joinToString(":") { it.joinToString(",") },
    if (d.postOn) "1" else "0",
    d.post.joinToString(","),
    if (d.elOn) "1" else "0",
    d.elBase.toString(),
    d.elStrength.toString(),
    d.elMaxDb.toString(),
    d.eqTab.toString(),
    d.geqQ4.joinToString(","),
    d.geqQ.toString(),
    d.geqMaster.toString(),
    d.peq,
    d.curve.joinToString(","),
    d.eqIntensity.toString(),
    d.geqInt.joinToString(","),
    d.aeq
).joinToString("|")

internal fun dpUnpack(s: String): DpSnapshot? {
    if (s.isBlank()) return null
    var p = s.split("|")
    // 旧格式（v2.13.x）带混响：索引 5/6 是 revOn/rev，22/23 是 revMode/revPreset
    if (p.size == 24) {
        p = p.filterIndexed { i, _ -> i != 5 && i != 6 && i != 22 && i != 23 }
    }
    if (p.size < 20) return null
    return runCatching {
        DpSnapshot(
            curve = p[16].split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
                .let { if (it.size == 127) it.map { v -> v.coerceIn(-40f, 40f) }.toFloatArray() else FloatArray(127) },
            eqTab = p[11].toInt(),
            geqQ4 = p[12].split(",").mapNotNull { it.toIntOrNull() }.toIntArray()
                .let { if (it.size == DapRealtime.BANDS) it else IntArray(DapRealtime.BANDS) },
            geqQ = p[13].toInt(),
            geqMaster = p[14].toInt(),
            peq = p[15],
            gain = p[0].toInt().coerceIn(-200, 200),
            limOn = p[1] == "1",
            lim = p[2].split(",").mapNotNull { it.toIntOrNull() }.toIntArray()
                .let { a ->
                    if (a.size >= 6) intArrayOf(
                        a[0].coerceIn(-600, 0), a[1].coerceIn(0, 1000), a[2].coerceIn(1, 2000),
                        a[3].coerceIn(10, 1000), a[4].coerceIn(-200, 200), a[5].coerceIn(0, 3)
                    ) else intArrayOf(-20, 10, 60, 100, 0, 0)
                },
            mbcOn = p[3] == "1",
            mbc = p[4].split(":").mapNotNull { seg ->
                seg.split(",").mapNotNull { it.toIntOrNull() }.toIntArray().takeIf { it.size >= 9 }
            }.ifEmpty { defaultMbcInts() },
            postOn = p[5] == "1",
            post = p[6].split(",").mapNotNull { it.toIntOrNull() }.toIntArray(),
            elOn = p[7] == "1",
            elBase = p[8].toInt(),
            elStrength = p[9].toInt(),
            elMaxDb = p[10].toInt(),
            eqIntensity = p.getOrNull(17)?.toIntOrNull() ?: 100,
            geqInt = (p.getOrNull(18) ?: "").split(",").mapNotNull { it.toIntOrNull() }.toIntArray()
                .let { if (it.size == DapRealtime.BANDS) it else IntArray(DapRealtime.BANDS) { 100 } },
            aeq = p.getOrNull(19) ?: ""
        )
    }.getOrNull()
}

internal data class Preset(
    val name: String,
    val geq: IntArray,
    val fx: FxState,
    val dp: DpSnapshot? = null,
    val scope: Int = SCOPE_DOLBY,
    // 音效页的两个宏（不属于 DpSnapshot，单独存，带默认值兼容旧预设）
    val bbOn: Boolean = false,
    val bbAmt: Int = 60,
    val dsOn: Boolean = false,
    val dsAmt: Int = 40,
    /** 等比增益：按曲线自动反向补偿 */
    val autoGain: Boolean = false
)

// ── 预设 JSON 的读写工具：杜比侧字段 + 音效页两个宏 ──
internal fun org.json.JSONObject.putFxJson(f: FxState) {
    put("hp", if (f.hpVirtual) 1 else 0)
    put("sp", if (f.spVirtual) 1 else 0)
    put("bass", if (f.bassOn) 1 else 0)
    put("gain", f.bassGain)
    put("cutoff", f.bassCutoff)
    put("width", f.bassWidth)
    put("dialog", if (f.dialogOn) 1 else 0)
    put("damt", f.dialogAmt)
    put("vb", if (f.vbOn) 1 else 0)
    put("vba", f.vbAmt)
    put("vbb", f.vbBlend)
    put("vbh", f.vbHarm)
    put("vbl", f.vbLow)
}

internal fun org.json.JSONObject.readFxJson(): FxState = FxState(
    optInt("hp", 0) == 1, optInt("sp", 0) == 1,
    optInt("bass", 0) == 1, optInt("gain", 0),
    optInt("cutoff", 150), optInt("width", 0),
    optInt("dialog", 0) == 1, optInt("damt", 4),
    optInt("vb", 0) == 1, optInt("vba", 160), optInt("vbb", 128),
    optInt("vbh", 96), optInt("vbl", 100)
)

internal fun org.json.JSONObject.putDpMacros(p: Preset) {
    put("bb", if (p.bbOn) 1 else 0)
    put("bba", p.bbAmt)
    put("ds", if (p.dsOn) 1 else 0)
    put("dsa", p.dsAmt)
    put("ag", if (p.autoGain) 1 else 0)
}

internal fun loadFx(ctx: Context): FxState {
    val a = ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
        .getString("fx", null)?.split(",") ?: return FxState()
    return try {
        FxState(
            a.getOrNull(0) == "1", a.getOrNull(1) == "1", a.getOrNull(2) == "1",
            a.getOrNull(3)?.toIntOrNull() ?: 0,
            a.getOrNull(4)?.toIntOrNull() ?: 150,
            a.getOrNull(5)?.toIntOrNull() ?: 0,
            a.getOrNull(6) == "1",
            a.getOrNull(7)?.toIntOrNull() ?: 4,
            a.getOrNull(8) == "1",
            a.getOrNull(9)?.toIntOrNull() ?: 160,
            a.getOrNull(10)?.toIntOrNull() ?: 128,
            a.getOrNull(11)?.toIntOrNull() ?: 96,
            a.getOrNull(12)?.toIntOrNull() ?: 100
        )
    } catch (_: Exception) { FxState() }
}

internal fun saveFx(ctx: Context, f: FxState) {
    val v = listOf(
        if (f.hpVirtual) "1" else "0",
        if (f.spVirtual) "1" else "0",
        if (f.bassOn) "1" else "0",
        f.bassGain.toString(), f.bassCutoff.toString(), f.bassWidth.toString(),
        if (f.dialogOn) "1" else "0", f.dialogAmt.toString(),
        if (f.vbOn) "1" else "0", f.vbAmt.toString(), f.vbBlend.toString(),
        f.vbHarm.toString(), f.vbLow.toString()
    ).joinToString(",")
    ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE).edit().putString("fx", v).apply()
}

private fun presetFile(ctx: Context) = File(ctx.filesDir, "presets.json")

internal fun loadPresets(ctx: Context): List<Preset> {
    return try {
        val f = presetFile(ctx)
        if (!f.exists()) return emptyList()
        val arr = org.json.JSONArray(f.readText())
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val g = o.getJSONArray("geq")
                val geq = IntArray(g.length()) { g.getInt(it) }
                val dstr = o.optString("dp", "")
                if (geq.size != DapRealtime.BANDS) null else Preset(
                    o.getString("name"), geq,
                    o.readFxJson(),
                    dpUnpack(dstr),
                    o.optInt("scope", SCOPE_DOLBY),
                    o.optInt("bb", 0) == 1, o.optInt("bba", 60),
                    o.optInt("ds", 0) == 1, o.optInt("dsa", 40),
                    o.optInt("ag", 0) == 1
                )
            }.getOrNull()
        }
    } catch (_: Exception) { emptyList() }
}

internal fun writePresets(ctx: Context, list: List<Preset>) {
    val arr = org.json.JSONArray()
    list.forEach { p ->
        arr.put(org.json.JSONObject().apply {
            put("name", p.name)
            put("geq", org.json.JSONArray(p.geq.toList()))
            putFxJson(p.fx)
            put("scope", p.scope)
            put("dp", p.dp?.let { dpPack(it) } ?: "")
            putDpMacros(p)
        })
    }
    presetFile(ctx).writeText(arr.toString(2))
}

/** 单个预设 → JSON 文本（导出用） */
private fun presetJsonString(p: Preset): String = org.json.JSONObject().apply {
    put("name", p.name)
    put("geq", org.json.JSONArray(p.geq.toList()))
    putFxJson(p.fx)
    put("scope", p.scope)
    put("dp", p.dp?.let { dpPack(it) } ?: "")
    putDpMacros(p)
}.toString(2)

/** 正式写入全部音效参数（失败返回原因） */
private suspend fun pushFx(next: FxState): String? = withContext(Dispatchers.IO) {
    var e = DapRealtime.setParamValue(101, if (next.hpVirtual) 1 else 0)
    if (e == null) e = DapRealtime.setParamValue(102, if (next.spVirtual) 1 else 0)
    if (e == null) e = DapRealtime.setParamValue(105, if (next.dialogOn) 1 else 0)
    if (e == null) e = DapRealtime.setParamValue(108, next.dialogAmt)
    if (e == null) e = DapRealtime.setParamValue(111, if (next.bassOn) 1 else 0)
    if (e == null) e = DapRealtime.setParamValue(233, next.bassGain)
    if (e == null) e = DapRealtime.setParamValue(234, next.bassCutoff)
    if (e == null) e = DapRealtime.setParamValue(235, next.bassWidth)
    if (e == null) e = DapRealtime.writeVirtualBass(next.vbOn, next.vbAmt, next.vbBlend, next.vbHarm, next.vbLow)
    e
}

/**
 * 等比增益：只做「往下拉」。
 *
 * 曲线整体抬高多少，就补回多少，最多 -10 dB。
 * 曲线凹陷时不再往上补（以前是双向 ±24 dB，遇到局部深谷会被放大成巨响，
 * 遇到窄峰又会被能量平均放大成 -20 dB 的闷声）。
 */
/** 从最近任务列表隐藏 / 恢复本 APP（服务不受影响） */
internal fun setHideInRecents(ctx: android.content.Context, hide: Boolean) {
    runCatching {
        val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager ?: return
        am.appTasks.firstOrNull()?.setExcludeFromRecents(hide)
    }
}

internal fun autoGainOf(curve: FloatArray): Float {
    if (curve.isEmpty()) return 0f
    var s = 0.0
    for (v in curve) s += Math.pow(10.0, (v / 10.0))
    val net = 10.0 * Math.log10(s / curve.size)
    return (-net).coerceIn(-10.0, 0.0).toFloat()
}

/** Q6 线性值 → dB 文本（64 = 0 dB，0 = -∞） */
private fun q6db(v: Int): String =
    if (v <= 0) "-∞ dB" else String.format("%+.1f dB", 20.0 * Math.log10(v / 64.0))


/** 应用一条预设：GEQ 写入并提交 + 全部音效参数，成功时同步本地记录 */
private suspend fun applyDolbyPreset(ctx: Context, p: Preset): String? {
    val e1 = withContext(Dispatchers.IO) { DapRealtime.writeLive(p.geq) }
    val e2 = if (e1 == null) withContext(Dispatchers.IO) { DapRealtime.commit(p.geq) } else e1
    val e3 = if (e2 == null) pushFx(p.fx) else e2
    if (e3 == null) {
        saveGeq(ctx, p.geq)
        saveFx(ctx, p.fx)
    }
    return e3
}

private fun parsePeqStr(s: String): List<PeqBand> = s.split(";").mapNotNull { seg ->
    val a = seg.split(",").mapNotNull { it.toFloatOrNull() }
    if (a.size == 3) PeqBand(a[0], a[1], a[2]) else null
}.ifEmpty { defaultPeq() }

/** 从 dynproc 偏好读取当前音效页参数，打包成预设用的快照 */
internal fun loadDpSnapshot(ctx: Context): DpSnapshot {
    val p = ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE)
    val geq = loadDpGeq(p)
    val q = p.getInt("geq_q", 10)
    val m = p.getInt("geq_master", 0)
    val eqTab = p.getInt("eq_tab", 0)
    val peq = loadPeq(p)
    val elOn = p.getBoolean("el_on", false)
    val elBase = p.getInt("el_base", -1)
    val elStrength = p.getInt("el_strength", 10)
    val elMaxDb = p.getInt("el_maxdb", 80)
    val gi = (p.getString("geq_int", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
        .let { if (it.size == DapRealtime.BANDS) it.toIntArray() else IntArray(DapRealtime.BANDS) { 100 } }
    val inten = p.getInt("eq_intensity", 100)
    val aeqCsv = p.getString("aeq", "") ?: ""
    val aeqCurve = AutoEq.fromCsv(aeqCsv)
    val base = aeqCurve ?: if (eqTab == 0) {
        EqMath.geq20To127(
            IntArray(DapRealtime.BANDS) { i ->
                (geq.getOrElse(i) { 0 } * gi.getOrElse(i) { 100 } / 100f).toInt()
            }, q / 10f, m / 10f
        )
    } else EqMath.peqTo127(peq)
    val raw = AutoEq.scale(base, inten)
    var pct = 100
    runCatching {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val mx = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            .coerceAtLeast(1)
        pct = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100 / mx
    }
    val curve = if (elOn && elBase > 0) {
        EqMath.applyEqualLoudness(raw, EqMath.FREQ127, pct, elBase, elStrength / 10f, elMaxDb / 10f)
    } else raw
    return DpSnapshot(
        curve = curve, eqTab = eqTab, geqQ4 = geq, geqQ = q, geqMaster = m,
        peq = peq.joinToString(";") { "${it.freqHz},${it.gainDb},${it.q}" },
        gain = p.getInt("gain", 0),
        limOn = p.getBoolean("lim_on", true),
        lim = intArrayOf(
            p.getInt("lim_thr", -20), p.getInt("lim_atk", 10), p.getInt("lim_rel", 60),
            p.getInt("lim_rat", 100), p.getInt("lim_post", 0), p.getInt("lim_link", 0)
        ),
        mbcOn = p.getBoolean("mbc_on", false), mbc = loadMbc(p),
        postOn = p.getBoolean("post_on", false), post = loadPost(p),
        elOn = elOn, elBase = elBase, elStrength = elStrength, elMaxDb = elMaxDb,
        eqIntensity = inten, geqInt = gi, aeq = aeqCsv
    )
}

internal suspend fun applyPreset(ctx: Context, p: Preset): String? {
    var e: String? = null
    if (p.scope == SCOPE_DOLBY || p.scope == SCOPE_BOTH) e = applyDolbyPreset(ctx, p)
    if (e == null && (p.scope == SCOPE_DP || p.scope == SCOPE_BOTH)) {
        e = applyDpPreset(ctx, p.dp, p.bbOn, p.bbAmt, p.dsOn, p.dsAmt, p.autoGain)
    }
    return e
}

private suspend fun applyDpPreset(
    ctx: Context,
    d: DpSnapshot?,
    bbOn: Boolean = false, bbAmt: Int = 60,
    dsOn: Boolean = false, dsAmt: Int = 40,
    autoGain: Boolean = false
): String? {
    if (d == null) return "预设里没有音效页参数"
    val engineOn = ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE)
        .getBoolean("on", false)
    return withContext(Dispatchers.IO) {
        // 总开关关着：只记录参数，等用户自己开开关时再下发
        if (!engineOn) {
            saveDpPrefs(ctx, d)
            ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE).edit()
                .putBoolean("bb_on", bbOn).putInt("bb_amt", bbAmt)
                .putBoolean("ds_on", dsOn).putInt("ds_amt", dsAmt)
                .putBoolean("auto_gain", autoGain)
                .apply()
            return@withContext null
        }
        val ok = DynProc.start(
            0f,
            DynProc.LimiterCfg(
                enabled = d.limOn,
                thresholdDb = d.lim.getOrElse(0) { -20 } / 10f,
                attackMs = d.lim.getOrElse(1) { 10 } / 10f,
                releaseMs = d.lim.getOrElse(2) { 60 }.toFloat(),
                ratio = d.lim.getOrElse(3) { 100 } / 10f,
                postGainDb = d.lim.getOrElse(4) { 0 } / 10f,
                linkGroup = d.lim.getOrElse(5) { 0 }
            ),
            applyDpMacros(dpMbc(d.mbc, d.mbcOn), bbOn, bbAmt, dsOn, dsAmt)
        )
        if (!ok) return@withContext "音效引擎启动失败"
        DynProc.setOutGain(if (autoGain) autoGainOf(d.curve) else d.gain / 10f)
        DynProc.setPreEq(d.curve)
        if (d.postOn) DynProc.setPostEq(d.post, true)
        saveDpPrefs(ctx, d)
        ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE).edit()
            .putBoolean("bb_on", bbOn).putInt("bb_amt", bbAmt)
            .putBoolean("ds_on", dsOn).putInt("ds_amt", dsAmt)
            .putBoolean("auto_gain", autoGain)
            .apply()
        null
    }
}

private fun saveDpPrefs(ctx: Context, d: DpSnapshot) {
    val p = ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE)
    p.edit()
        .putInt("gain", d.gain)
        .putBoolean("lim_on", d.limOn)
        .putInt("lim_thr", d.lim.getOrElse(0) { -20 })
        .putInt("lim_atk", d.lim.getOrElse(1) { 10 })
        .putInt("lim_rel", d.lim.getOrElse(2) { 60 })
        .putInt("lim_rat", d.lim.getOrElse(3) { 100 })
        .putInt("lim_post", d.lim.getOrElse(4) { 0 })
        .putInt("lim_link", d.lim.getOrElse(5) { 0 })
        .putBoolean("mbc_on", d.mbcOn)
        .putBoolean("post_on", d.postOn)
        .putInt("eq_tab", d.eqTab)
        .putInt("geq_q", d.geqQ)
        .putInt("geq_master", d.geqMaster)
        .putBoolean("el_on", d.elOn)
        .putInt("el_base", d.elBase)
        .putInt("el_strength", d.elStrength)
        .putInt("el_maxdb", d.elMaxDb)
        .putInt("eq_intensity", d.eqIntensity)
        .putString("geq_int", d.geqInt.joinToString(","))
        .putString("aeq", d.aeq)
        .apply()
    saveDpGeq(p, d.geqQ4)
    savePeq(p, parsePeqStr(d.peq))
    saveMbc(p, d.mbc)
    savePost(p, d.post)
}

/** 把「低频增强 / 齿音抑制」两个宏叠到 MBC 上（与音效页里的算法完全一致） */
internal fun applyDpMacros(
    base: List<DynProc.MbcBandCfg>,
    bbOn: Boolean, bbAmt: Int,
    dsOn: Boolean, dsAmt: Int
): List<DynProc.MbcBandCfg> {
    val out = base.toMutableList()
    if (bbOn && out.isNotEmpty()) {
        val b = out[0]
        out[0] = b.copy(
            enabled = true,
            postGainDb = (b.postGainDb + bbAmt / 10f).coerceIn(-40f, 40f)
        )
    }
    if (dsOn && out.size > 2) {
        val b = out[2]
        val f = dsAmt / 100f
        out[2] = b.copy(
            enabled = true,
            attackMs = minOf(b.attackMs, 1.5f),
            releaseMs = minOf(b.releaseMs, 70f),
            ratio = maxOf(b.ratio, 4.8f * f + 1.2f),
            thresholdDb = minOf(b.thresholdDb, -8f - 32f * f),
            kneeWidth = maxOf(b.kneeWidth, 8f),
            postGainDb = (b.postGainDb - f * 5.5f).coerceIn(-40f, 40f)
        )
    }
    return out
}

internal fun dpMbc(l: List<IntArray>, on: Boolean): List<DynProc.MbcBandCfg> = l.map { a ->
    DynProc.MbcBandCfg(
        enabled = on && a.getOrElse(9) { 0 } == 1,
        thresholdDb = a.getOrElse(0) { -200 } / 10f,
        ratio = a.getOrElse(1) { 30 } / 10f,
        attackMs = a.getOrElse(2) { 10 } / 10f,
        releaseMs = a.getOrElse(3) { 80 }.toFloat(),
        kneeWidth = a.getOrElse(4) { 60 }.toFloat(),
        noiseGateDb = a.getOrElse(5) { -900 } / 10f,
        expanderRatio = a.getOrElse(6) { 10 } / 10f,
        preGainDb = a.getOrElse(7) { 0 } / 10f,
        postGainDb = a.getOrElse(8) { 0 } / 10f
    )
}

/**
 * DSP 出厂默认「音效」参数基准。
 * GEQ 出厂本来就是平直，不需要检测；真正会因机型 / ROM 而不同的是音效页这组参数
 *（环绕、人声增强、低音增强及其增益 / 截止 / 带宽），所以只把音效参数记成基准。
 */
internal object DspBaseline {
    private const val PREF = "dolby_tuner"
    private const val K_FX = "dsp_base_fx"
    private const val K_READY = "dsp_base_ready"

    fun captured(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(K_READY, false)

    fun fx(ctx: Context): FxState {
        val a = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(K_FX, null)?.split(",") ?: return FxState()
        return try {
            FxState(
                a.getOrNull(0) == "1", a.getOrNull(1) == "1", a.getOrNull(2) == "1",
                a.getOrNull(3)?.toIntOrNull() ?: 0,
                a.getOrNull(4)?.toIntOrNull() ?: 150,
                a.getOrNull(5)?.toIntOrNull() ?: 0,
                a.getOrNull(6) == "1",
                a.getOrNull(7)?.toIntOrNull() ?: 4
            )
        } catch (_: Exception) { FxState() }
    }

    fun save(ctx: Context, fx: FxState) {
        val v = listOf(
            if (fx.hpVirtual) "1" else "0",
            if (fx.spVirtual) "1" else "0",
            if (fx.bassOn) "1" else "0",
            fx.bassGain.toString(), fx.bassCutoff.toString(), fx.bassWidth.toString(),
            if (fx.dialogOn) "1" else "0", fx.dialogAmt.toString()
        ).joinToString(",")
        val ed = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(K_FX, v)
            .putBoolean(K_READY, true)
        ed.apply()
    }
}

/** 把文本报告写到 Download/DolbyTuner/<name> */
private fun saveTextReport(ctx: Context, name: String, text: String): String? {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DolbyTuner")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        "Download/DolbyTuner/$name"
    } catch (t: Throwable) {
        null
    }
}

/**
 * 读取当前 DSP 的音效参数，作为「重置」时的音效默认值。
 * 成功返回 null；读不到时返回原因（此时保留原有基准不动）。
 */
internal suspend fun captureDspBaseline(ctx: Context): String? = withContext(Dispatchers.IO) {
    fun pick(id: Int, lo: Int, hi: Int): Int? =
        runCatching { DapRealtime.readParam(id) }.getOrNull()?.takeIf { it in lo..hi }

    val hp = pick(101, 0, 1)
    val sp = pick(102, 0, 1)
    val dialog = pick(105, 0, 1)
    val damt = pick(108, 0, 16)
    val bass = pick(111, 0, 1)
    val gain = pick(233, 0, 192)
    val cutoff = pick(234, 80, 300)
    val width = pick(235, 0, 64)
    val hit = listOf(hp, sp, dialog, damt, bass, gain, cutoff, width).count { it != null }

    // 音效页（PARA 通道）可读回；A 类（FourCC）本机只写不读，所以不采集，直接用官方基线
    if (hit == 0) {
        return@withContext "读取失败：本机实时层不支持回读，暂时只能用内置默认值"
    }

    DspBaseline.save(
        ctx,
        FxState(
            hpVirtual = hp == 1,
            spVirtual = sp == 1,
            bassOn = bass == 1,
            bassGain = gain ?: 0,
            bassCutoff = cutoff ?: 150,
            bassWidth = width ?: 0,
            dialogOn = dialog == 1,
            dialogAmt = damt ?: 4
        )
    )
    null
}

/** 恢复为已检测的 DSP 默认值（没检测过则退回平直 + 内置默认）；未绑定设备时也用这套 */
/** 音效页（DP）恢复出厂：只写 dynproc 偏好，不动 Dolby 侧 */
internal fun resetDpPrefsToDefault(ctx: Context) {
    val p = ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE)
    val flatGeq = IntArray(DapRealtime.BANDS)
    val peq = defaultPeq()
    val mbc = defaultMbcInts()
    val post = IntArray(11)
    p.edit()
        .putInt("gain", 0).putBoolean("lim_on", true)
        .putInt("lim_thr", -20).putInt("lim_atk", 10).putInt("lim_rel", 60)
        .putInt("lim_rat", 100).putInt("lim_post", 0).putInt("lim_link", 0)
        .putBoolean("mbc_on", false)
        .putBoolean("post_on", false).putInt("eq_tab", 0)
        .putInt("geq_q", 10).putInt("geq_master", 0)
        .putBoolean("el_on", false).putInt("el_base", -1)
        .putInt("el_strength", 10).putInt("el_maxdb", 80)
        .putInt("eq_intensity", 100).putString("aeq", "")
        .putString("geq_int", IntArray(DapRealtime.BANDS) { 100 }.joinToString(","))
        .putBoolean("bb_on", false).putInt("bb_amt", 60)
        .putBoolean("ds_on", false).putInt("ds_amt", 40)
        .putBoolean("auto_gain", false)
        .apply()
    saveDpGeq(p, flatGeq)
    savePeq(p, peq)
    saveMbc(p, mbc)
    savePost(p, post)
}

/** 音效页（DP）参数与运行中的 DSP 一并恢复默认 */
internal suspend fun resetDpOnly(ctx: Context) {
    resetDpPrefsToDefault(ctx)
    // 总开关没开：只把记录复位，不去碰 DSP，也绝不把开关打开
    if (!ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE).getBoolean("on", false)) return
    withContext(Dispatchers.IO) {
        runCatching {
            if (DynProc.running) {
                DynProc.setInputGain(0f)
                DynProc.setOutGain(0f)
                DynProc.setPreEq(FloatArray(127))
                DynProc.setLimiter(DynProc.LimiterCfg())
                DynProc.setMbc(DynProc.defaultMbc())
                DynProc.setPostEq(IntArray(10), false)
            }
        }
    }
}

internal suspend fun resetToDefault(ctx: Context): String? {
    val flat = IntArray(DapRealtime.BANDS)
    val def = if (DspBaseline.captured(ctx)) DspBaseline.fx(ctx) else FxState()
    var e = withContext(Dispatchers.IO) { DapRealtime.writeLive(flat) }
    if (e == null) e = pushFx(def)
    // 最后再保存一次：Dolby 的保存命令会把「当前全部参数」落进它自己的存储，
    // 这样重启之后还是重置后的状态，不会白重置。
    if (e == null) e = withContext(Dispatchers.IO) { DapRealtime.commit(flat) }
    if (e == null) {
        saveGeq(ctx, flat)
        saveFx(ctx, def)
    }
    return e
}

/** 音效页：环绕 / 低音 / 重置 / 预设 */
@Composable
private fun FxSection(onStatus: (String) -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var fx by remember { mutableStateOf(loadFx(ctx)) }
    // 系统 Dolby 自己的配置（persist 属性，每次进页面重新读）
    var sysDolby by remember { mutableStateOf<SysDolby.State?>(null) }
    // 滑杆实时写入通道：只保留最新值，避免拖动时写入排队
    val liveFx = remember { Channel<Pair<Int, Int>>(Channel.CONFLATED) }
    LaunchedEffect(Unit) {
        for ((id, v) in liveFx) {
            withContext(Dispatchers.IO) { DapRealtime.setParamValue(id, v) }
        }
    }
    var status by remember { mutableStateOf("实时层参数 · 改完立即生效") }
    LaunchedEffect(status) { onStatus(status) }
    var saving by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    // 设备切换（主层套用预设）后，音效页的开关与滑杆跟着刷新
    LaunchedEffect(DeviceState.changeTick) {
        if (DeviceState.changeTick > 0) fx = loadFx(ctx)
    }
    // 进入音效页时先读 DSP 真实值；读不到（或数值越界）就保留本地记录
    LaunchedEffect(Unit) {
        // 系统 Dolby 自己的配置：每次进页面重新读一遍
        sysDolby = withContext(Dispatchers.IO) { SysDolby.read() }

        val synced = withContext(Dispatchers.IO) {
            fun pick(v: Int?, lo: Int, hi: Int) = v?.takeIf { it in lo..hi }
            var next = fx
            var hit = 0
            pick(DapRealtime.readParam(101), 0, 1)?.let { next = next.copy(hpVirtual = it == 1); hit++ }
            pick(DapRealtime.readParam(102), 0, 1)?.let { next = next.copy(spVirtual = it == 1); hit++ }
            pick(DapRealtime.readParam(105), 0, 1)?.let { next = next.copy(dialogOn = it == 1); hit++ }
            pick(DapRealtime.readParam(108), 0, 16)?.let { next = next.copy(dialogAmt = it); hit++ }
            pick(DapRealtime.readParam(111), 0, 1)?.let { next = next.copy(bassOn = it == 1); hit++ }
            pick(DapRealtime.readParam(233), 0, 192)?.let { next = next.copy(bassGain = it); hit++ }
            pick(DapRealtime.readParam(234), 80, 300)?.let { next = next.copy(bassCutoff = it); hit++ }
            pick(DapRealtime.readParam(235), 0, 64)?.let { next = next.copy(bassWidth = it); hit++ }
            if (hit > 0) next to hit else null
        }
        if (synced != null) {
            fx = synced.first
            saveFx(ctx, synced.first)
            status = "已同步 ${synced.second}/8 项 DSP 实时参数"
        }
        }
    DisposableEffect(Unit) { onDispose { DapRealtime.closeSession() } }

    fun apply(next: FxState) {
        fx = next
        saveFx(ctx, next)
        status = "写入中…"
        scope.launch {
            val err = pushFx(next)
            status = if (err == null) "已写入 · 重启后仍生效" else "写入失败：$err"
        }
    }



    Column(Modifier.fillMaxWidth()) {


        Spacer(Modifier.height(4.dp))
        // ── 虚拟低频：拖动时 60ms 节流写一次（三个参数要一起发）──
        val vbHolder = remember { arrayOfNulls<FxState>(1) }
        vbHolder[0] = fx
        val vbThrottle = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
        val vbTick = remember {
            Runnable {
                val s = vbHolder[0]
                if (s != null) scope.launch {
                    withContext(Dispatchers.IO) {
                        DapRealtime.writeVirtualBass(s.vbOn, s.vbAmt, s.vbBlend, s.vbHarm, s.vbLow)
                    }
                }
            }
        }
        fun pushVb(next: FxState, live: Boolean) {
            fx = next
            saveFx(ctx, next)
            vbHolder[0] = next
            vbThrottle.removeCallbacks(vbTick)
            if (live) vbThrottle.postDelayed(vbTick, 60L) else vbThrottle.post(vbTick)
        }
        Spacer(Modifier.height(8.dp))
        EffectCard(
            title = "虚拟低频",
            subtitle = "低频谐波再生 · 小音量下补回下潜",
            icon = Icons.Default.VolumeUp,
            enabled = fx.vbOn,
            onEnabledChange = { pushVb(fx.copy(vbOn = it), false) },
            accent = Appearance.colorDyn.value
        ) {
            FxSlider(
                "强度", fx.vbAmt.toString(), fx.vbAmt, 0..192, fx.vbOn,
                onLive = { pushVb(fx.copy(vbAmt = it), true) }
            ) { pushVb(fx.copy(vbAmt = it), false) }
            FxSlider(
                "谐波混合", fx.vbBlend.toString(), fx.vbBlend, 0..192, fx.vbOn,
                onLive = { pushVb(fx.copy(vbBlend = it), true) }
            ) { pushVb(fx.copy(vbBlend = it), false) }
            FxSlider(
                "谐波增益", fx.vbHarm.toString(), fx.vbHarm, 0..192, fx.vbOn,
                onLive = { pushVb(fx.copy(vbHarm = it), true) }
            ) { pushVb(fx.copy(vbHarm = it), false) }
            FxSlider(
                "起始频率 · Hz", fx.vbLow.toString(), fx.vbLow, 40..200, fx.vbOn,
                onLive = { pushVb(fx.copy(vbLow = it), true) }
            ) { pushVb(fx.copy(vbLow = it), false) }
            Text(
                "走杜比 DAP 自身的谐波再生：音量越小越明显，音量大时几乎听不出",
                fontSize = 9.sp, color = cs.onSurfaceVariant
            )
        }
        EffectCard(
            title = "低音增强",
            subtitle = "bass 111 · 增益 / 截止 / 带宽",
            icon = Icons.Default.GraphicEq,
            enabled = fx.bassOn,
            onEnabledChange = { apply(fx.copy(bassOn = it)) },
            accent = Appearance.colorLimit.value
        ) {
            FxSlider(
                "低音增益", fx.bassGain.toString(), fx.bassGain, 0..192, fx.bassOn,
                onLive = { fx = fx.copy(bassGain = it); liveFx.trySend(233 to it) }
            ) { apply(fx.copy(bassGain = it)) }
            FxSlider(
                "低音截止 · Hz", fx.bassCutoff.toString(), fx.bassCutoff, 80..300, fx.bassOn,
                onLive = { fx = fx.copy(bassCutoff = it); liveFx.trySend(234 to it) }
            ) { apply(fx.copy(bassCutoff = it)) }
            FxSlider(
                "低带宽", fx.bassWidth.toString(), fx.bassWidth, 0..64, fx.bassOn,
                onLive = { fx = fx.copy(bassWidth = it); liveFx.trySend(235 to it) }
            ) { apply(fx.copy(bassWidth = it)) }
        }
        EffectCard(
            title = "人声增强",
            subtitle = "deon 105 · 只提升人声频段",
            icon = Icons.Default.RecordVoiceOver,
            enabled = fx.dialogOn,
            onEnabledChange = { apply(fx.copy(dialogOn = it)) },
            accent = Appearance.accent.value
        ) {
            FxSlider(
                "增强量", fx.dialogAmt.toString(), fx.dialogAmt, 0..16, fx.dialogOn,
                onLive = { fx = fx.copy(dialogAmt = it); liveFx.trySend(108 to it) }
            ) { apply(fx.copy(dialogAmt = it)) }
            Text("不压低背景（ded 未使用）", fontSize = 9.sp, color = cs.onSurfaceVariant)
        }

        EffectCard(
            title = "耳机虚拟环绕",
            subtitle = "vdhe 101 · 耳机下的声场扩展",
            icon = Icons.Default.Headphones,
            enabled = fx.hpVirtual,
            onEnabledChange = { apply(fx.copy(hpVirtual = it)) },
            accent = Appearance.secondary.value,
            toggleOnly = true
        )
        EffectCard(
            title = "扬声器虚拟环绕",
            subtitle = "vspe 102 · 外放下的声场扩展",
            icon = Icons.Default.Speaker,
            enabled = fx.spVirtual,
            onEnabledChange = { apply(fx.copy(spVirtual = it)) },
            accent = Appearance.secondary.value,
            toggleOnly = true
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { showSaveConfirm = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            enabled = !saving
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("保存到 Dolby（GEQ + 音效）", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEF4444).copy(alpha = 0.88f)
            )
        ) {
            Text(
                "重置为 DSP 默认值（GEQ + 音效）",
                fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))
        
        Spacer(Modifier.height(10.dp))

        if (showSaveConfirm) {
            AlertDialog(
                onDismissRequest = { showSaveConfirm = false },
                title = { Text("保存到 Dolby", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column {
                        Text("将把当前 GEQ 20 段与全部音效参数写入：", fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("① 杜比引擎内存（立刻生效）", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Text("② Dolby DAP 参数存储（重启后沿用）", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text("不重启音频服务。确定保存？", fontSize = 11.sp, color = Accent)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSaveConfirm = false
                        if (saving) return@TextButton
                        saving = true
                        status = "正在保存 GEQ + 音效…"
                        scope.launch {
                            val g = loadGeq(ctx)
                            var e = withContext(Dispatchers.IO) { DapRealtime.writeLive(g) }
                            if (e == null) e = withContext(Dispatchers.IO) { DapRealtime.commit(g) }
                            val err = if (e == null) pushFx(fx) else e
                            status = when {
                                err != null -> "保存失败：$err"
                                else -> "已保存 · GEQ + 音效重启后沿用"
                            }
                            if (err == null) showSavedDialog = true
                            saving = false
                        }
                    }) { Text("保存", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveConfirm = false }) { Text("取消", fontSize = 12.sp) }
                }
            )
        }

        if (showResetConfirm) {
            val hasBase = DspBaseline.captured(ctx)
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("重置为 DSP 默认值", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column {
                        Text("将执行：", fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("• GEQ 20 段全部归零（出厂即平直）", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Text("• 恢复本机官方基线（写死值，不读 DSP）", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        if (hasBase) {
                            Text("• 音效恢复为检测到的本机默认状态", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        } else {
                            Text("• 音效恢复内置默认：环绕 / 人声增强 / 低音增强全部关闭，低音增益与带宽归 0", fontSize = 11.sp, color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("尚未检测本机音效默认参数，当前使用的是内置默认值。", fontSize = 11.sp, color = hintInk(Color(0xFFB45309), Color(0xFFFBBF24)))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("会立即写入 Dolby 并自动保存，重启后仍是重置状态。此操作不可撤销。", fontSize = 11.sp, color = hintInk(Color(0xFFB45309), Color(0xFFFBBF24)))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showResetConfirm = false
                        val def = if (hasBase) DspBaseline.fx(ctx) else FxState()
                        fx = def
                        saveFx(ctx, def)
                        saveGeq(ctx, IntArray(DapRealtime.BANDS))
                        status = "正在重置…"
                        scope.launch {
                            val e2 = resetToDefault(ctx)
                            status = when {
                                e2 != null -> "重置失败：$e2"
                                hasBase -> "已恢复为本机 DSP 默认值"
                                else -> "已恢复为内置默认值（未检测本机参数）"
                            }
                        }
                    }) { Text("重置", color = hintInk(Color(0xFFB45309), Color(0xFFFBBF24)), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) { Text("取消", fontSize = 12.sp) }
                }
            )
        }

        if (showSavedDialog) {
            AlertDialog(
                onDismissRequest = { showSavedDialog = false },
                title = { Text("已保存到杜比", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column {
                        Text("GEQ 20 段 + 全部音效参数已写入：", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("① 杜比引擎内存（立刻生效）", fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 16.sp)
                        Text("② Dolby DAP 参数存储（重启后沿用）", fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "写入杜比引擎 + 参数存储 · 不重启音频服务 · 重启后沿用",
                            fontSize = 10.sp, color = cs.onSurfaceVariant, lineHeight = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("重启手机后会继续沿用这次保存的参数。", fontSize = 11.sp, color = Accent, lineHeight = 16.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSavedDialog = false }) {
                        Text("知道了", color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

private fun loadDpGeq(prefs: android.content.SharedPreferences): IntArray {
    val s = prefs.getString("dp_geq", "").orEmpty()
    if (s.isBlank()) return IntArray(DapRealtime.BANDS)
    val a = s.split(",").mapNotNull { it.trim().toIntOrNull() }
    return if (a.size == DapRealtime.BANDS) a.toIntArray() else IntArray(DapRealtime.BANDS)
}

private fun saveDpGeq(prefs: android.content.SharedPreferences, a: IntArray) {
    prefs.edit().putString("dp_geq", a.joinToString(",")).apply()
}

private fun defaultPeq(): List<PeqBand> = listOf(
    PeqBand(100f, 0f, 1f),
    PeqBand(1000f, 0f, 1f),
    PeqBand(8000f, 0f, 1f)
)

private fun loadPeq(prefs: android.content.SharedPreferences): List<PeqBand> {
    val s = prefs.getString("dp_peq", "").orEmpty()
    if (s.isBlank()) return defaultPeq()
    if (s.contains(";")) {
        val l = s.split(";").filter { it.isNotBlank() }.mapNotNull { seg ->
            val a = seg.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (a.size == 3) PeqBand(a[0], a[1], a[2]) else null
        }
        return if (l.isNotEmpty()) l else defaultPeq()
    }
    val a = s.split(",").mapNotNull { it.trim().toFloatOrNull() }
    if (a.size == 30) return (0 until 10).map { i -> PeqBand(a[i * 3], a[i * 3 + 1], a[i * 3 + 2]) }
    return defaultPeq()
}

private fun savePeq(prefs: android.content.SharedPreferences, l: List<PeqBand>) {
    val s = l.joinToString(";") { "${it.freqHz},${it.gainDb},${it.q}" }
    prefs.edit().putString("dp_peq", s).apply()
}

private fun saveDolbyGeqBackup(prefs: android.content.SharedPreferences, a: IntArray) {
    prefs.edit().putString("dolby_geq_backup", a.joinToString(",")).apply()
}

private fun loadDolbyGeqBackup(prefs: android.content.SharedPreferences): IntArray? {
    val s = prefs.getString("dolby_geq_backup", "").orEmpty()
    if (s.isBlank()) return null
    val a = s.split(",").mapNotNull { it.trim().toIntOrNull() }
    return if (a.size == DapRealtime.BANDS) a.toIntArray() else null
}

private fun loadPost(prefs: android.content.SharedPreferences): IntArray {
    val s = prefs.getString("post_data", "").orEmpty()
    if (s.isBlank()) return IntArray(11)
    val a = s.split(",").mapNotNull { it.trim().toIntOrNull() }
    return if (a.size == 11) a.toIntArray() else IntArray(11)
}

private fun savePost(prefs: android.content.SharedPreferences, a: IntArray) {
    prefs.edit().putString("post_data", a.joinToString(",")).apply()
}

private fun defaultMbcInts(): List<IntArray> =
    List(4) { intArrayOf(-200, 30, 10, 80, 60, -900, 10, 0, 0, 0) }

private fun loadMbc(prefs: android.content.SharedPreferences): List<IntArray> {
    val s = prefs.getString("mbc_data", "").orEmpty()
    if (s.isBlank()) return defaultMbcInts()
    val parts = s.split(";")
    if (parts.size != 4) return defaultMbcInts()
    return parts.map { seg ->
        val a = seg.split(",").mapNotNull { it.trim().toIntOrNull() }
        when {
            a.size >= 10 -> a.take(10).toIntArray()
            a.size == 9 -> (a + 1).toIntArray()
            else -> intArrayOf(-200, 30, 10, 80, 60, -900, 10, 0, 0, 0)
        }
    }
}

private fun saveMbc(prefs: android.content.SharedPreferences, l: List<IntArray>) {
    prefs.edit().putString("mbc_data", l.joinToString(";") { it.joinToString(",") }).apply()
}

@Composable
private fun EffectsScreen() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE) }

    var dpOn by remember { mutableStateOf(prefs.getBoolean("on", false)) }
    var gain by remember { mutableIntStateOf(prefs.getInt("gain", 0)) }
    // 等比增益：按当前 EQ 的整体提升自动反向补偿，让音量保持不变
    var autoGain by remember { mutableStateOf(prefs.getBoolean("auto_gain", false)) }
    var limOn by remember { mutableStateOf(prefs.getBoolean("lim_on", true)) }
    var thr by remember { mutableIntStateOf(prefs.getInt("lim_thr", -20)) }
    var atk by remember { mutableIntStateOf(prefs.getInt("lim_atk", 10)) }
    var rel by remember { mutableIntStateOf(prefs.getInt("lim_rel", 60)) }
    var rat by remember { mutableIntStateOf(prefs.getInt("lim_rat", 100)) }
    var post by remember { mutableIntStateOf(prefs.getInt("lim_post", 0)) }
    var link by remember { mutableIntStateOf(prefs.getInt("lim_link", 0)) }
    var mbcOn by remember { mutableStateOf(prefs.getBoolean("mbc_on", false)) }
    var bbOn by remember { mutableStateOf(prefs.getBoolean("bb_on", false)) }
    var bbAmt by remember { mutableIntStateOf(prefs.getInt("bb_amt", 60)) }
    var dsOn by remember { mutableStateOf(prefs.getBoolean("ds_on", false)) }
    var dsAmt by remember { mutableIntStateOf(prefs.getInt("ds_amt", 40)) }
    var postOn by remember { mutableStateOf(prefs.getBoolean("post_on", false)) }
    var postData by remember { mutableStateOf(loadPost(prefs)) }
    var eqTab by remember { mutableIntStateOf(prefs.getInt("eq_tab", 0)) }
    var debugOn by remember { mutableStateOf(prefs.getBoolean("dp_debug", false)) }
    var showDebugDlg by remember { mutableStateOf(false) }
    var delPeqIdx by remember { mutableIntStateOf(-1) }
    var geqQ4 by remember { mutableStateOf(loadDpGeq(prefs)) }
    var peqData by remember { mutableStateOf(loadPeq(prefs)) }
    var peqSel by remember { mutableIntStateOf(0) }
    var eqCurve by remember { mutableStateOf(FloatArray(127)) }
    var eqRaw by remember { mutableStateOf(FloatArray(127)) }

    // ===== AutoEQ 导入 + EQ 强度（整体 / 逐段）=====
    var aeqCurve by remember { mutableStateOf(AutoEq.fromCsv(prefs.getString("aeq", "") ?: "")) }
    var showAeqDlg by remember { mutableStateOf(false) }
    var aeqText by remember { mutableStateOf("") }
    var eqIntensity by remember { mutableIntStateOf(prefs.getInt("eq_intensity", 100)) }
    var geqInt by remember {
        mutableStateOf(
            (prefs.getString("geq_int", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
                .let { if (it.size == DapRealtime.BANDS) it.toIntArray() else IntArray(DapRealtime.BANDS) { 100 } }
        )
    }

    // ===== 等响补偿 =====
    var elOn by remember { mutableStateOf(prefs.getBoolean("el_on", false)) }
    var elBase by remember { mutableIntStateOf(prefs.getInt("el_base", -1)) }
    var elStrength by remember { mutableIntStateOf(prefs.getInt("el_strength", 10)) }
    var elMaxDb by remember { mutableIntStateOf(prefs.getInt("el_maxdb", 80)) }
    val amVol = remember {
        ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val volMaxV = remember {
        amVol.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var curVolPct by remember {
        mutableIntStateOf(
            amVol.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100 / volMaxV
        )
    }

    fun finalEq(raw: FloatArray): FloatArray =
        if (elOn && elBase > 0) {
            EqMath.applyEqualLoudness(
                raw, EqMath.FREQ127, curVolPct, elBase,
                elStrength / 10f, elMaxDb / 10f
            )
        } else raw

    var sel by remember { mutableIntStateOf(prefs.getInt("mbc_sel", 0)) }
    var mbcStore by remember { mutableStateOf(loadMbc(prefs)) }
    var status by remember { mutableStateOf(DynProc.status) }
    var busy by remember { mutableStateOf(false) }

    fun toDynMbc(l: List<IntArray>) = l.map { a ->
        DynProc.MbcBandCfg(
            enabled = mbcOn && a[9] == 1,
            thresholdDb = a[0] / 10f,
            ratio = a[1] / 10f,
            attackMs = a[2] / 10f,
            releaseMs = a[3].toFloat(),
            kneeWidth = a[4].toFloat(),
            noiseGateDb = a[5] / 10f,
            expanderRatio = a[6] / 10f,
            preGainDb = a[7] / 10f,
            postGainDb = a[8] / 10f
        )
    }

    // ── 两个「宏」：低频增强 / 齿音抑制，都叠在 MBC 对应段上，不改用户自己的段设置 ──
    fun withMacros(base: List<DynProc.MbcBandCfg>): List<DynProc.MbcBandCfg> {
        val out = base.toMutableList()
        if (bbOn && out.isNotEmpty()) {
            val b = out[0]
            out[0] = b.copy(
                enabled = true,
                postGainDb = (b.postGainDb + bbAmt / 10f).coerceIn(-40f, 40f)
            )
        }
        if (dsOn && out.size > 2) {
            val b = out[2]
            val f = dsAmt / 100f
            out[2] = b.copy(
                enabled = true,
                attackMs = minOf(b.attackMs, 1.5f),
                releaseMs = minOf(b.releaseMs, 70f),
                ratio = maxOf(b.ratio, 4.8f * f + 1.2f),
                thresholdDb = minOf(b.thresholdDb, -8f - 32f * f),
                kneeWidth = maxOf(b.kneeWidth, 8f),
                postGainDb = (b.postGainDb - f * 5.5f).coerceIn(-40f, 40f)
            )
        }
        return out
    }
    fun pushMacros() {
        val d = withMacros(toDynMbc(mbcStore))
        scope.launch {
            withContext(Dispatchers.IO) { DynProc.setMbc(d) }
            status = DynProc.status
        }
    }
    fun applyMbc() {
        val d = withMacros(toDynMbc(mbcStore))
        scope.launch {
            withContext(Dispatchers.IO) { DynProc.setMbc(d) }
            status = DynProc.status
        }
    }

    fun updMbc(idx: Int, v: Int) {
        val l = mbcStore.toMutableList()
        val a = l[sel].copyOf()
        a[idx] = v
        l[sel] = a
        mbcStore = l
        saveMbc(prefs, l)
        applyMbc()
    }

    // ── 拖动节流：拖动期间不改 Compose state，避免整页重组 ──
    val throttle = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val macroTick = remember { Runnable { pushMacros() } }
    val pendLim = remember { intArrayOf(thr, atk, rel, rat, post, link) }
    val pendMbc = remember { mbcStore[sel].copyOf() }

    fun limFrom(p: IntArray) = DynProc.LimiterCfg(
        limOn, p[0] / 10f, p[1] / 10f, p[2].toFloat(), p[3] / 10f, p[4] / 10f, p[5]
    )

    val limTick = remember {
        Runnable {
            val c = limFrom(pendLim)
            scope.launch {
                withContext(Dispatchers.IO) { DynProc.setLimiter(c) }
                status = DynProc.status
            }
        }
    }

    fun liveLim(i: Int, v: Int) {
        pendLim[i] = v
        throttle.removeCallbacks(limTick)
        throttle.postDelayed(limTick, 60L)
    }

    fun commitLim(i: Int, v: Int) {
        pendLim[i] = v
        thr = pendLim[0]; atk = pendLim[1]; rel = pendLim[2]
        rat = pendLim[3]; post = pendLim[4]; link = pendLim[5]
        prefs.edit().putInt("lim_thr", thr).putInt("lim_atk", atk)
            .putInt("lim_rel", rel).putInt("lim_rat", rat)
            .putInt("lim_post", post).putInt("lim_link", link).apply()
        throttle.removeCallbacks(limTick)
        throttle.post(limTick)
    }

    val mbcTick = remember {
        Runnable {
            val l = mbcStore.toMutableList()
            l[sel] = pendMbc.copyOf()
            val d = withMacros(toDynMbc(l))
            scope.launch {
                withContext(Dispatchers.IO) { DynProc.setMbc(d) }
                status = DynProc.status
            }
        }
    }

    fun liveMbc(i: Int, v: Int) {
        pendMbc[i] = v
        throttle.removeCallbacks(mbcTick)
        throttle.postDelayed(mbcTick, 60L)
    }

    fun commitMbc(i: Int, v: Int) {
        pendMbc[i] = v
        val l = mbcStore.toMutableList()
        l[sel] = pendMbc.copyOf()
        mbcStore = l
        saveMbc(prefs, l)
        throttle.removeCallbacks(mbcTick)
        throttle.post(mbcTick)
    }

    val pendPost = remember { postData.copyOf() }
    val postTick = remember {
        Runnable {
            val d = pendPost.copyOf()
            val on = postOn
            scope.launch {
                withContext(Dispatchers.IO) { DynProc.setPostEq(d, on) }
                status = DynProc.status
            }
        }
    }

    fun livePost(i: Int, v: Int) {
        pendPost[i] = v
        throttle.removeCallbacks(postTick)
        throttle.postDelayed(postTick, 60L)
    }

    fun commitPost(i: Int, v: Int) {
        pendPost[i] = v
        postData = pendPost.copyOf()
        savePost(prefs, postData)
        throttle.removeCallbacks(postTick)
        throttle.post(postTick)
    }

    var geqQ by remember { mutableIntStateOf(prefs.getInt("geq_q", 10)) }
    var geqMaster by remember { mutableIntStateOf(prefs.getInt("geq_master", 0)) }

    /**
     * 当前 EQ 的原始曲线（未套等响补偿）：
     * AutoEQ 导入 > GEQ(20 段 × 逐段强度) > PEQ，最后统一乘整体强度。
     */
    fun rawCurve(peqOverride: List<PeqBand>? = null): FloatArray {
        val ae = aeqCurve
        val base: FloatArray = if (ae != null) {
            ae.copyOf()
        } else if (eqTab == 0) {
            val scaled = IntArray(DapRealtime.BANDS) { i ->
                (geqQ4.getOrElse(i) { 0 } * geqInt.getOrElse(i) { 100 } / 100f).toInt()
            }
            EqMath.geq20To127(scaled, geqQ / 10f, geqMaster / 10f)
        } else {
            EqMath.peqTo127(peqOverride ?: peqData)
        }
        return AutoEq.scale(base, eqIntensity)
    }

    var pendFreq by remember { mutableIntStateOf(500) }
    var pendGain by remember { mutableIntStateOf(0) }
    var pendQ by remember { mutableIntStateOf(100) }

    fun posToFreq(v: Int): Float = (20.0 * Math.pow(1000.0, v / 1000.0)).toFloat()

    fun freqToPos(f: Float): Int =
        ((Math.log(f / 20.0) / Math.log(1000.0)) * 1000.0).toInt().coerceIn(0, 1000)

    fun selectPeq(i: Int) {
        if (i !in peqData.indices) return
        peqSel = i
        val b = peqData[i]
        pendFreq = freqToPos(b.freqHz)
        pendGain = (b.gainDb * 10).toInt()
        pendQ = (b.q * 100).toInt()
    }

    fun peqLive(): List<PeqBand> {
        val l = peqData.toMutableList()
        if (peqSel in l.indices) {
            l[peqSel] = PeqBand(posToFreq(pendFreq), pendGain / 10f, pendQ / 100f)
        }
        return l
    }

    /** 等比增益要补的 dB：取最终曲线的能量平均，反向抵消 */
    fun autoGainDb(): Float = autoGainOf(eqCurve)

    fun pushEq() {
        val raw = rawCurve(peqLive())
        eqRaw = raw
        val curve = finalEq(raw)
        eqCurve = curve
        scope.launch {
            withContext(Dispatchers.IO) { DynProc.setPreEq(curve) }
            status = DynProc.status
        }
        if (autoGain) {
            val ag = autoGainDb()
            scope.launch {
                withContext(Dispatchers.IO) { DynProc.setOutGain(ag) }
                status = DynProc.status
            }
        }
    }

    /** 音效页一键重置（总开关不动） */
    fun resetDpAll() {
        gain = 0
        autoGain = false
        limOn = true; thr = -20; atk = 10; rel = 60; rat = 100; post = 0; link = 0
        mbcOn = false; mbcStore = defaultMbcInts()
        bbOn = false; bbAmt = 60
        dsOn = false; dsAmt = 40
        postOn = false; postData = IntArray(11)
        eqTab = 0; geqQ4 = IntArray(DapRealtime.BANDS); geqQ = 10; geqMaster = 0
        peqData = defaultPeq()
        eqRaw = FloatArray(127); eqCurve = FloatArray(127)
        aeqCurve = null; eqIntensity = 100
        geqInt = IntArray(DapRealtime.BANDS) { 100 }
        elOn = false; elBase = -1; elStrength = 10; elMaxDb = 80
        prefs.edit()
            .putInt("gain", 0).putBoolean("auto_gain", false).putBoolean("lim_on", true)
            .putInt("lim_thr", -20).putInt("lim_atk", 10).putInt("lim_rel", 60)
            .putInt("lim_rat", 100).putInt("lim_post", 0).putInt("lim_link", 0)
            .putBoolean("mbc_on", false)
            .putBoolean("bb_on", false).putInt("bb_amt", 60)
            .putBoolean("ds_on", false).putInt("ds_amt", 40)
            .putBoolean("post_on", false).putInt("eq_tab", 0)
            .putInt("geq_q", 10).putInt("geq_master", 0)
            .putInt("eq_intensity", 100).putString("aeq", "")
            .putString("geq_int", IntArray(DapRealtime.BANDS) { 100 }.joinToString(","))
            .putBoolean("el_on", false).putInt("el_base", -1)
            .putInt("el_strength", 10).putInt("el_maxdb", 80)
            .apply()
        saveDpGeq(prefs, geqQ4)
        savePeq(prefs, peqData)
        saveMbc(prefs, mbcStore)
        savePost(prefs, postData)
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    DynProc.setInputGain(0f)
                    DynProc.setOutGain(0f)
                    DynProc.setPreEq(FloatArray(127))
                    DynProc.setLimiter(
                        DynProc.LimiterCfg(
                            enabled = true, thresholdDb = -2f, attackMs = 1f,
                            releaseMs = 60f, ratio = 10f, postGainDb = 0f
                        )
                    )
                    DynProc.setMbc(dpMbc(defaultMbcInts(), false))
                    DynProc.setPostEq(IntArray(10), false)
                }
            }
            status = DynProc.status
        }
    }

    DisposableEffect(Unit) {
        val obs = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                val v = amVol.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val pct = v * 100 / volMaxV
                if (pct == curVolPct) return
                curVolPct = pct
                if (elOn) pushEq()
            }
        }
        val cr = ctx.contentResolver
        runCatching {
            cr.registerContentObserver(
                android.provider.Settings.System.getUriFor("volume_music_speaker"), false, obs
            )
            cr.registerContentObserver(
                android.provider.Settings.System.getUriFor("volume_music_headphone"), false, obs
            )
            cr.registerContentObserver(
                android.provider.Settings.System.getUriFor("volume_music_headset"), false, obs
            )
        }
        onDispose { runCatching { cr.unregisterContentObserver(obs) } }
    }

    val eqTick = remember { Runnable { pushEq() } }

    fun scheduleEq() {
        throttle.removeCallbacks(eqTick)
        throttle.postDelayed(eqTick, 60L)
    }

    fun pushGeq(i: Int, q4: Int) {
        if (!debugOn) return
        val n = geqQ4.copyOf()
        n[i] = q4
        geqQ4 = n
        eqRaw = rawCurve()
        eqCurve = finalEq(eqRaw)
        saveDpGeq(prefs, n)
        scheduleEq()
    }

    fun updGeqQ(v: Int) {
        geqQ = v
        prefs.edit().putInt("geq_q", v).apply()
        eqRaw = rawCurve()
        eqCurve = finalEq(eqRaw)
        scheduleEq()
    }

    fun updGeqMaster(v: Int) {
        geqMaster = v
        prefs.edit().putInt("geq_master", v).apply()
        eqRaw = rawCurve()
        eqCurve = finalEq(eqRaw)
        scheduleEq()
    }

    fun livePeq(which: Int, v: Int) {
        if (!debugOn) return
        when (which) {
            0 -> pendFreq = v
            1 -> pendGain = v
            else -> pendQ = v
        }
        scheduleEq()
    }

    fun commitPeq(which: Int, v: Int) {
        if (!debugOn) return
        livePeq(which, v)
        val l = peqLive()
        peqData = l
        savePeq(prefs, l)
        eqRaw = rawCurve(l)
        eqCurve = finalEq(eqRaw)
        scheduleEq()
    }

    fun addPeq() {
        if (peqData.size >= 12) return
        val nf = posToFreq(freqToPos(1000f) + peqData.size * 40)
        val l = peqData + PeqBand(nf, 0f, 1f)
        peqData = l
        savePeq(prefs, l)
        selectPeq(l.size - 1)
    }

    fun delPeq() {
        if (peqData.size <= 1) return
        val l = peqData.toMutableList()
        l.removeAt(peqSel)
        peqData = l
        savePeq(prefs, l)
        selectPeq((peqSel - 1).coerceAtLeast(0))
        eqRaw = rawCurve(l)
        eqCurve = finalEq(eqRaw)
        scheduleEq()
    }

    fun setDebug(on: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { DapRealtime.openSession() }
                if (on) {
                    val live = runCatching { DapRealtime.readLive() }.getOrNull()
                        ?: runCatching { loadGeq(ctx) }.getOrNull()
                    if (live != null) saveDolbyGeqBackup(prefs, live)
                    runCatching { DapRealtime.writeLive(IntArray(DapRealtime.BANDS)) }
                } else {
                    val b = loadDolbyGeqBackup(prefs)
                    if (b != null) runCatching { DapRealtime.writeLive(b) }
                    // 关闭调试：DP 侧自动平直
                    runCatching { DynProc.setPreEq(FloatArray(127)) }
                }
            }
            debugOn = on
            prefs.edit().putBoolean("dp_debug", on).apply()
            if (on) {
                pushEq()
            } else {
                eqCurve = FloatArray(127)
            }
        }
    }


    fun cfg() = DynProc.LimiterCfg(
        enabled = limOn,
        thresholdDb = thr / 10f,
        attackMs = atk / 10f,
        releaseMs = rel.toFloat(),
        ratio = rat / 10f,
        postGainDb = post / 10f,
        linkGroup = link
    )

    fun applyLim(commit: Boolean) {
        val c = cfg()
        scope.launch {
            withContext(Dispatchers.IO) { DynProc.setLimiter(c) }
            status = DynProc.status
            if (commit) {
                prefs.edit().putBoolean("lim_on", limOn)
                    .putInt("lim_thr", thr).putInt("lim_atk", atk)
                    .putInt("lim_rel", rel).putInt("lim_rat", rat)
                    .putInt("lim_post", post).putInt("lim_link", link).apply()
            }
        }
    }


    fun currentRaw(): FloatArray = rawCurve()

    /** 启动/重启引擎，并把界面上的全部参数一次性下发（含 EQ 曲线） */
    fun pushAllDp() {
        val raw = currentRaw()
        eqRaw = raw
        val curve = finalEq(raw)
        eqCurve = curve
        val gDb = if (autoGain) autoGainDb() else gain / 10f
        scope.launch {
            busy = true
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!DynProc.running) {
                        DynProc.start(0f, cfg(), toDynMbc(mbcStore))
                    } else {
                        DynProc.setInputGain(0f)
                    }
                    DynProc.setLimiter(cfg())
                    DynProc.setOutGain(gDb)
                    DynProc.setPreEq(curve)
                    DynProc.setMbc(toDynMbc(mbcStore))
                    if (postOn) DynProc.setPostEq(postData.copyOf(), true)
                    else DynProc.setPostEq(IntArray(10), false)
                }
            }
            status = DynProc.status
            busy = false
        }
    }

    fun toggleEngine(on: Boolean) {
        if (busy) return
        dpOn = on
        prefs.edit().putBoolean("on", on).apply()
        if (on) {
            pushAllDp()
        } else {
            scope.launch {
                busy = true
                withContext(Dispatchers.IO) { DynProc.stop() }
                status = DynProc.status
                busy = false
            }
        }
    }

    // 输出增益：拖动时 50ms 节流一次，避免一次拖动堆出几十个协程互相覆盖
    val gainTick = remember {
        Runnable {
            val g = if (autoGain) autoGainDb() else gain / 10f
            val v = gain
            scope.launch {
                withContext(Dispatchers.IO) { DynProc.setOutGain(g) }
                status = DynProc.status
                prefs.edit().putInt("gain", v).apply()
            }
        }
    }
    fun applyGain() {
        throttle.removeCallbacks(gainTick)
        throttle.postDelayed(gainTick, 50L)
    }

    // ── 磁贴 / 后台服务改了偏好时，页面立刻跟着变（不用切页面再回来） ──
    val uiHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    DisposableEffect(prefs) {
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            uiHandler.post {
                when (key) {
                    "on" -> {
                        val v = prefs.getBoolean("on", false)
                        if (v != dpOn) {
                            dpOn = v
                            if (v) pushAllDp()
                        }
                    }
                    "gain" -> gain = prefs.getInt("gain", 0)
                    "auto_gain" -> autoGain = prefs.getBoolean("auto_gain", false)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    // 兜底：从控制中心回到 APP 时再对一次
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val v = prefs.getBoolean("on", false)
                if (v != dpOn) {
                    dpOn = v
                    if (v) pushAllDp()
                }
                gain = prefs.getInt("gain", 0)
                autoGain = prefs.getBoolean("auto_gain", false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        // 先把曲线按本地记录算出来：总开关关着也要和滑杆一致，不能显示成平线
        runCatching {
            val raw = currentRaw()
            eqRaw = raw
            eqCurve = finalEq(raw)
        }
        if (dpOn) pushAllDp()
    }

    val accent = Appearance.secondary.value
    val cLim = Appearance.colorLimit.value

    androidx.compose.runtime.CompositionLocalProvider(LocalDpOn provides dpOn) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 14.dp).padding(top = 18.dp).padding(bottom = 96.dp)
    ) {
        item {
        Text("DynamicsProcessing音效", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "安卓原生音效 · 依赖 APP在后台运行",
            fontSize = 11.sp, color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        }

        item {
        // ===== 引擎总开关 =====
        GlassSurface(
            Modifier.fillMaxWidth(),
            radius = Appearance.radius.value
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Tune, null, Modifier.size(22.dp),
                    tint = if (dpOn) accent else cs.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("DynamicsProcessing 引擎", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (CallGuard.muted.value) "通话中已暂停"
                        else if (dpOn) status else "音效引擎未运行",
                        fontSize = 9.sp, lineHeight = 11.sp,
                        color = if (dpOn) accent else cs.onSurfaceVariant
                    )
                    Text(
                        "全局生效 · 退出 APP后自动停止",
                        fontSize = 8.5.sp, color = cs.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = dpOn && !CallGuard.muted.value, enabled = !busy && !CallGuard.muted.value,
                    onCheckedChange = { if (!CallGuard.muted.value) toggleEngine(it) }
                )
            }
        }

        }
        Spacer(Modifier.height(8.dp))

        item {
        // ===== DP EQ（GEQ / PEQ） =====
        val cEq = Appearance.tertiary.value
        GlassSurface(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            radius = Appearance.radius.value
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("EQ 调节", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    EqTab("GEQ", eqTab == 0, cEq, dpOn) {
                        eqTab = 0
                        prefs.edit().putInt("eq_tab", 0).apply()
                        eqRaw = rawCurve()
                        eqCurve = finalEq(eqRaw)
                        scheduleEq()
                    }
                    Spacer(Modifier.width(6.dp))
                    EqTab("PEQ", eqTab == 1, cEq, dpOn) {
                        eqTab = 1
                        prefs.edit().putInt("eq_tab", 1).apply()
                        eqRaw = rawCurve()
                        eqCurve = finalEq(eqRaw)
                        scheduleEq()
                    }
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = debugOn,
                        onCheckedChange = { if (dpOn) showDebugDlg = true },
                        enabled = dpOn,
                        modifier = Modifier.height(28.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (debugOn) "杜比 GEQ 已切平直 · 当前 EQ 由 DP 接管"
                    else "打开右侧开关后杜比 GEQ 临时平直，改由 DP 调节",
                    fontSize = 9.sp,
                    color = if (debugOn) Color(0xFF10B981) else cs.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                DpEqCanvas(
                    curve = eqCurve,
                    fMin = if (eqTab == 0) 47f else 20f,
                    fMax = if (eqTab == 0) 19700f else 20000f,
                    accent = cEq
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (aeqCurve != null) "AutoEQ 曲线生效中 · 127 点"
                        else "可从 AutoEQ 的 GraphicEQ 文本导入整条曲线",
                        fontSize = 9.sp,
                        color = if (aeqCurve != null) Color(0xFF10B981) else cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (aeqCurve != null) {
                        TextButton(
                            onClick = {
                                if (!dpOn) return@TextButton
                                aeqCurve = null
                                prefs.edit().putString("aeq", "").apply()
                                eqRaw = rawCurve()
                                eqCurve = finalEq(eqRaw)
                                scheduleEq()
                            },
                            enabled = dpOn
                        ) { Text("清除", fontSize = 10.sp, color = cs.onSurfaceVariant) }
                    }
                    TextButton(
                        onClick = { if (dpOn) { aeqText = ""; showAeqDlg = true } },
                        enabled = dpOn
                    ) { Text("导入 AutoEQ", fontSize = 10.sp, color = cEq) }
                }
                Spacer(Modifier.height(2.dp))

                if (aeqCurve != null) {
                    Text(
                        "GEQ / PEQ 编辑已暂停（曲线来自导入文件）；点「清除」可回到手动调节。",
                        fontSize = 9.sp, color = cs.onSurfaceVariant
                    )
                } else if (eqTab == 0) {
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        items(DapRealtime.BANDS) { i ->
                            DapBandSlider(
                                freqLabel = fmtFreq(DapRealtime.FREQS[i]),
                                valueQ4 = geqQ4[i],
                                onChange = { q4 -> pushGeq(i, q4) },
                                maxAbsQ4 = 320,
                                trackHeight = 200.dp,
                                accent = cEq,
                                trackColor = cs.outline.copy(alpha = 0.30f),
                                tickColor = cs.onSurfaceVariant,
                                valueColor = cs.onSurfaceVariant,
                                freqColor = cs.onSurfaceVariant
                            )
                        }
                    }
                    FxSlider(
                        "段间过渡 · Q", String.format("%.1f", geqQ / 10f), geqQ, 10..100, debugOn,
                        onLive = { updGeqQ(it) }, onDone = { updGeqQ(it) }
                    )
                    FxSlider(
                        "总增益 · Master", String.format("%+.1f dB", geqMaster / 10f), geqMaster, -120..120, debugOn,
                        onLive = { updGeqMaster(it) }, onDone = { updGeqMaster(it) }
                    )
                } else {
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        items(peqData.size) { i ->
                            val b = peqData[i]
                            val sel = peqSel == i
                            Column(
                                Modifier.width(80.dp)
                                    .heightIn(min = 92.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (sel) cEq.copy(alpha = 0.22f)
                                        else cs.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                    .pointerInput(i, peqData.size) {
                                        detectTapGestures(
                                            onTap = { if (dpOn) selectPeq(i) },
                                            onLongPress = { if (dpOn && peqData.size > 1) delPeqIdx = i }
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    fmtFreq(b.freqHz.toInt()),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (sel) cEq else cs.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    String.format("%+.1f dB", b.gainDb),
                                    fontSize = 9.sp,
                                    color = cs.onSurfaceVariant
                                )
                                Text(
                                    String.format("Q %.2f", b.q),
                                    fontSize = 8.sp,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                        item {
                            Box(
                                Modifier.width(80.dp)
                                    .heightIn(min = 92.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cEq.copy(alpha = 0.12f))
                                    .jellyPress(Appearance.jelly.value > 0).clickable(enabled = dpOn) { addPeq() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add, null,
                                    Modifier.size(32.dp), tint = cEq
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (peqSel in peqData.indices) {
                        FxSlider(
                            "中心频率", fmtFreq(posToFreq(pendFreq).toInt()),
                            pendFreq, 0..1000, debugOn,
                            onLive = { livePeq(0, it) }, onDone = { commitPeq(0, it) }
                        )
                        FxSlider(
                            "增益", String.format("%+.1f dB", pendGain / 10f),
                            pendGain, -150..150, debugOn,
                            onLive = { livePeq(1, it) }, onDone = { commitPeq(1, it) }
                        )
                        FxSlider(
                            "Q 值", String.format("%.2f", pendQ / 100f),
                            pendQ, 10..1000, debugOn,
                            onLive = { livePeq(2, it) }, onDone = { commitPeq(2, it) }
                        )
                    }
                }
            }
        }
        }

        item {
        // ===== EQ 强度（整体 / 逐段）=====
        EffectCard(
            title = "EQ 强度",
            subtitle = "整体与逐段缩放 · 100% 为调好的原曲线",
            icon = Icons.Default.Tune,
            enabled = true,
            onEnabledChange = {},
            showSwitch = false,
            initiallyExpanded = true,
            accent = cEq
        ) {
            FxSlider(
                "整体强度", "$eqIntensity%", eqIntensity, 0..150, debugOn,
                onLive = {
                    eqIntensity = it
                    prefs.edit().putInt("eq_intensity", it).apply()
                    scheduleEq()
                },
                onDone = {
                    eqIntensity = it
                    prefs.edit().putInt("eq_intensity", it).apply()
                    scheduleEq()
                }
            )
            if (eqTab == 0 && aeqCurve == null) {
                Spacer(Modifier.height(6.dp))
                Text("逐段强度 · 按住左右拖动", fontSize = 9.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
                ) {
                    items(DapRealtime.BANDS) { i ->
                        PctBandSlider(
                            label = fmtFreq(DapRealtime.FREQS[i]),
                            value = geqInt.getOrElse(i) { 100 },
                            accent = cEq,
                            enabled = debugOn && dpOn,
                            onChange = { v ->
                                val n = geqInt.copyOf()
                                if (i < n.size) n[i] = v
                                geqInt = n
                                prefs.edit().putString("geq_int", n.joinToString(",")).apply()
                                eqRaw = rawCurve()
                                eqCurve = finalEq(eqRaw)
                                scheduleEq()
                            }
                        )
                    }
                }
            } else {
                Text(
                    if (aeqCurve != null) "AutoEQ 曲线模式下只用整体强度"
                    else "PEQ 模式下只用整体强度",
                    fontSize = 9.sp, color = cs.onSurfaceVariant
                )
            }
        }
        }

        item {
        // ===== 等响补偿 =====
        val cEl = Appearance.colorDyn.value
        EffectCard(
            title = "等响补偿",
            subtitle = if (elBase < 0) "请先设定基准音量"
            else "低于基准音量时自动增强低频与高频",
            icon = Icons.Default.Hearing,
            enabled = elOn,
            onEnabledChange = { on ->
                if (on && elBase <= 0) {
                    elBase = curVolPct
                    prefs.edit().putInt("el_base", elBase).apply()
                }
                elOn = on
                prefs.edit().putBoolean("el_on", on).apply()
                if (on) {
                    pushEq()
                } else {
                    eqRaw = rawCurve()
                    eqCurve = eqRaw
                    scope.launch {
                        withContext(Dispatchers.IO) { DynProc.setPreEq(eqRaw) }
                        status = DynProc.status
                    }
                }
            },
            accent = cEl
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("当前音量", fontSize = 9.sp, color = cs.onSurfaceVariant)
                    Text("$curVolPct%", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("基准音量", fontSize = 9.sp, color = cs.onSurfaceVariant)
                    Text(
                        if (elBase > 0) "$elBase%" else "--",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("补偿幅度", fontSize = 9.sp, color = cs.onSurfaceVariant)
                    Text(
                        if (elBase > 0) String.format("%.1f dB", elMaxDb / 10f) else "--",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    elBase = curVolPct
                    prefs.edit().putInt("el_base", elBase).apply()
                    if (elOn) pushEq()
                },
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = dpOn,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cEl)
            ) {
                Text("设定当前系统音量为基准（$curVolPct%）", fontSize = 11.sp)
            }
            if (elBase > 0) {
                Spacer(Modifier.height(4.dp))
                FxSlider(
                    "补偿强度", String.format("%.1f", elStrength / 10f), elStrength, 1..30,
                    dpOn && elBase > 0,
                    onLive = { elStrength = it },
                    onDone = {
                        elStrength = it
                        prefs.edit().putInt("el_strength", it).apply()
                        if (elOn) pushEq()
                    }
                )
                FxSlider(
                    "最大补偿幅度", String.format("%.1f dB", elMaxDb / 10f), elMaxDb, 10..200,
                    dpOn && elBase > 0,
                    onLive = { elMaxDb = it },
                    onDone = {
                        elMaxDb = it
                        prefs.edit().putInt("el_maxdb", it).apply()
                        if (elOn) pushEq()
                    }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "音量越低补偿越多",
                    fontSize = 9.sp, color = cs.onSurfaceVariant
                )
            }
        }
        }

        item {
        // ===== 输入增益 =====
        EffectCard(
            title = "输出增益",
            subtitle = "全链路总电平 · 加减都作用在最终输出上",
            icon = Icons.Default.VolumeUp,
            enabled = dpOn,
            onEnabledChange = {},
            accent = Appearance.accent.value,
            showSwitch = false,
        ) {
            FxSlider(
                "整体增益", (if (gain > 0) "+${gain / 10f}" else "${gain / 10f}") + " dB",
                gain, -200..200, dpOn && !autoGain,
                fmt = { String.format("%+.1f dB", it / 10f) },
                onLive = { gain = it; applyGain() },
                onDone = { gain = it; applyGain() }
            )
            ParamToggle(
                label = "等比增益",
                hint = "按 EQ 的整体提升自动反向补偿，音量听感保持不变（当前 " +
                    String.format("%+.1f dB", autoGainDb()) + "）",
                checked = autoGain,
                enabled = dpOn,
                onCheckedChange = { on ->
                    autoGain = on
                    prefs.edit().putBoolean("auto_gain", on).apply()
                    applyGain()
                }
            )
            Text(
                "作用在限幅之后，提升和衰减都听得出；拖动时自动忽略等响补偿的叠加",
                fontSize = 9.sp, lineHeight = 13.sp, color = cs.onSurfaceVariant
            )
        }
        }

        item {
        // ===== Limiter =====
        EffectCard(
            title = "动态限制器",
            subtitle = "Limiter · 防削波与峰值压制",
            icon = Icons.Default.Waves,
            enabled = limOn,
            onEnabledChange = { limOn = it; applyLim(true) },
            accent = cLim
        ) {
            val presets = listOf(
                Triple("稳妥", intArrayOf(-20, 10, 60, 100, 0), "10:1 · -2dB"),
                Triple("硬顶", intArrayOf(-5, 1, 30, 500, 0), "50:1 · -0.5dB"),
                Triple("冲击", intArrayOf(-40, 5, 100, 80, 0), "8:1 · -4dB"),
                Triple("轻柔", intArrayOf(-30, 50, 200, 40, 0), "4:1 · -3dB")
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { pr ->
                    Surface(
                        Modifier.weight(1f)
                            .jellyPress(Appearance.jelly.value > 0).clickable(enabled = limOn && dpOn) {
                                thr = pr.second[0]; atk = pr.second[1]; rel = pr.second[2]
                                rat = pr.second[3]; post = pr.second[4]
                                applyLim(true)
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = cLim.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, cLim.copy(alpha = 0.4f))
                    ) {
                        Column(
                            Modifier.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(pr.first, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(pr.third, fontSize = 8.sp, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }

            FxSlider(
                "门限阈值 · Threshold", String.format("%+.1f dB", thr / 10f), thr, -600..0, limOn,
                onLive = { liveLim(0, it) },
                onDone = { commitLim(0, it) }
            )
            FxSlider(
                "启动时间 · Attack", String.format("%.1f ms", atk / 10f), atk, 1..1000, limOn,
                onLive = { liveLim(1, it) },
                onDone = { commitLim(1, it) }
            )
            FxSlider(
                "释放时间 · Release", "$rel ms", rel, 1..5000, limOn,
                onLive = { liveLim(2, it) },
                onDone = { commitLim(2, it) }
            )
            FxSlider(
                "压缩比 · Ratio", String.format("%.1f:1", rat / 10f), rat, 10..300, limOn,
                onLive = { liveLim(3, it) },
                onDone = { commitLim(3, it) }
            )
            FxSlider(
                "后级增益 · Post Gain", String.format("%+.1f dB", post / 10f), post, -400..400, limOn,
                onLive = { liveLim(4, it) },
                onDone = { commitLim(4, it) }
            )
            FxSlider(
                "联动组 · Link Group", if (link == 0) "0 · 立体声联动" else "$link", link, 0..3, limOn,
                onLive = { liveLim(5, it) },
                onDone = { commitLim(5, it) }
            )
        }
        }

        item {
        // ===== 低频增强（DP · 叠在 MBC 第 0 段）=====
        EffectCard(
            title = "低频增强",
            subtitle = "250Hz 以下补一档后级增益 · 最多 +12dB",
            icon = Icons.Default.Speaker,
            enabled = bbOn,
            onEnabledChange = {
                bbOn = it
                prefs.edit().putBoolean("bb_on", it).apply()
                pushMacros()
            },
            accent = Appearance.colorBass.value
        ) {
            FxSlider(
                "增强量", String.format("%+.1f dB", bbAmt / 10f), bbAmt, 0..120, bbOn,
                onLive = {
                    bbAmt = it
                    prefs.edit().putInt("bb_amt", it).apply()
                    throttle.removeCallbacks(macroTick)
                    throttle.postDelayed(macroTick, 60L)
                }
            ) {
                bbAmt = it
                prefs.edit().putInt("bb_amt", it).apply()
                throttle.removeCallbacks(macroTick)
                throttle.post(macroTick)
            }
        }
        }

        item {
        // ===== MBC 多段压缩 =====
        EffectCard(
            title = "多段压缩器",
            subtitle = "4段动态 · 250 / 2k / 8k / 20k",
            icon = Icons.Default.Compress,
            enabled = mbcOn,
            onEnabledChange = { mbcOn = it; prefs.edit().putBoolean("mbc_on", it).apply(); applyMbc() },
            accent = Appearance.colorDyn.value
        ) {
            val cMbc = Appearance.colorDyn.value
            val segNames = listOf("Low 250", "Mid 2k", "High 8k", "Air 20k")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                segNames.forEachIndexed { i, n ->
                    Surface(
                        Modifier.weight(1f)
                            .jellyPress(Appearance.jelly.value > 0).clickable { sel = i; mbcStore[i].copyInto(pendMbc); prefs.edit().putInt("mbc_sel", i).apply() },
                        shape = RoundedCornerShape(10.dp),
                        color = cMbc.copy(alpha = if (sel == i) 0.30f else 0.08f),
                        border = BorderStroke(1.dp, cMbc.copy(alpha = if (sel == i) 0.72f else 0.26f))
                    ) {
                        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                n, fontSize = 9.sp,
                                fontWeight = if (sel == i) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${segNames[sel]} 段启用",
                    fontSize = 11.sp, modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.Switch(
                    checked = mbcStore[sel][9] == 1,
                    enabled = mbcOn,
                    onCheckedChange = { updMbc(9, if (it) 1 else 0) }
                )
            }

            val mbcPresets = listOf(
                "轻压" to intArrayOf(-180, 20, 20, 150, 60, -900, 10, 0, 0, 1),
                "收束" to intArrayOf(-240, 40, 5, 80, 40, -900, 10, 30, -30, 1),
                "通透" to intArrayOf(-200, 10, 10, 80, 60, -900, 10, 0, 0, 1)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                mbcPresets.forEach { pr ->
                    Surface(
                        Modifier.weight(1f)
                            .jellyPress(Appearance.jelly.value > 0).clickable(enabled = mbcOn && dpOn) {
                                val l = List(4) { pr.second.copyOf() }
                                mbcStore = l
                                pr.second.copyInto(pendMbc)
                                saveMbc(prefs, l)
                                applyMbc()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = cMbc.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, cMbc.copy(alpha = 0.4f))
                    ) {
                        Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                            Text(pr.first, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            val b = mbcStore[sel]
            FxSlider(
                "阈值 · Threshold", String.format("%.1f dB", b[0] / 10f), b[0], -600..0, mbcOn,
                onLive = { liveMbc(0, it) }, onDone = { commitMbc(0, it) }
            )
            FxSlider(
                "压缩比 · Ratio", String.format("%.1f:1", b[1] / 10f), b[1], 10..300, mbcOn,
                onLive = { liveMbc(1, it) }, onDone = { commitMbc(1, it) }
            )
            FxSlider(
                "启动时间 · Attack", String.format("%.1f ms", b[2] / 10f), b[2], 1..1000, mbcOn,
                onLive = { liveMbc(2, it) }, onDone = { commitMbc(2, it) }
            )
            FxSlider(
                "释放时间 · Release", "${b[3]} ms", b[3], 1..5000, mbcOn,
                onLive = { liveMbc(3, it) }, onDone = { commitMbc(3, it) }
            )
            FxSlider(
                "膝宽 · Knee", "${b[4]}", b[4], 0..60, mbcOn,
                onLive = { liveMbc(4, it) }, onDone = { commitMbc(4, it) }
            )
            FxSlider(
                "噪声门 · Gate", String.format("%.1f dB", b[5] / 10f), b[5], -900..0, mbcOn,
                onLive = { liveMbc(5, it) }, onDone = { commitMbc(5, it) }
            )
            FxSlider(
                "扩展比 · Expander", String.format("%.1f", b[6] / 10f), b[6], 10..100, mbcOn,
                onLive = { liveMbc(6, it) }, onDone = { commitMbc(6, it) }
            )
            FxSlider(
                "前级 · Pre Gain", String.format("%+.1f dB", b[7] / 10f), b[7], -400..400, mbcOn,
                onLive = { liveMbc(7, it) }, onDone = { commitMbc(7, it) }
            )
            FxSlider(
                "后级 · Post Gain", String.format("%+.1f dB", b[8] / 10f), b[8], -400..400, mbcOn,
                onLive = { liveMbc(8, it) }, onDone = { commitMbc(8, it) }
            )
        }
        }

        item {
        // ===== 齿音抑制（DP · 叠在 MBC 第 2 段）=====
        EffectCard(
            title = "齿音抑制",
            subtitle = "8kHz 以上的动态去齿 · 最多 -5.5dB",
            icon = Icons.Default.FilterAlt,
            enabled = dsOn,
            onEnabledChange = {
                dsOn = it
                prefs.edit().putBoolean("ds_on", it).apply()
                pushMacros()
            },
            accent = Appearance.colorVocal.value
        ) {
            FxSlider(
                "抑制量", "${dsAmt}%", dsAmt, 0..100, dsOn,
                onLive = {
                    dsAmt = it
                    prefs.edit().putInt("ds_amt", it).apply()
                    throttle.removeCallbacks(macroTick)
                    throttle.postDelayed(macroTick, 60L)
                }
            ) {
                dsAmt = it
                prefs.edit().putInt("ds_amt", it).apply()
                throttle.removeCallbacks(macroTick)
                throttle.post(macroTick)
            }
        }
        }

        item {
        // ===== 后置均衡器 =====
        EffectCard(
            title = "后置均衡器",
            subtitle = "Post EQ · 10段精细修形",
            icon = Icons.Default.Equalizer,
            enabled = postOn,
            onEnabledChange = {
                postOn = it
                prefs.edit().putBoolean("post_on", it).apply()
                throttle.removeCallbacks(postTick)
                throttle.post(postTick)
            },
            accent = Appearance.secondary.value
        ) {
            val freqs = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
            freqs.forEachIndexed { i, f ->
                FxSlider(
                    "$f Hz", String.format("%+.1f dB", postData[i] / 10f), postData[i], -200..200, postOn,
                    onLive = { livePost(i, it) }, onDone = { commitPost(i, it) }
                )
            }
            FxSlider(
                "左右平衡 · Balance", String.format("%+.1f dB", postData[10] / 10f), postData[10], -100..100, postOn,
                onLive = { livePost(10, it) }, onDone = { commitPost(10, it) }
            )
        }
        }
        item {

        Spacer(Modifier.height(8.dp))
        // ===== 重置 =====
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { resetDpAll() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = dpOn,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEF4444).copy(alpha = 0.88f)
            )
        ) {
            Text(
                "重置音效页为默认值",
                fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                if (busy) "正在下发…" else status,
                fontSize = 10.sp, color = cs.onSurfaceVariant
            )
        }
        }
        }
    }

    if (showAeqDlg) {
        AlertDialog(
            onDismissRequest = { showAeqDlg = false },
            title = { Text("导入 AutoEQ 曲线", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        "粘贴 AutoEQ 输出的 GraphicEQ 文本，或每行「频率 增益」两列。",
                        fontSize = 9.sp, color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aeqText,
                        onValueChange = { aeqText = it },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        textStyle = TextStyle(fontSize = 10.sp),
                        placeholder = { Text("GraphicEQ:20 -1.5;21 -1.4; …", fontSize = 10.sp) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = AutoEq.parse(aeqText)
                    if (c == null) {
                        status = "导入失败：没解析出频点"
                    } else {
                        aeqCurve = c
                        prefs.edit().putString("aeq", AutoEq.toCsv(c)).apply()
                        eqRaw = rawCurve()
                        eqCurve = finalEq(eqRaw)
                        scheduleEq()
                        status = "已导入 AutoEQ 曲线（127 点）"
                        showAeqDlg = false
                    }
                }) { Text("导入", color = Appearance.tertiary.value, fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showAeqDlg = false }) { Text("取消", fontSize = 12.sp) }
            }
        )
    }

    if (showDebugDlg) {
        AlertDialog(
            onDismissRequest = { showDebugDlg = false },
            title = { Text(if (debugOn) "关闭 DP 接管？" else "用 DP 接管 EQ？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (debugOn) "关闭后会把杜比 GEQ 恢复成你之前那条曲线；同时 DP 侧 EQ 自动写回平直（0 dB），不会留下残余影响。"
                    else "开启后会把杜比 GEQ 的 20 段全部写成平直（0 dB），EQ 改由 DynamicsProcessing 接管。关闭时会恢复原来的杜比曲线，并把 DP 侧归平直。",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showDebugDlg = false; setDebug(!debugOn) }) {
                    Text(if (debugOn) "关闭并恢复" else "开启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDebugDlg = false }) { Text("取消") }
            }
        )
    }

    if (delPeqIdx in peqData.indices) {
        AlertDialog(
            onDismissRequest = { delPeqIdx = -1 },
            title = { Text("删除这个频段？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "将删除 ${fmtFreq(peqData[delPeqIdx].freqHz.toInt())} 这一段，其它频段不受影响。",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val k = delPeqIdx
                    delPeqIdx = -1
                    if (k in peqData.indices) {
                        selectPeq(k)
                        delPeq()
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { delPeqIdx = -1 }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EqTab(
    label: String,
    active: Boolean,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        Modifier.clip(RoundedCornerShape(8.dp)).jellyPress(Appearance.jelly.value > 0).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = if (active) 0.28f else 0.06f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (active) 0.7f else 0.2f))
    ) {
        Text(
            label, fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FxSwitch(title: String, code: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp)
            Text(code, fontSize = 9.sp, color = subInk())
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FxSlider(
    title: String, valueText: String, value: Int, range: IntRange,
    enabled: Boolean,
    fmt: (Int) -> String = { it.toString() },
    onLive: (Int) -> Unit = {}, onDone: (Int) -> Unit
) {
    // 与 GEQ 滑杆同一套做法：拖动期间以本地值为准，不被外部 state 回灌打断
    val en = enabled && LocalDpOn.current
    var local by remember { mutableStateOf(value.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!dragging) local = value.toFloat()
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 12.sp, color = if (en) MaterialTheme.colorScheme.onSurface else subInk(0.8f))
            Text(
                if (dragging) fmt(local.toInt().coerceIn(range.first, range.last)) else valueText,
                fontSize = 12.sp, color = subInk()
            )
        }
        androidx.compose.material3.Slider(
            value = local,
            onValueChange = {
                dragging = true
                local = it
                // 拖动过程中就写 DSP，松手再走完整保存
                onLive(it.toInt().coerceIn(range.first, range.last))
            },
            onValueChangeFinished = {
                dragging = false
                onDone(local.toInt().coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // 范围大时不要生成成千上万个刻度（那会让拖动直接卡死）
            steps = run {
                val span = range.last - range.first
                if (span in 1..120) span - 1 else 0
            },
            enabled = en
        )
    }
}

@Composable
private fun SettingsScreen(activity: Activity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var themeMode by remember { mutableStateOf(ThemePrefs.getMode(ctx)) }
    val prefs = ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
    var bootWrite by remember { mutableStateOf(prefs.getBoolean("boot_writeback", false)) }
    var showBootDialog by remember { mutableStateOf(false) }
    var deviceService by remember { mutableStateOf(prefs.getBoolean("device_service", false)) }
    var hideRecents by remember { mutableStateOf(prefs.getBoolean("hide_recents", false)) }
    var keepAlive by remember { mutableStateOf(prefs.getBoolean("keepalive_job", false)) }
    var callMute by remember { mutableStateOf(prefs.getBoolean("call_mute_dp", false)) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val phonePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) android.widget.Toast.makeText(ctx, "没有电话状态权限，通话暂停不会生效", android.widget.Toast.LENGTH_LONG).show()
    }
    var bootDialogFailed by remember { mutableStateOf(false) }
    fun openAutostart(): Boolean = runCatching {
        ctx.startActivity(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
        )
        true
    }.getOrDefault(false)
    var showLicense by remember { mutableStateOf(false) }
    val licenseText = remember {
        runCatching { ctx.assets.open("license_apache2.txt").bufferedReader().use { it.readText() } }
            .getOrNull().orEmpty()
    }
    var wallpaperUri by remember { mutableStateOf(ThemePrefs.getWallpaperUri(ctx)) }
    var baseReady by remember { mutableStateOf(DspBaseline.captured(ctx)) }
    var baseBusy by remember { mutableStateOf(false) }
    val verName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0.9.3"
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val f = File(ctx.filesDir, "wallpaper_bg")
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            f.outputStream().use { out -> input.copyTo(out) }
                        }
                        f.path
                    }.getOrNull()
                }
                if (ok != null) {
                    ThemePrefs.setWallpaperUri(ctx, ok)
                    wallpaperUri = ok
                    activity.recreate()
                }
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 120.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("设置", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        CapabilitySection()
        Spacer(Modifier.height(10.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text("外观", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SettingRow(
                icon = Icons.Default.LightMode, title = "浅色",
                selected = themeMode == ThemeMode.LIGHT
            ) {
                ThemePrefs.setMode(ctx, ThemeMode.LIGHT); activity.recreate()
            }
            SettingRow(
                icon = Icons.Default.DarkMode, title = "深色",
                selected = themeMode == ThemeMode.DARK
            ) {
                ThemePrefs.setMode(ctx, ThemeMode.DARK); activity.recreate()
            }
            SettingRow(
                icon = Icons.Default.Settings, title = "跟随系统",
                selected = themeMode == ThemeMode.SYSTEM
            ) {
                ThemePrefs.setMode(ctx, ThemeMode.SYSTEM); activity.recreate()
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.RestartAlt, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("开机参数自动写回", fontSize = 13.sp)
                    Text(
                        if (bootWrite) "已开启 · 开机写回杜比参数与音效页"
                        else "关闭时开机不写入任何参数",
                        fontSize = 10.sp, color = subInk()
                    )
                }
                androidx.compose.material3.Switch(
                    checked = bootWrite,
                    onCheckedChange = { on ->
                        bootWrite = on
                        prefs.edit().putBoolean("boot_writeback", on).apply()
                        if (on) {
                            bootDialogFailed = false
                            showBootDialog = true
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Tune, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("后台设备监听", fontSize = 13.sp)
                    Text(
                        if (deviceService) "息屏时也会按绑定预设自动切换" else "只在 APP 打开时检测设备",
                        fontSize = 10.sp, color = subInk()
                    )
                }
                androidx.compose.material3.Switch(
                    checked = deviceService,
                    onCheckedChange = { on ->
                        deviceService = on
                        prefs.edit().putBoolean("device_service", on).apply()
                        if (on) {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            runCatching {
                                ctx.startForegroundService(Intent(ctx, DeviceAutomationService::class.java))
                            }
                        } else {
                            runCatching { ctx.stopService(Intent(ctx, DeviceAutomationService::class.java)) }
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("重新下发监听通知", fontSize = 12.sp)
                    Text(
                        "按当前设备刷新一次常驻通知",
                        fontSize = 9.sp, color = subInk()
                    )
                }
                TextButton(
                    onClick = {
                        if (!deviceService) {
                            android.widget.Toast.makeText(
                                ctx, "请先开启「后台设备监听」", android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@TextButton
                        }
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        runCatching {
                            ctx.startForegroundService(
                                Intent(ctx, DeviceAutomationService::class.java)
                                    .setAction(DeviceAutomationService.ACTION_REFRESH)
                            )
                        }
                        android.widget.Toast.makeText(
                            ctx, "已重新下发监听通知", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh, null,
                        tint = Accent, modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("下发", fontSize = 11.sp, color = Accent)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("后台保活", fontSize = 12.sp)
                    Text(
                        "被系统清掉后由系统定时把服务拉回来",
                        fontSize = 9.sp, color = subInk()
                    )
                }
                androidx.compose.material3.Switch(
                    checked = keepAlive,
                    onCheckedChange = { on ->
                        keepAlive = on
                        prefs.edit().putBoolean("keepalive_job", on).apply()
                        if (on) {
                            KeepAliveJobService.schedule(ctx)
                            runCatching {
                                ctx.startForegroundService(
                                    Intent(ctx, DeviceAutomationService::class.java)
                                )
                            }
                        } else {
                            KeepAliveJobService.cancel(ctx)
                        }
                    }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("通话时暂停音效", fontSize = 12.sp)
                    Text(
                        "来电或通话中自动关掉音效页，挂断后按记录恢复",
                        fontSize = 9.sp, color = subInk()
                    )
                }
                androidx.compose.material3.Switch(
                    checked = callMute,
                    onCheckedChange = { on ->
                        callMute = on
                        prefs.edit().putBoolean("call_mute_dp", on).apply()
                        if (on) {
                            if (Build.VERSION.SDK_INT >= 23) {
                                runCatching { phonePerm.launch(Manifest.permission.READ_PHONE_STATE) }
                            }
                            runCatching {
                                ctx.startForegroundService(
                                    Intent(ctx, DeviceAutomationService::class.java)
                                )
                            }
                        }
                    }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("后台隐藏", fontSize = 12.sp)
                    Text(
                        if (hideRecents) "最近任务里看不到本 APP · 服务照常运行"
                        else "最近任务里会显示本 APP",
                        fontSize = 9.sp, color = subInk()
                    )
                }
                androidx.compose.material3.Switch(
                    checked = hideRecents,
                    onCheckedChange = { on ->
                        hideRecents = on
                        prefs.edit().putBoolean("hide_recents", on).apply()
                        setHideInRecents(activity, on)
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("DSP音效默认值", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (baseReady) "重置基准：已按本机检测（音效参数）"
                else "重置基准：内置默认值（尚未检测本机）",
                fontSize = 10.sp, color = subInk()
            )
            Spacer(Modifier.height(6.dp))
            SettingRow(
                icon = Icons.Default.Refresh,
                title = if (baseReady) "重新检测" else "检测 Dolby DSP音效参数",
                subtitle = if (baseBusy) "检测中…" else "读取当前 DSP音效参数作为重置基准",
                selected = false
            ) {
                if (baseBusy) return@SettingRow
                baseBusy = true
                scope.launch {
                    val err = captureDspBaseline(ctx)
                    baseBusy = false
                    if (err == null) {
                        baseReady = true
                        android.widget.Toast.makeText(
                            ctx, "已记录本机音效默认值", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            ctx, err, android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            Text(
                "检测会读取当前 Dolby DSP 里的音效参数作为基准；GEQ 出厂即平直，不需要检测。",
                fontSize = 9.sp, lineHeight = 14.sp, color = subInk()
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))
        AppearanceSection(
            onPickWallpaper = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearWallpaper = {
                ThemePrefs.setWallpaperUri(ctx, null)
                Appearance.setWallpaperKind(ctx, 2)
                activity.recreate()
            },
            hasLocalWallpaper = ThemePrefs.getWallpaperUri(ctx) != null,
            onWallpaperKind = { k ->
                Appearance.setWallpaperKind(ctx, k)
                if (k == 0 && ThemePrefs.getWallpaperUri(ctx) == null) {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } else {
                    activity.recreate()
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("关于", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AboutRow("开发者", "016. · Aoi")
            AboutRow("机型", realModelName())
            AboutRow("版本", "v$verName")
            SettingRow(
                icon = Icons.Default.Description, title = "开源许可证",
                subtitle = "Apache-2.0", selected = false
            ) { showLicense = true }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Dolby Tuner · 无 root 方案\nGEQ 通过 Dolby DAP 参数 110 实时写入",
            fontSize = 10.sp, color = subInk()
        )
    }

    if (showBootDialog) {
        AlertDialog(
            onDismissRequest = { showBootDialog = false },
            title = { Text("开机参数自动写回", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    if (bootDialogFailed) {
                        Text("无法自动打开自启动设置。", fontSize = 12.sp, color = hintInk(Color(0xFFB45309), Color(0xFFFBBF24)))
                        Spacer(Modifier.height(6.dp))
                    }
                    Text("已开启。请允许 Dolby Tuner 自启动，否则重启后系统不会唤醒 APP 写回参数。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text("路径：设置 → 应用设置 → 授权管理 → 自启动 → 允许 Dolby Tuner", fontSize = 10.sp, color = subInk())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (openAutostart()) showBootDialog = false else bootDialogFailed = true
                }) { Text("去设置", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showBootDialog = false }) { Text("稍后", fontSize = 12.sp) }
            }
        )
    }

    if (showLicense) {
        Dialog(onDismissRequest = { showLicense = false }) {
            LicenseDialogContent(
                title = "开源许可 — Apache-2.0",
                summary = "本工程以 Apache License 2.0 发布。\n" +
                    "可自由使用、修改、分发，须保留版权与许可声明，并附带 NOTICE 文件（如有）。",
                original = licenseText,
                onDismiss = { showLicense = false }
            )
        }
    }
}

@Composable
private fun LicenseDialogContent(
    title: String,
    summary: String,
    original: String,
    onDismiss: () -> Unit
) {
    GlassSurface(
        Modifier.fillMaxHeight(0.9f).fillMaxWidth(),
        radius = Appearance.radius.value
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title, modifier = Modifier.weight(1f),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Accent
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "返回", modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                Text(summary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    "许可证原文", fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = subInk()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    original.ifBlank { "未找到许可证原文。" },
                    fontSize = 9.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("返回", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label, fontSize = 11.sp,
            color = subInk(),
            modifier = Modifier.width(72.dp)
        )
        Text(value, fontSize = 12.sp)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .jellyPress(Appearance.jelly.value > 0).clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, fontSize = 10.sp, color = subInk())
            }
        }
        if (selected) {
            Icon(Icons.Default.Check, null, tint = Accent, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * 只读状态徽章：外形是按钮，但不可点击。
 * 绿 = 杜比 DAP 生效中；红 = 杜比未生效；灰 = 无法判断（本机无 DAP）。
 */
@Composable
private fun DolbyBadge(on: Boolean?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val color = when (on) {
        true -> Color(0xFF22C55E)
        false -> Color(0xFFEF4444)
        null -> subInk(0.8f)
    }
    val label = when (on) {
        true -> "生效中"
        false -> "未生效"
        null -> "未知"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text("Dolby", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, color = color.copy(alpha = 0.85f))
    }
}