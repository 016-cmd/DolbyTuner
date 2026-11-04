package com.zero16.dolbytuner

/**
 * 本机「官方」A 类基线参数。
 *
 * 来源：/data/vendor/dolby/dax_sqlite3.db
 *   · profile_endpoints  → profile 4 的 speaker / headphone 行
 *   · tunings            → speaker 的 speaker_landscape_large 行
 * 提取时间：2026-11-04（卸载音质模块之前抓的副本 _dax/dax_now3.db）
 *
 * 用途：
 *   ① A 类音效页的默认值
 *   ② 「重置」时直接写这套写死的值，不再依赖 DSP 读回
 *
 * 注意：这些是「基线」，不是「开启时的推荐值」；
 * 功能默认全部关闭，只有用户打开滑杆时才会用到这些数字。
 */
internal object DapOfficial {

    // ── profile_endpoints（profile 4）────────────────────────
    const val PEAK_SPEAKER = 1024
    const val PEAK_HEADPHONE = 256
    const val SURROUND_SPEAKER = 105
    const val SURROUND_HEADPHONE = 72
    const val IEQ_AMOUNT_SPEAKER = 12
    const val IEQ_AMOUNT_HEADPHONE = 6
    const val IEQ_PRESET_SPEAKER = 0
    const val DIALOG_AMOUNT_SPEAKER = 5
    const val DIALOG_AMOUNT_HEADPHONE = 4
    const val VOLMAX_SPEAKER = 62
    const val VOLMAX_HEADPHONE = 32

    // ── tunings（speaker / speaker_landscape_large）──────────
    const val BASS_BOOST = 50
    const val BASS_CUTOFF = 145
    const val BASS_WIDTH = 16
    const val VB_OVERALL_GAIN = 35
    const val VB_MIX_LOW = 30
    const val VB_MIX_HIGH = 150
    const val VB_SRC_LOW = 35
    const val VB_SRC_HIGH = 140

    /**
     * A 类出厂默认：功能全关，参数取上面这套官方基线。
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
        vbLowHz = VB_MIX_LOW,
        vbHighHz = VB_MIX_HIGH,
        surroundOn = false,
        surroundBoost = SURROUND_SPEAKER,
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
