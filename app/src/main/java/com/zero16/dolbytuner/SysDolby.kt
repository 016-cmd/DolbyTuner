package com.zero16.dolbytuner

/**
 * 读取「系统 Dolby」自己的持久化配置（persist.vendor.dolby.*）。
 *
 * 为什么用属性而不是 FourCC 读回：
 *   FourCC 通道只写不读（实测 rc=-4/-7，回包全 0）；
 *   而 Dolby 服务会把当前配置写进 persist 属性，getprop 就能读到，
 *   不需要 root、不需要 Shizuku。
 *
 * 注意：这是「系统 Dolby 的配置值」，不等于我们刚写进 DSP 内存的实时值。
 */
internal object SysDolby {

    data class State(
        val globalOn: Boolean? = null,
        val profile: Int? = null,
        val bassOn: Boolean? = null,
        val bassBoost: Int? = null,
        val dialogOn: Boolean? = null,
        val dialogAmount: Int? = null,
        val virtualizerOn: Boolean? = null,
        val virtualizerAmount: Int? = null,
        val ieqOn: Boolean? = null,
        val spectralOn: Boolean? = null,
        val levelerOn: Boolean? = null,
        val deviceMap: String? = null
    ) {
        /** 一行摘要，给音效页标题下面那行小字用 */
        fun summary(): String {
            val p = ArrayList<String>(6)
            bassOn?.let { p += "低音" + if (it) "开" else "关" }
            dialogOn?.let { p += "人声" + if (it) "开" else "关" }
            virtualizerOn?.let { p += "环绕" + if (it) "开" else "关" }
            ieqOn?.let { p += "IEQ " + if (it) "开" else "关" }
            levelerOn?.let { p += "均衡" + if (it) "开" else "关" }
            profile?.let { p += "profile $it" }
            return if (p.isEmpty()) "（未读到系统属性）" else p.joinToString(" · ")
        }
    }

    /** 每次调用都重新读一遍，切页面回来拿到的就是最新值 */
    fun read(): State? {
        val props = readProps() ?: return null
        fun b(key: String): Boolean? = props[key]?.let { it == "1" || it == "true" }
        fun i(key: String): Int? = props[key]?.toIntOrNull()
        return State(
            globalOn = b("persist.vendor.dolby.global.enable"),
            profile = i("persist.audio.dolby.profile"),
            bassOn = b("persist.vendor.dolby.bass.enable"),
            bassBoost = i("persist.vendor.dolby.bass.boost"),
            dialogOn = b("persist.vendor.dolby.dialog.enhancer.enable"),
            dialogAmount = i("persist.vendor.dolby.dialog.enhancer.amount"),
            virtualizerOn = b("persist.vendor.dolby.virtualizer.enable"),
            virtualizerAmount = i("persist.vendor.dolby.virtualizer.amount"),
            ieqOn = b("persist.vendor.dolby.ieq.enable"),
            spectralOn = b("persist.vendor.dolby.spectral.enable"),
            levelerOn = b("persist.vendor.dolby.volume.leveler.enable"),
            deviceMap = props["persist.audio.effect.device_map"]
        )
    }

    private val propLine = Regex("""^\[([^\]]+)\]: \[(.*)\]$""")

    private fun readProps(): Map<String, String>? = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop"))
        val text = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        val map = HashMap<String, String>(512)
        for (line in text.lineSequence()) {
            val m = propLine.find(line) ?: continue
            map[m.groupValues[1]] = m.groupValues[2]
        }
        map
    } catch (t: Throwable) {
        null
    }
}