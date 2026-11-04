package com.zero16.dolbytuner

import android.media.audiofx.DynamicsProcessing
import android.util.Log

/**
 * Android 原生 DynamicsProcessing 引擎封装（P0：总开关 + 输入增益 + Limiter）
 *
 * 逆向参考：SonicKeyframe 的 K5 / J5
 *   - priority = Integer.MAX_VALUE, session = 0
 *   - Config: variant=0, ch=2, preEq=true(127), mbc=true(4), postEq=true(10), limiter=true
 *   - setPreferredFrameDuration(80f)
 *   - band 数按 127 → 64 → 32 → 20 依次降级
 *
 * 参数一旦被写坏/实例被抢，就 stop() 再 start()，不做部分热更新（更稳）。
 */
object DynProc {

    private const val TAG = "DynProc"

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var activeBands: Int = 0
        private set

    @Volatile
    var status: String = "未启动"
        private set

    private var dp: DynamicsProcessing? = null

    /** 上一次成功下发的配置（用于重建） */
    private var lastGain: Float = 0f
    private var lastLimiter: LimiterCfg = LimiterCfg()
    private var lastMbc: List<MbcBandCfg> = defaultMbc()
    private var lastPost: IntArray? = null

    /** 后置均衡 10 段中心频率 */
    val POST_FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    data class LimiterCfg(
        var enabled: Boolean = true,
        var thresholdDb: Float = -2.0f,
        var attackMs: Float = 1.0f,
        var releaseMs: Float = 60f,
        var ratio: Float = 10f,
        var postGainDb: Float = 0f,
        var linkGroup: Int = 0
    )

    /** MBC 单段（分频点固定，参数可调） */
    data class MbcBandCfg(
        var enabled: Boolean = false,
        var thresholdDb: Float = -20f,
        var ratio: Float = 3f,
        var attackMs: Float = 1f,
        var releaseMs: Float = 80f,
        var kneeWidth: Float = 60f,
        var noiseGateDb: Float = -90f,
        var expanderRatio: Float = 1f,
        var preGainDb: Float = 0f,
        var postGainDb: Float = 0f
    )

    /** 四段默认（照 SonicKeyframe 的出厂值，全部关闭） */
    fun defaultMbc(): List<MbcBandCfg> = listOf(
        MbcBandCfg(), MbcBandCfg(), MbcBandCfg(), MbcBandCfg()
    )

    /** 启动（可重复调用，内部会先释放旧实例） */
    @Synchronized
    fun start(
        gainDb: Float,
        limiter: LimiterCfg,
        mbc: List<MbcBandCfg>? = null
    ): Boolean {
        stop()
        lastGain = gainDb
        lastLimiter = limiter
        if (mbc != null) lastMbc = mbc

        val candidates = intArrayOf(127, 64, 32, 20)
        for (bands in candidates) {
            try {
                val cfg = DynamicsProcessing.Config.Builder(
                    0,                      // VARIANT_FAVOR_FREQUENCY_RESOLUTION
                    2,                      // 立体声
                    true, bands,            // preEq 段数（自适应）
                    true, 4,                // MBC 4 段
                    true, 10,               // postEq 10 段
                    true                    // limiter
                ).setPreferredFrameDuration(80.0f).build()

                val d = DynamicsProcessing(Int.MAX_VALUE, 0, cfg)

                // 输入增益
                runCatching { d.setInputGainAllChannelsTo(gainDb) }
                    .onFailure {
                        runCatching {
                            d.setInputGainbyChannel(0, gainDb)
                            d.setInputGainbyChannel(1, gainDb)
                        }
                    }

                // 限幅器
                applyLimiterTo(d, limiter)

                // 多段压缩器
                applyMbcAll(d, lastMbc)

                // 控制权 / 启用状态监听：被系统或别的 App 关掉时标记失联
                runCatching {
                    d.setEnableStatusListener { _, enabled ->
                        if (!enabled && running) status = "被系统关闭 · 请重新开启"
                    }
                    d.setControlStatusListener { _, granted ->
                        if (!granted && running) status = "被其他应用占用 · 未写入"
                    }
                }

                d.setEnabled(true)

                dp = d
                running = true
                activeBands = bands
                status = "已启动 · ${bands}段处理"
                Log.i(TAG, "DP started, bands=$bands")
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "DP init failed with bands=$bands: ${t.message}")
            }
        }

        status = "启动失败 · 设备不支持此功能"
        running = false
        activeBands = 0
        return false
    }

    private fun applyLimiterTo(d: DynamicsProcessing, c: LimiterCfg) {
        if (!c.enabled) {
            runCatching { d.setLimiterAllChannelsTo(flatLimiter()) }
            return
        }
        val lim = DynamicsProcessing.Limiter(
            true,                                   // inUse
            true,                                   // enabled
            c.linkGroup.coerceIn(0, 3),
            c.attackMs.coerceIn(0.1f, 100f),
            c.releaseMs.coerceIn(1f, 5000f),
            c.ratio.coerceIn(1f, 30f),
            c.thresholdDb.coerceIn(-60f, 0f),
            c.postGainDb.coerceIn(-40f, 40f)
        )
        val ok = runCatching { d.setLimiterAllChannelsTo(lim) }.isSuccess
        if (!ok) {
            runCatching {
                d.setLimiterByChannelIndex(0, lim)
                d.setLimiterByChannelIndex(1, lim)
            }
        }
    }

    private val MBC_CUTOFFS = floatArrayOf(250f, 2000f, 8000f, 20000f)

    private fun applyMbcBand(d: DynamicsProcessing, i: Int, c: MbcBandCfg) {
        runCatching {
            val band = DynamicsProcessing.MbcBand(
                c.enabled,
                MBC_CUTOFFS[i],
                c.attackMs.coerceIn(0.1f, 100f),
                c.releaseMs.coerceIn(1f, 5000f),
                c.ratio.coerceIn(1f, 30f),
                c.thresholdDb.coerceIn(-80f, 0f),
                c.kneeWidth.coerceIn(0f, 60f),
                c.noiseGateDb.coerceIn(-90f, 0f),
                c.expanderRatio.coerceIn(1f, 10f),
                c.preGainDb.coerceIn(-40f, 40f),
                c.postGainDb.coerceIn(-40f, 40f)
            )
            val ok = runCatching { d.setMbcBandAllChannelsTo(i, band) }.isSuccess
            if (!ok) {
                runCatching {
                    d.setMbcBandByChannelIndex(0, i, band)
                    d.setMbcBandByChannelIndex(1, i, band)
                }
            }
        }
    }

    private fun applyMbcAll(d: DynamicsProcessing, bands: List<MbcBandCfg>) {
        for (i in 0 until 4) applyMbcBand(d, i, bands.getOrNull(i) ?: MbcBandCfg())
    }

    /** 运行时改 MBC */
    @Synchronized
    fun setMbc(bands: List<MbcBandCfg>) {
        val prev = lastMbc
        lastMbc = bands
        val d = dp ?: return
        if (!runCatching { d.hasControl() }.getOrDefault(false)) {
            status = "被其他应用占用 · 未写入"
            return
        }
        // 只写变化段
        for (i in 0 until 4) {
            val cur = bands.getOrNull(i) ?: continue
            if (prev.getOrNull(i) != cur) applyMbcBand(d, i, cur)
        }
    }

    /** data = [10 段增益 x0.1dB, 左右平衡 x0.1dB] */
    private fun applyPostEqTo(d: DynamicsProcessing, data: IntArray) {
        val eqL = DynamicsProcessing.Eq(true, true, 10)
        val eqR = DynamicsProcessing.Eq(true, true, 10)
        val bal = (data.getOrNull(10) ?: 0) / 10f
        for (i in 0 until 10) {
            val base = (data.getOrNull(i) ?: 0) / 10f
            val l = (base + bal).coerceIn(-20f, 20f)
            val r = (base - bal).coerceIn(-20f, 20f)
            eqL.setBand(i, DynamicsProcessing.EqBand(true, POST_FREQS[i], l))
            eqR.setBand(i, DynamicsProcessing.EqBand(true, POST_FREQS[i], r))
        }
        runCatching { d.setPostEqByChannelIndex(0, eqL) }
        runCatching { d.setPostEqByChannelIndex(1, eqR) }
    }

    @Synchronized
    fun setPostEq(data: IntArray, enabled: Boolean) {
        val prev = lastPost
        lastPost = data.copyOf()
        val d = dp ?: return
        if (!runCatching { d.hasControl() }.getOrDefault(false)) {
            status = "被其他应用占用 · 未写入"
            return
        }
        if (!enabled) {
            val eq = DynamicsProcessing.Eq(true, false, 10)
            for (i in 0 until 10) {
                eq.setBand(i, DynamicsProcessing.EqBand(false, POST_FREQS[i], 0f))
            }
            runCatching { d.setPostEqAllChannelsTo(eq) }
            return
        }
        if (prev != null && prev.contentEquals(data)) return
        applyPostEqTo(d, data)
    }

    fun lastPostData(): IntArray? = lastPost?.copyOf()

    private fun flatLimiter() = DynamicsProcessing.Limiter(
        false, false, 0, 1f, 60f, 1f, 0f, 0f
    )

    /** 运行时改输入增益（实例不在就只记下来） */
    @Synchronized
    fun setInputGain(gainDb: Float) {
        lastGain = gainDb
        val d = dp ?: return
        runCatching { d.setInputGainAllChannelsTo(gainDb) }
            .onFailure {
                runCatching {
                    d.setInputGainbyChannel(0, gainDb)
                    d.setInputGainbyChannel(1, gainDb)
                }.onFailure { Log.w(TAG, "setInputGain failed") }
            }
    }

    /** 运行时改 Limiter */
    @Synchronized
    fun setLimiter(c: LimiterCfg) {
        if (c == lastLimiter) return
        lastLimiter = c
        val d = dp ?: return
        if (!runCatching { d.hasControl() }.getOrDefault(false)) {
            status = "被其他应用占用 · 未写入"
            return
        }
        applyLimiterTo(d, c)
    }

    /** 重新按上次配置起一次（用于"被抢走"后手动恢复） */
    @Synchronized
    fun restart(): Boolean = start(lastGain, lastLimiter, lastMbc)

    @Synchronized
    fun stop() {
        val d = dp
        dp = null
        running = false
        activeBands = 0
        status = "已关闭"
        if (d != null) {
            runCatching { d.setEnabled(false) }
            runCatching { d.release() }
        }
        Log.i(TAG, "DP stopped")
    }

    fun currentGain(): Float = lastGain
    fun currentLimiter(): LimiterCfg = lastLimiter
}
