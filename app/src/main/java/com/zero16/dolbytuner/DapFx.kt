package com.zero16.dolbytuner

import android.content.Context

/**
 * Dolby DAP 扩展参数（A 类音效）。
 *
 * 与现有 FxState（PARA 101/102/105/108/111/233/234/235）完全分开：
 * 这里全部走 FourCC 通道（dplp / beon / dvle / ieon / …），不会互相覆盖。
 */
internal data class DapFx(
    // A1 Peak Limiter
    val limiterOn: Boolean = false,
    val dplpDb: Float = -0.52f,
    val pregOn: Boolean = true,
    val preoDb: Int = 0,
    val vmbDb: Int = 0,

    // A2 虚拟低音（默认值取自本机官方 tunings：overall_gain 35、mix 30–150Hz）
    val vbOn: Boolean = false,
    val vbStrength: Int = 35,
    val vbLowHz: Int = 30,
    val vbHighHz: Int = 150,
    // A4 环绕（默认值取自本机官方 profile_endpoints：speaker surround_boost 105）
    val surroundOn: Boolean = false,
    val surroundBoost: Int = 105,
    // A5 IEQ（默认值取自本机官方 profile_endpoints：speaker ieq_amount 12、preset 0）
    val ieqOn: Boolean = false,
    val ieqAmount: Int = 12,
    val ieqPreset: Int = 0,

    // A7 MBDRC
    val mbdrcOn: Boolean = false,
    val mbdrcProfile: Int = 0,
    val mbdrcAttackMs: Int = 50,
    val mbdrcReleaseMs: Int = 60,
    val mbdrcOffsets: List<Int> = List(8) { 0 },

    // A8 后级增益
    val postGainOn: Boolean = false,
    val postGainDb: Int = 0
) {
    /** 存成一行，方便塞进 SharedPreferences（和现有 fx 键同风格） */
    fun toPref(): String = listOf(
        if (limiterOn) 1 else 0,
        (dplpDb * 100f).toInt(),
        if (pregOn) 1 else 0,
        preoDb,
        vmbDb,
        if (vbOn) 1 else 0,
        vbStrength,
        vbLowHz,
        vbHighHz,
        if (surroundOn) 1 else 0,
        surroundBoost,
        if (ieqOn) 1 else 0,
        ieqAmount,
        ieqPreset,
        if (mbdrcOn) 1 else 0,
        mbdrcProfile,
        mbdrcAttackMs,
        mbdrcReleaseMs,
        mbdrcOffsets.joinToString(":") { it.toString() },
        if (postGainOn) 1 else 0,
        postGainDb
    ).joinToString(",")

    companion object {
        fun fromPref(raw: String?): DapFx {
            if (raw.isNullOrBlank()) return DapFx()
            val a = raw.split(",")
            fun i(idx: Int, def: Int) = a.getOrNull(idx)?.toIntOrNull() ?: def
            fun b(idx: Int, def: Boolean) = when (a.getOrNull(idx)) {
                "1" -> true
                "0" -> false
                else -> def
            }
            val offsets = a.getOrNull(18)
                ?.split(":")
                ?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 8 }
                ?: List(8) { 0 }
            return DapFx(
                limiterOn = b(0, false),
                dplpDb = i(1, -52) / 100f,
                pregOn = b(2, true),
                preoDb = i(3, 0),
                vmbDb = i(4, 0),
                vbOn = b(5, false),
                vbStrength = i(6, 64),
                vbLowHz = i(7, 40),
                vbHighHz = i(8, 160),
                surroundOn = b(9, false),
                surroundBoost = i(10, 64),
                ieqOn = b(11, false),
                ieqAmount = i(12, 10),
                ieqPreset = i(13, 2),
                mbdrcOn = b(14, false),
                mbdrcProfile = i(15, 0),
                mbdrcAttackMs = i(16, 50),
                mbdrcReleaseMs = i(17, 60),
                mbdrcOffsets = offsets,
                postGainOn = b(19, false),
                postGainDb = i(20, 0)
            )
        }
    }
}

/**
 * 从 DSP 读回 A 类参数。
 *
 * 每一项单独读：读不到就保留本地记录里的值（不硬写默认值）。
 * @return null = 一项都没读到（说明本机不支持这条读回通道）
 */
internal fun readDapFxFromDsp(local: DapFx): Pair<DapFx, Int>? {
    var d = local
    var hit = 0
    fun rd(tag: String, n: Int = 1): IntArray? = DapRealtime.readFourCC(tag, n)

    rd("dplp")?.firstOrNull()?.let { lin ->
        if (lin in 1..256) {
            // dplp 是线性增益 ×256，换回 dBFS
            val db = (20.0 * Math.log10(lin / 256.0)).toFloat().coerceIn(-12f, 0f)
            d = d.copy(limiterOn = lin < 256, dplpDb = db)
            hit++
        }
    }
    rd("preg")?.firstOrNull()?.let { d = d.copy(pregOn = it != 0); hit++ }
    rd("preo")?.firstOrNull()?.let { d = d.copy(preoDb = (it / 16).coerceIn(0, 12)); hit++ }
    rd("vmb ")?.firstOrNull()?.let { d = d.copy(vmbDb = (it / 16).coerceIn(0, 6)); hit++ }

    rd("dvle")?.firstOrNull()?.let { d = d.copy(surroundOn = it != 0); hit++ }
    rd("dvla")?.firstOrNull()?.let { d = d.copy(surroundBoost = it.coerceIn(0, 192)); hit++ }

    rd("beon")?.firstOrNull()?.let { d = d.copy(vbOn = it != 0); hit++ }
    rd("beb")?.firstOrNull()?.let { d = d.copy(vbStrength = it.coerceIn(0, 192)); hit++ }
    rd("becf", 2)?.let { a ->
        if (a.size >= 2) {
            d = d.copy(vbHighHz = a[0].coerceIn(60, 240), vbLowHz = a[1].coerceIn(20, 160))
            hit++
        }
    }

    rd("ieon")?.firstOrNull()?.let { d = d.copy(ieqOn = it != 0); hit++ }
    rd("iea")?.firstOrNull()?.let { d = d.copy(ieqAmount = it.coerceIn(0, 16)); hit++ }
    rd("ieid")?.firstOrNull()?.let { d = d.copy(ieqPreset = it.coerceIn(0, 10)); hit++ }

    rd("vol")?.firstOrNull()?.let { v ->
        d = d.copy(postGainOn = v != 0, postGainDb = v.coerceIn(-12, 12))
        hit++
    }

    return if (hit == 0) null else d to hit
}

internal object DapFxStore {
    private const val PREF = "dolby_tuner"
    private const val KEY = "dap_fx"

    fun load(ctx: Context): DapFx =
        DapFx.fromPref(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(ctx: Context, fx: DapFx) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, fx.toPref())
            .apply()
    }
}