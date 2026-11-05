package com.zero16.dolbytuner

import android.content.Context

/**
 * 从本地记录（dynproc 偏好）把音效页的完整状态重建到 DSP。
 *
 * 三处复用：开机写回、服务常驻恢复、通话结束恢复。
 * 目的只有一个 —— 记录里是什么，写进 DSP 的就是什么，不多不少。
 */
object DpRestore {

    /** 本地记录里的限幅器配置（与设置页 UI 的默认值一致） */
    fun limiterOf(p: android.content.SharedPreferences): DynProc.LimiterCfg =
        DynProc.LimiterCfg(
            enabled = p.getBoolean("lim_on", true),
            thresholdDb = p.getInt("lim_thr", -20) / 10f,
            attackMs = p.getInt("lim_atk", 10) / 10f,
            releaseMs = p.getInt("lim_rel", 60).toFloat(),
            ratio = p.getInt("lim_rat", 100) / 10f,
            postGainDb = p.getInt("lim_post", 0) / 10f,
            linkGroup = p.getInt("lim_link", 0)
        )

    /** 音效页总开关是否记录为开 */
    fun isOn(ctx: Context): Boolean =
        ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE).getBoolean("on", false)

    /** 把记录里的参数整套写进 DSP；总开关关着就顺手把引擎停掉 */
    fun apply(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE)
        if (!p.getBoolean("on", false)) {
            runCatching { DynProc.stop() }
            return false
        }
        return runCatching {
            val d = loadDpSnapshot(ctx)
            val auto = p.getBoolean("auto_gain", false)
            val g = if (auto) autoGainOf(d.curve) else d.gain / 10f
            val mbc = applyDpMacros(
                dpMbc(d.mbc, d.mbcOn),
                p.getBoolean("bb_on", false), p.getInt("bb_amt", 60),
                p.getBoolean("ds_on", false), p.getInt("ds_amt", 40)
            )
            DynProc.start(g, limiterOf(p), mbc)
            DynProc.setLimiter(limiterOf(p))
            DynProc.setOutGain(g)
            DynProc.setPreEq(d.curve)
            DynProc.setMbc(mbc)
            if (d.postOn) DynProc.setPostEq(d.post, true)
            else DynProc.setPostEq(IntArray(10), false)
            true
        }.getOrDefault(false)
    }
}
