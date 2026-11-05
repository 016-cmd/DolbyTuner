package com.zero16.dolbytuner

import com.kyant.backdrop.Backdrop

/**
 * 全局「壁纸层」backdrop。
 *
 * 只有一个 layerBackdrop（在 Theme 里包住壁纸那张图），
 * 所有卡片共用它做 drawBackdrop 采样 —— 不是每张卡各抓一次背景，
 * 这样才不会踩 Backdrop 的 RenderThread 递归崩溃。
 */
object GlassBackdrop {
    @Volatile
    var instance: Backdrop? = null
}
