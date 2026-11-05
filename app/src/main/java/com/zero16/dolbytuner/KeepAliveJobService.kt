package com.zero16.dolbytuner

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * JobScheduler 兜底：前台服务被系统清掉之后，由系统按周期把服务重新拉起来。
 *
 * 只做「拉起」一件事，不在这里干活；真正的恢复在 DeviceAutomationService 里。
 * 开关在设置 → 后台设备监听卡内。
 */
class KeepAliveJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val sp = getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
        if (sp.getBoolean("keepalive_job", false)) {
            runCatching {
                startForegroundService(Intent(this, DeviceAutomationService::class.java))
            }
        }
        // 周期任务：告诉系统这次干完了，等下一个周期
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val JOB_ID = 4721

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val info = JobInfo.Builder(
                JOB_ID,
                ComponentName(ctx, KeepAliveJobService::class.java)
            )
                .setPeriodic(15 * 60 * 1000L)          // 系统允许的最小周期
                .setPersisted(true)                     // 重启后仍然有效
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setRequiresDeviceIdle(false)
                .build()
            runCatching { js.schedule(info) }
        }

        fun cancel(ctx: Context) {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            runCatching { js.cancel(JOB_ID) }
        }
    }
}