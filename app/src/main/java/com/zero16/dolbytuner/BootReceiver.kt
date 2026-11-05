package com.zero16.dolbytuner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机写回：系统启动后，如果用户在设置里打开了「开机参数自动写回」，
 * 就把本地记录的 GEQ + 音效参数重新下发到 Dolby DSP，并 commit 保存。
 *
 * 需要 RECEIVE_BOOT_COMPLETED 权限，且系统里允许本应用自启动。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val sp = context.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)

        // 后台设备监听开着就恢复服务（息屏/重启后仍能自动切预设）
        if (sp.getBoolean("device_service", false)) {
            runCatching {
                context.startForegroundService(Intent(context, DeviceAutomationService::class.java))
            }
        }

        if (!sp.getBoolean("boot_writeback", false)) return

        // 音效页（DP）要写回必须借服务进程重建：DP 是进程内 AudioEffect
        if (DpRestore.isOn(context)) {
            runCatching {
                context.startForegroundService(
                    Intent(context, DeviceAutomationService::class.java)
                        .setAction(DeviceAutomationService.ACTION_RESTORE)
                )
            }
        }

        val pending = goAsync()
        Thread {
            try {
                val geq = sp.getString("geq", null)
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.toIntArray()
                if (geq != null && geq.size == DapRealtime.BANDS) {
                    DapRealtime.commit(geq)
                }

                val fx = sp.getString("fx", null)?.split(",")
                if (fx != null) {
                    fun bit(i: Int) = if (fx.getOrNull(i) == "1") 1 else 0
                    fun num(i: Int, def: Int) = fx.getOrNull(i)?.toIntOrNull() ?: def

                    DapRealtime.setParamValue(101, bit(0))
                    DapRealtime.setParamValue(102, bit(1))
                    DapRealtime.setParamValue(105, bit(6))
                    DapRealtime.setParamValue(108, num(7, 4))
                    DapRealtime.setParamValue(111, bit(2))
                    DapRealtime.setParamValue(233, num(3, 80))
                    DapRealtime.setParamValue(234, num(4, 150))
                    DapRealtime.setParamValue(235, num(5, 16))
                }
            } catch (_: Throwable) {
                // 开机阶段 Dolby 可能还没就绪，静默失败，下次重启再试
            } finally {
                runCatching { pending.finish() }
            }
        }.start()
    }
}