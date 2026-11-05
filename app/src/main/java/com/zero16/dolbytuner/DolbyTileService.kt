package com.zero16.dolbytuner

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * 控制中心磁贴：一键开关音效页。
 *
 * 磁贴和 APP 同进程，所以 DynProc 与界面共享同一份状态；
 * 开关只改 dynproc.on，真正的下发走 DpRestore（和开机写回同一套）。
 */
class DolbyTileService : TileService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val p = getSharedPreferences("dynproc", Context.MODE_PRIVATE)
        val target = !p.getBoolean("on", false)
        p.edit().putBoolean("on", target).apply()

        Thread {
            if (target) DpRestore.apply(this) else runCatching { DynProc.stop() }
            main.post { refresh() }
        }.start()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        runCatching {
            val p = getSharedPreferences("dynproc", Context.MODE_PRIVATE)
            val muted = CallGuard.muted.value
            val on = p.getBoolean("on", false) && !muted
            tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "Dolby音效"
            tile.subtitle = when {
                muted -> "通话中已暂停"
                on -> "已开启"
                else -> "已关闭"
            }
            tile.icon = Icon.createWithResource(this, R.drawable.tile_dolby)
            tile.updateTile()
        }
    }
}