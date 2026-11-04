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

    // A2 虚拟低音（官方 tunings：overall_gain 0、src 35–160Hz、mix 94–469Hz）
    val vbOn: Boolean = false,
    val vbStrength: Int = 0,
    val vbLowHz: Int = 35,
    val vbHighHz: Int = 160,
    // A4 环绕（官方 profile_endpoints：speaker surround_boost 0）
    val surroundOn: Boolean = false,
    val surroundBoost: Int = 0,
    // A5 IEQ（官方 profile_endpoints：speaker ieq_amount 9、preset 1）
    val ieqOn: Boolean = false,
    val ieqAmount: Int = 9,
    val ieqPreset: Int = 1,

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