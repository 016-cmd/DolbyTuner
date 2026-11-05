package com.zero16.dolbytuner

import androidx.compose.runtime.mutableStateOf

/**
 * 通话暂停状态。
 *
 * 服务里检测到通话（普通电话 / 微信语音·视频等 VoIP）时把 audioMuted 置 true，
 * 音效页的总开关据此显示为「关」；挂断后恢复。这里只表示「临时暂停」，
 * 不碰本地记录里的 on，通话结束照原记录恢复。
 */
object CallGuard {
    val muted = mutableStateOf(false)
}