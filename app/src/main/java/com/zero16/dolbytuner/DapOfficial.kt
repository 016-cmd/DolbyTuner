package com.zero16.dolbytuner

/**
 * 本机「官方」A 类基线参数（卸载音质模块后重新提取）。
 *
 * 来源：/data/vendor/dolby/dax_sqlite3.db（2026-11-04 11:06 模块卸载后系统重建的库）
 *   副本：_dax/dax_official.db（94,208 字节，MD5 718e75441030d30151f4e57dbc25e38a）
 *   · profile_endpoints → profile 4 · speaker / headphone（custom_name=default）
 *   · tunings           → endpoint=speaker 的五条 tuning（值完全一致）
 *
 * 用途：
 *   ① A 类音效页的默认值
 *   ② 「重置」时直接写这套写死的值，不再依赖 DSP 读回
 *
 * 模块版和官方版差别不小（模块把低音推得很猛），这里全部按官方值来：
 *   低音增强 boost 192 / 截止 200 / 带宽 16
 *   虚拟低音 overall_gain 0 / mix 94–469 / src 35–160
 *   环绕 boost 0、IEQ amount 9 / preset 1、peak 180、volmax 0
 */
internal object DapOfficial {

    // ── profile_endpoints（profile 4 · speaker）──────────────
    const val PEAK_SPEAKER = 180
    const val SURROUND_SPEAKER = 0
    const val IEQ_AMOUNT_SPEAKER = 9
    const val IEQ_PRESET_SPEAKER = 1
    const val DIALOG_AMOUNT_SPEAKER = 4
    const val VOLMAX_SPEAKER = 0

    // ── profile_endpoints（profile 4 · headphone）────────────
    const val PEAK_HEADPHONE = 256
    const val SURROUND_HEADPHONE = 0
    const val IEQ_AMOUNT_HEADPHONE = 6
    const val IEQ_PRESET_HEADPHONE = 2
    const val DIALOG_AMOUNT_HEADPHONE = 4
    const val VOLMAX_HEADPHONE = 32

    // ── tunings（speaker，五条 tuning 一致）──────────────────
    const val BASS_BOOST = 192
    const val BASS_CUTOFF = 200
    const val BASS_WIDTH = 16
    // DSP 侧：64 = 0 dB（线性 Q6）
    const val VB_OVERALL_GAIN = 64
    const val VB_MIX_LOW = 94
    const val VB_MIX_HIGH = 469
    const val VB_SRC_LOW = 35
    const val VB_SRC_HIGH = 160
    // 官方 harmgains 后 8 位全是 48（48/64 ≈ -2.5 dB）
    const val VB_HARM = 48
    // 逆向写死的环绕前后级
    const val VIR_PRE = -256
    const val VIR_POST = -256

    /**
     * A 类出厂默认：功能全关，参数取官方基线。
     * 「重置」就用它，写死，不读 DSP、不读本地记录。
     */
    val DEFAULT = DapFx(
        limiterOn = false,
        dplpDb = -0.5f,
        pregOn = true,
        preoDb = 0,
        vmbDb = 0,
        vbOn = false,
        vbStrength = VB_OVERALL_GAIN,
        vbLowHz = VB_SRC_LOW,
        vbHighHz = VB_SRC_HIGH,
        vbMix = VB_OVERALL_GAIN,
        vbH2 = VB_HARM,
        vbH3 = VB_HARM,
        vbH4 = VB_HARM,
        vbH5 = VB_HARM,
        surroundOn = false,
        surroundBoost = SURROUND_SPEAKER,
        dvli = VIR_PRE,
        dvlo = VIR_POST,
        ieqOn = false,
        ieqAmount = IEQ_AMOUNT_SPEAKER,
        ieqPreset = IEQ_PRESET_SPEAKER,
        mbdrcOn = false,
        mbdrcProfile = 0,
        mbdrcAttackMs = 50,
        mbdrcReleaseMs = 60,
        mbdrcOffsets = List(8) { 0 },
        postGainOn = false,
        postGainDb = 0
    )
}