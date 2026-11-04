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

    data class LimiterCfg(
        var enabled: Boolean = true,
        var thresholdDb: Float = -2.0f,
        var attackMs: Float = 1.0f,
        var releaseMs: Float = 60f,
        var ratio: Float = 10f,
        var postGainDb: Float = 0f,
        var linkGroup: Int = 0
    )

    /** 启动（可重复调用，内部会先释放旧实例） */
    @Synchronized
    fun start(gainDb: Float, limiter: LimiterCfg): Boolean {
        stop()
        lastGain = gainDb
        lastLimiter = limiter

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

                // 控制权 / 启用状态监听：被系统或别的 App 关掉时标记失联
                runCatching {
                    d.setEnableStatusListener { _, enabled ->
                        if (!enabled && running) status = "被系统关闭 · 需重启"
                    }
                    d.setControlStatusListener { _, granted ->
                        if (!granted && running) status = "控制权丢失 · 需重启"
                    }
                }

                d.setEnabled(true)

                dp = d
                running = true
                activeBands = bands
                status = "已启动 · ${bands}段 · session 0"
                Log.i(TAG, "DP started, bands=$bands")
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "DP init failed with bands=$bands: ${t.message}")
            }
        }

        status = "启动失败 · HAL 拒绝所有段数"
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
        lastLimiter = c
        val d = dp ?: return
        if (!runCatching { d.hasControl() }.getOrDefault(false)) {
            status = "无控制权 · 写入被忽略"
            return
        }
        applyLimiterTo(d, c)
    }

    /** 重新按上次配置起一次（用于"被抢走"后手动恢复） */
    @Synchronized
    fun restart(): Boolean = start(lastGain, lastLimiter)

    @Synchronized
    fun stop() {
        val d = dp
        dp = null
        running = false
        activeBands = 0
        status = "已关闭 · 管线休眠"
        if (d != null) {
            runCatching { d.setEnabled(false) }
            runCatching { d.release() }
        }
        Log.i(TAG, "DP stopped")
    }

    fun currentGain(): Float = lastGain
    fun currentLimiter(): LimiterCfg = lastLimiter
}
