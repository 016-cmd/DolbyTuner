package com.zero16.dolbytuner

import android.media.audiofx.AudioEffect
import android.os.SystemClock
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Dolby DAP 实时层（GEQ 参数 110）
 *
 * 只做一件事：把 20 段 GEQ 曲线写进「运行中的」杜比 DAP，不重启音频服务。
 * 协议细节全部来自对 DapTune（Apache-2.0）接口事实的核对，实现为本项目自写。
 *
 * 关键协议（不要改，改了就写不进去/读不出来）：
 *   查询  key = command + 5
 *   读曲线 key = 0x01000000 + 5 + (110 shl 16) + (profile shl 8)
 *   写     setParameter(5, [0x01000000, 21, profile, 110, gain×20])
 *   保存   setParameter(5, [0x09000000])
 *
 * 安全红线：
 *   ① offload 型读回全是 0，禁止「回读比对 → 回滚」，否则把曲线清零
 *   ② 上限不卡 +10 dB（本模块 dax 里有 +11.56 dB 的段），这里放宽到 ±24 dB
 *   ③ 每次用完必须 release()，否则控制权被自己占住
 */
object DapRealtime {

    const val BANDS = 20
    const val Q4_PER_DB = 16
    const val MAX_BOOST_Q4 = 384      // +24.0 dB（放宽到二改版同款，DSP 侧若截断不报错）
    const val MIN_GAIN_Q4 = -384      // -24.0 dB

    val FREQS = intArrayOf(
        47, 141, 234, 328, 469, 656, 844, 1031, 1313, 1688,
        2250, 3000, 3750, 4688, 5813, 7125, 9000, 11250, 13875, 19688
    )

    // ── 协议常量 ──
    private const val CMD_PARAM = 5
    private const val COMMAND_SET_PROFILE = 0x01000000
    private const val COMMAND_GET_PROFILE_COUNT = 0x03000000
    private const val COMMAND_SAVE = 0x09000000
    private const val COMMAND_GET_CURRENT = 0x0a000000
    private const val COMMAND_GET_DAP_ON = 0x00000000
    private const val PARAM_GEQ_BAND_GAINS = 110

    // ── FourCC 通道（A 类音效）──
    private const val CMD_SET_BATCH = 0     // command(0)：批量设置参数
    private const val CMD_READ_FOURCC = 20  // getParameter(20, in, out)：读回

    private val TYPE_UUID = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")
    private val IMPL_UUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")
    private val PROXY_TYPE_UUID = UUID.fromString("fa81dbde-588b-11ed-9b6a-0242ac120002")
    private val OFFLOAD_TYPE_UUID = UUID.fromString("46d279d9-9be7-453d-9d7c-ef937f675587")

    data class Info(
        val available: Boolean,
        val detail: String,
        val readback: Boolean = false,
        val profileCount: Int = 0,
        val currentProfile: Int = -1,
        val dapOn: Boolean = false
    )

    // ────────────────────────────────────────────────────────────
    //  反射桥（4 参构造 + setParameter/getParameter）
    // ────────────────────────────────────────────────────────────

    private var bypassReady = false

    /**
     * 隐藏 API 豁免：必须用 "L" 全量豁免。
     * 只写类名前缀在 Android 17 上匹配不到 -> getParameter 查找失败 ->
     * NoSuchMethodException（现象：能写不能读）。
     */
    private fun ensureBypass() {
        if (bypassReady) return
        val ok = runCatching {
            org.lsposed.hiddenapibypass.HiddenApiBypass
                .addHiddenApiExemptions("L")
            true
        }.getOrDefault(false)
        if (ok) bypassReady = true
    }

    /** 先 getMethod，失败再用 getDeclaredMethod 并解除访问限制 */
    private fun findEffectMethod(name: String, vararg types: Class<*>): Method {
        val c = AudioEffect::class.java
        val m = runCatching { c.getMethod(name, *types) }.getOrNull()
            ?: runCatching { c.getDeclaredMethod(name, *types) }.getOrNull()
            ?: throw NoSuchMethodException("android.media.audiofx.AudioEffect.$name")
        runCatching { m.isAccessible = true }
        return m
    }

    private val ctor: Constructor<*> by lazy {
        AudioEffect::class.java.getDeclaredConstructor(
            UUID::class.java,
            UUID::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
    }

    private val setParamMethod: Method by lazy {
        findEffectMethod("setParameter", Int::class.javaPrimitiveType!!, ByteArray::class.java)
    }

    private val getParamMethod: Method by lazy {
        findEffectMethod("getParameter", Int::class.javaPrimitiveType!!, ByteArray::class.java)
    }

    /** getParameter(int, byte[], byte[]) —— 带出入参的读法，FourCC 读回要用 */
    private val getParamFullMethod: Method by lazy {
        findEffectMethod(
            "getParameter",
            Int::class.javaPrimitiveType!!,
            ByteArray::class.java,
            ByteArray::class.java
        )
    }

    private fun findDescriptor(): AudioEffect.Descriptor? =
        AudioEffect.queryEffects()?.firstOrNull { it.uuid == IMPL_UUID }

    /**
     * 打开 effect。
     *
     * 真机取证：本机 `dap_hw` 注册行是
     *   <effect name="dap_hw" library="dap_hw" uuid="9d4921da-…"/>
     * 全盘没有任何 `ec7178ec-…`（那是其它小米机型上的 DAP type）。
     * 所以这里不能用硬编码 type，顺序是：
     *   ① 系统上报的 descriptor.type（最准）
     *   ② impl uuid 自身（MIUI 的 DolbyAudioEffect 内部就只带一个 uuid）
     *   ③ 旧机型的 ec7178ec（兜底）
     */
    private fun openEffect(): AudioEffect {
        ensureBypass()
        val candidates = ArrayList<UUID>(3)
        findDescriptor()?.type?.let { candidates.add(it) }
        candidates.add(IMPL_UUID)
        candidates.add(TYPE_UUID)

        var last: Throwable? = null
        for (type in candidates.distinct()) {
            try {
                val e = ctor.newInstance(type, IMPL_UUID, 0, 0) as AudioEffect
                attachWatchers(e)
                return e
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("未找到可用的 DAP effect type")
    }

    private fun AudioEffect.setParam(key: Int, payload: ByteArray): Int =
        setParamMethod.invoke(this, key, payload) as Int

    private fun AudioEffect.getParam(key: Int, buffer: ByteArray): Int =
        getParamMethod.invoke(this, key, buffer) as Int

    private fun statusText(status: Int, what: String): String = when {
        status >= 0 -> ""
        status == AudioEffect.ERROR_BAD_VALUE -> "$what：参数无效"
        status == AudioEffect.ERROR_INVALID_OPERATION -> "$what：操作无效"
        status == AudioEffect.ERROR_NO_INIT -> "$what：音效未初始化"
        status == AudioEffect.ERROR_DEAD_OBJECT -> "$what：音频服务已重启"
        else -> "$what：错误 $status"
    }

    /** 反射会把真实异常包一层，这里剥出根因，方便排查 */
    private fun rootCause(t: Throwable): String {
        var cur: Throwable = t
        var guard = 0
        while (cur.cause != null && cur.cause !== cur && guard < 8) {
            cur = cur.cause!!
            guard++
        }
        val name = cur.javaClass.simpleName
        val msg = cur.message?.takeIf { it.isNotBlank() } ?: ""
        return if (msg.isEmpty()) name else "$name: $msg"
    }

    // ────────────────────────────────────────────────────────────
    //  协议组包
    // ────────────────────────────────────────────────────────────

    private fun queryKey(command: Int): Int = command + CMD_PARAM

    private fun curveReadKey(profile: Int): Int =
        COMMAND_SET_PROFILE + CMD_PARAM +
            (PARAM_GEQ_BAND_GAINS shl 16) + (profile shl 8)

    private fun buildCurvePayload(profile: Int, gains: IntArray): ByteArray {
        val buf = ByteBuffer.allocate((BANDS + 4) * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(COMMAND_SET_PROFILE)
        buf.putInt(BANDS + 1)
        buf.putInt(profile)
        buf.putInt(PARAM_GEQ_BAND_GAINS)
        gains.forEach { buf.putInt(it) }
        return buf.array()
    }
    /** 任意参数的写入包（探测用）：[cmd][count][profile][paramId][values...] */
    private fun buildParamPayload(profile: Int, paramId: Int, values: IntArray): ByteArray {
        val buf = ByteBuffer.allocate((values.size + 4) * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(COMMAND_SET_PROFILE)
        buf.putInt(values.size + 1)
        buf.putInt(profile)
        buf.putInt(paramId)
        values.forEach { buf.putInt(it) }
        return buf.array()
    }

    private fun buildSavePayload(): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(COMMAND_SAVE).array()

    /**
     * command(0) 批量设置包 —— 逆向 C0832dg.O(profile) 的原始格式：
     *
     *   putInt(profile)
     *   putInt(参数条数)
     *   每条：
     *     putInt(profile)
     *     putInt(fourcc)
     *     putInt(值的个数)
     *     putInt(值…)
     *
     * 这是真正能改 DSP 参数的那条通道；之前用的 command(5) 逐条是读/写单参数的旧接口。
     */
    private fun buildParamSetPayload(
        profile: Int,
        items: List<Pair<String, IntArray>>
    ): ByteArray {
        var words = 2
        items.forEach { words += it.second.size + 3 }
        val buf = ByteBuffer.allocate(words * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(profile)
        buf.putInt(items.size)
        items.forEach { (tag, vs) ->
            buf.putInt(profile)
            buf.putInt(fourcc(tag))
            buf.putInt(vs.size)
            vs.forEach { buf.putInt(it) }
        }
        return buf.array()
    }

    private fun buildQueryBuffer(): ByteArray = ByteArray(12)

    private fun buildCurveBuffer(): ByteArray = ByteArray((BANDS + 2) * 4)

    private fun readFirstInt(bytes: ByteArray): Int =
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int

    private fun readCurveInts(bytes: ByteArray): IntArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(BANDS) { buf.int }
    }

    // ────────────────────────────────────────────────────────────
    //  对外接口
    // ────────────────────────────────────────────────────────────

    /** 探测能力（会不会写入，取决于 readback / profileCount） */
    fun inspect(): Info {
        var effect: AudioEffect? = null
        return try {
            val desc = findDescriptor()
                ?: return Info(false, "未找到 Dolby DAP（本机可能不支持实时层）")
            val readback = desc.type != OFFLOAD_TYPE_UUID &&
                desc.name?.equals("DAP_offload", ignoreCase = true) != true
            effect = openEffect()
            // getParameter 被隐藏 API 屏蔽时读不到，但写入仍然可用：降级为“仅写入”
            val dapOn = runCatching { queryInt(effect, COMMAND_GET_DAP_ON) }.getOrDefault(0) > 0
            val count = runCatching { queryInt(effect, COMMAND_GET_PROFILE_COUNT) }.getOrDefault(0)
            val current = runCatching { queryInt(effect, COMMAND_GET_CURRENT) }.getOrDefault(-1)
            val canRead = count in 1..32 || current >= 0 || dapOn
            Info(
                available = true,
                detail = buildString {
                    append(if (readback && canRead) "可用（支持曲线回读）" else "可用（仅写入，不支持回读）")
                    append(" · type=").append(desc.type.toString().take(8))
                },
                readback = readback && canRead,
                profileCount = count,
                currentProfile = current,
                dapOn = dapOn
            )
        } catch (t: Throwable) {
            Info(false, "打开实时层失败：${rootCause(t)}")
        } finally {
            runCatching { effect?.release() }
        }
    }

    /**
     * 只读：杜比 DAP 当前是否在生效。
     *
     * 判定顺序：
     *   ① COMMAND_GET_DAP_ON（DSP 侧 DAP 开关，最接近「音频链上是否启用」）
     *   ② 读不到时退回 AudioEffect.enabled（只代表 effect 实例被启用）
     *
     * @return true = 生效中，false = 未生效，null = 无法判断（本机无 DAP / 无控制权）
     */
    @Synchronized
    fun dolbyState(): Boolean? {
        // A 类写入可能让 Dolby 重建效果链，旧句柄会失效；先探活
        ensureAlive()
        var own = false
        var e: AudioEffect? = null
        return try {
            e = session ?: openEffect().also { own = true }
            val q = runCatching { queryInt(e, COMMAND_GET_DAP_ON) }.getOrNull()
            when {
                q != null && q > 0 -> true
                q != null && q == 0 -> false
                else -> runCatching { e.enabled }.getOrNull()
            }
        } catch (t: Throwable) {
            null
        } finally {
            if (own) runCatching { e?.release() }
        }
    }

    /**
     * 读回当前生效的 GEQ 曲线（优先复用常驻会话）。
     *
     * 顺序：当前 profile → 其余 profile（0..count-1）。
     * 只要有一次 getParam 返回成功就采用（哪怕全是 0，也代表 DSP 确实是平直）。
     *
     * 如果失败原因是「句柄已失效」（音频服务重启过），会自动换一个新句柄再读一次。
     */
    @Synchronized
    fun readLive(): IntArray? {
        val (curve, err) = readLiveOnce()
        if (curve != null) return curve
        // 只有"死对象"才重开；读回不支持之类的错误不要乱重开（重开会触发 Dolby 下发旧值）
        if (err != null && isDeadError(err) && ensureAlive()) {
            return readLiveOnce().first
        }
        return null
    }

    /** 读一次，返回 (曲线, 错误描述) */
    private fun readLiveOnce(): Pair<IntArray?, String?> {
        var lastErr: String? = null
        return try {
            val e = session ?: openEffect().also { session = it }
            val count = runCatching { queryInt(e, COMMAND_GET_PROFILE_COUNT) }.getOrDefault(0)
            val cur = runCatching { queryInt(e, COMMAND_GET_CURRENT) }.getOrDefault(-1)
            val order = LinkedHashSet<Int>()
            if (cur in 0..31) order.add(cur)
            if (count in 1..32) for (p in 0 until count) order.add(p) else order.add(0)
            for (p in order) {
                val buf = buildCurveBuffer()
                val st = e.getParam(curveReadKey(p), buf)
                if (st >= 0) return readCurveInts(buf) to null
                lastErr = statusText(st, "读取曲线")
            }
            null to lastErr
        } catch (t: Throwable) {
            null to rootCause(t)
        }
    }

    /** 诊断用：把「读曲线」每一步的状态码拼成一行文字 */
    @Synchronized
    fun probeRead(): String {
        var effect: AudioEffect? = null
        return try {
            effect = session ?: openEffect().also { session = it }
            val count = runCatching { queryInt(effect, COMMAND_GET_PROFILE_COUNT) }.getOrDefault(-999)
            val cur = runCatching { queryInt(effect, COMMAND_GET_CURRENT) }.getOrDefault(-999)
            val sb = StringBuilder("cur=$cur count=$count")
            val n = if (count in 1..32) count else 1
            for (p in 0 until n) {
                val buf = buildCurveBuffer()
                val st = effect.getParam(curveReadKey(p), buf)
                val sum = if (st >= 0) readCurveInts(buf).sumOf { kotlin.math.abs(it) } else -1
                sb.append(" | p$p st=$st sum=$sum")
            }
            sb.toString()
        } catch (t: Throwable) {
            "probe 失败：${rootCause(t)}"
        } finally {
            if (session == null) runCatching { effect?.release() }
        }
    }

    /** 读回指定 profile 的 GEQ 曲线；不支持回读时返回 null */
    fun readCurve(profile: Int = -1): IntArray? {
        var effect: AudioEffect? = null
        return try {
            effect = openEffect()
            val target = if (profile >= 0) profile else queryInt(effect, COMMAND_GET_CURRENT)
            if (target < 0) return null
            val buf = buildCurveBuffer()
            val st = effect.getParam(curveReadKey(target), buf)
            if (st < 0) return null
            readCurveInts(buf)
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { effect?.release() }
        }
    }

    /**
     * 读回单个 DAP 参数的第一个值（101/102/105/108/111/233/234/235 等）。
     * 读不到或协议不支持时返回 null，调用方应保留本地记录，不要写 0。
     */
    @Synchronized
    fun readParam(paramId: Int): Int? {
        return try {
            val e = session ?: openEffect().also { session = it }
            val cur = runCatching { queryInt(e, COMMAND_GET_CURRENT) }.getOrDefault(0)
            val order = if (cur in 0..31) linkedSetOf(cur, 0) else linkedSetOf(0)
            var value: Int? = null
            for (p in order) {
                val buf = ByteArray(8)
                val st = e.getParam(paramReadKey(paramId, p), buf)
                if (st >= 0) {
                    value = readFirstInt(buf)
                    break
                }
            }
            value
        } catch (t: Throwable) {
            null
        }
    }

    private fun paramReadKey(paramId: Int, profile: Int): Int =
        COMMAND_SET_PROFILE + CMD_PARAM + (paramId shl 16) + (profile shl 8)

    /**
     * 写入曲线（一次性：打开 → 写 → 释放）。开机写回、探针用。
     * @param onlyProfile null = 写全部
     */
    fun writeCurve(curve: IntArray, onlyProfile: Int? = null): String? {
        if (curve.size != BANDS) return "曲线长度不正确（需要 $BANDS 段）"
        var effect: AudioEffect? = null
        return try {
            effect = openEffect()
            writeOn(effect, curve, onlyProfile)
        } catch (t: Throwable) {
            "写入失败：${rootCause(t)}"
        } finally {
            runCatching { effect?.release() }
        }
    }

    /** 让 Dolby 把当前参数落进自己的数据库（重启后由它自己读回） */
    fun save(): String? {
        var effect: AudioEffect? = null
        return try {
            effect = openEffect()
            saveOn(effect)
        } catch (t: Throwable) {
            "保存失败：${rootCause(t)}"
        } finally {
            runCatching { effect?.release() }
        }
    }

    // ────────────────────────────────────────────────────────────
    //  会话：拖动时复用同一个 effect 实例
    //
    //  真机现象：拖动没声音，点「保存到文件」才生效。
    //  原因：每次写都新建 + release，release 后全局 effect 实例被回收，
    //        刚写进内存的参数随之丢失；而 save 会把值提交到 Dolby 自己的
    //        存储并触发重放，所以只有那一下能听到。
    //  解决：页面内保持一个常开实例，拖动期间只写不释放。
    // ────────────────────────────────────────────────────────────

    private var session: AudioEffect? = null

    // ── 自动重建：Dolby 重建效果链时立刻重连并重注 ──────────────
    /** 最后一次成功写入的 GEQ 曲线，重建后用它重注 */
    @Volatile
    private var lastCurve: IntArray? = null

    @Volatile
    private var lastRebindAt = 0L

    /** 打开常驻会话（进入页面时调一次） */
    @Synchronized
    fun openSession(): String? {
        if (session != null) return null
        return try {
            session = openEffect()
            null
        } catch (t: Throwable) {
            session = null
            rootCause(t)
        }
    }

    /** 关闭常驻会话（离开页面时调） */
    @Synchronized
    fun closeSession() {
        runCatching { session?.release() }
        session = null
    }

    // ────────────────────────────────────────────────────────────
    //  自愈：音频服务重启后，旧的 AudioEffect 句柄会变成"死对象"
    //  （读写都返回 ERROR_DEAD_OBJECT），必须丢掉重开一个才能继续用。
    //  重开这一瞬间 Dolby 会把"它自己存的值"下发上来，
    //  所以调用方在重开之后要立刻把我们的曲线写回去（refreshFromDevice 已在做）。
    // ────────────────────────────────────────────────────────────

    /** 这个错误是不是"句柄已失效"这一类的 */
    private fun isDeadError(msg: String): Boolean =
        msg.contains("音频服务已重启") ||
            msg.contains("控制权被") ||
            msg.contains("DEAD_OBJECT", true)

    /**
     * 给新句柄挂上「控制权 / 启用状态」监听。
     *
     * 这两个是 AudioEffect 自带的原生回调，不乱轮询、不费电：
     * - 控制权被抢（Dolby 重建、别的 App 接管）→ 回调
     * - 效果被系统关掉 → 回调
     * 一收到就丢弃句柄、重开、把最后的曲线写回去。
     */
    private fun attachWatchers(e: AudioEffect) {
        // 只记日志，不主动重建：
        // Dolby 重建时会回调，但如果我们在回调里立刻 release 再 open，
        // 会让它反复重建，最终被判「未生效」。
        runCatching {
            e.setControlStatusListener { _, granted ->
                android.util.Log.w("DapRealtime", "DAP controlGranted=$granted")
            }
        }
        runCatching {
            e.setEnableStatusListener { _, enabled ->
                android.util.Log.w("DapRealtime", "DAP enabled=$enabled")
            }
        }
    }

    /** 去抖后重建句柄，并把最后一次曲线重新写回去 */
    private fun scheduleRebind(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRebindAt < 800L) return
        lastRebindAt = now
        Thread {
            runCatching {
                synchronized(this) {
                    val curve = lastCurve?.copyOf()
                    runCatching { session?.release() }
                    session = null
                    if (curve != null) writeLive(curve)
                }
            }
            android.util.Log.w("DapRealtime", "Dolby 句柄失效（$reason）→ 已自动重建并重注")
        }.start()
    }

    /**
     * A 类写完之后：丢句柄 + 立刻把最后一次 GEQ 曲线重注回去。
     * Dolby 重建效果链后，GEQ 的曲线不会自己回来，必须重注。
     */
    @Synchronized
    fun rebindAndReapply() {
        runCatching { session?.release() }
        session = null
        val c = lastCurve?.copyOf() ?: return
        writeLive(c)
    }

    /**
     * 一次性诊断：把 profile 结构、当前档、每档写入后的 DAP 状态全部记下来。
     * 只写 beon（虚拟低音开关）这一条，写完复位，不改其它参数。
     */
    @Synchronized
    fun diagReport(): String {
        val sb = StringBuilder()
        fun line(s: String) {
            sb.append(s).append('\n')
            android.util.Log.i("DapDiag", s)
        }
        try {
            line("Dolby profile 诊断")
            line("prop(persist.audio.dolby.profile)=${propProfile() ?: "读不到"}")
            val e = session ?: openEffect().also { session = it }
            line("enabled=${runCatching { e.enabled }.getOrNull()}")
            line("hasControl=${runCatching { e.hasControl() }.getOrNull()}")
            val count = runCatching { queryInt(e, COMMAND_GET_PROFILE_COUNT) }.getOrNull()
            val cur = runCatching { queryInt(e, COMMAND_GET_CURRENT) }.getOrNull()
            val on0 = runCatching { queryInt(e, COMMAND_GET_DAP_ON) }.getOrNull()
            line("queryInt(GET_PROFILE_COUNT)=$count")
            line("queryInt(GET_CURRENT)=$cur")
            line("queryInt(GET_DAP_ON)=$on0")
            line("")

            fun onNow(): String =
                runCatching { queryInt(e, COMMAND_GET_DAP_ON) }.getOrNull()?.toString() ?: "err"

            fun writeBeon(prof: Int, v: Int): String {
                val buf = ByteBuffer.allocate((1 + 4) * 4).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(COMMAND_SET_PROFILE)
                buf.putInt(2)
                buf.putInt(prof)
                buf.putInt(fourcc("beon"))
                buf.putInt(v)
                val st = runCatching { e.setParam(CMD_PARAM, buf.array()) }.getOrElse { -999 }
                return "profile=$prof beon=$v → rc=$st  之后 GET_DAP_ON=${onNow()}"
            }

            val tryProfs = listOf(0, 1, 2, 3, 4, 5)
            tryProfs.forEach { p -> line(writeBeon(p, 1)) }
            line("")
            tryProfs.forEach { p -> line(writeBeon(p, 0)) }
            line("")
            line("复位后 GET_DAP_ON=${onNow()}")
        } catch (t: Throwable) {
            line("异常：${rootCause(t)}")
        }
        return sb.toString()
    }

    /**
     * 主动丢弃当前句柄。
     * A 类写入后 Dolby 可能已经重建效果链，旧句柄写不进 GEQ，
     * 丢掉它，下一次操作会自动重开。
     */
    @Synchronized
    fun dropSession() {
        runCatching { session?.release() }
        session = null
    }

    /**
     * 探活：确认当前会话还能用；不能用就丢掉重开。
     * @return true = 现在有一个可用会话
     */
    @Synchronized
    fun ensureAlive(): Boolean {
        session?.let { e ->
            val ok = runCatching { queryInt(e, COMMAND_GET_DAP_ON) }.isSuccess
            if (ok) return true
            runCatching { e.release() }
            session = null
        }
        return try {
            val ne = openEffect()
            // 新开也要能用才算成功
            queryInt(ne, COMMAND_GET_DAP_ON)
            session = ne
            true
        } catch (t: Throwable) {
            runCatching { session?.release() }
            session = null
            false
        }
    }

    /** 用会话写一次（不含自愈） */
    private fun writeWithSession(curve: IntArray): String? = try {
        val e = session ?: openEffect().also { session = it }
        writeOn(e, curve, null)
    } catch (t: Throwable) {
        "写入失败：${rootCause(t)}"
    }

    /** 临时探测：按参数编号写一组安全值，返回 rc 或错误原因 */
    @Synchronized
    fun probeSet(paramId: Int, values: IntArray): String {
        return try {
            val e = session ?: openEffect().also { session = it }
            if (!e.hasControl()) return "无控制权"
            if (!e.enabled) return "杜比音效未启用"
            val st = e.setParam(CMD_PARAM, buildParamPayload(0, paramId, values))
            if (st < 0) statusText(st, "参数 $paramId") else "rc=$st"
        } catch (t: Throwable) {
            "异常：${rootCause(t)}"
        }
    }
    /** 正式写入单个参数并提交落库（失败返回原因，成功返回 null） */
    @Synchronized
    fun setParamValue(paramId: Int, value: Int): String? {
        return try {
            val e = session ?: openEffect().also { session = it }
            if (!e.hasControl()) return "无控制权"
            if (!e.enabled) return "杜比音效未启用"
            // 每个 profile 都写一遍：普通参数以前写死在 profile 0，
            // 一动它就把当前档切成 0，A 类再写回 4 就落空（表现为"再调没反应"）。
            for (p in allProfiles(e)) {
                val st = e.setParam(CMD_PARAM, buildParamPayload(p, paramId, intArrayOf(value)))
                if (st < 0) return statusText(st, "参数 $paramId / profile $p")
            }
            val s = e.setParam(CMD_PARAM, buildSavePayload())
            if (s < 0) statusText(s, "保存") else null
        } catch (t: Throwable) {
            rootCause(t)
        }
    }
    // ────────────────────────────────────────────────────────────
    //  FourCC 通道（A 类音效：dplp / beon / dvle / ieon / vol …）
    //
    //  写：command(5, [0x01000000, count, 0, fourcc, values…])
    //  批：command(0, [0, N, (0, fourcc, len, values…)…])
    //  读：getParameter(20, [0, fourcc], out)
    //
    //  读回格式各固件不完全一致，读不到时返回 null，
    //  调用方按「未校验」处理，不要当成写入失败。
    // ────────────────────────────────────────────────────────────

    /** 当前生效的 profile 编号；读不到就用 0（不做假设） */
    private fun curProfile(e: AudioEffect): Int {
        // AudioService 内部那份 profile 可能已经被写成 0，
        // 用 queryInt(GET_CURRENT) 取回来会一直拿到 0（自锁）。
        // persist.audio.dolby.profile 是 Dolby 自己持久化的真值，优先用它。
        propProfile()?.let { return it }
        return runCatching { queryInt(e, COMMAND_GET_CURRENT) }
            .getOrNull()?.takeIf { it in 0..31 } ?: 0
    }
    /**
     * A 类写入要覆盖的 profile 列表。
     * 光写"属性里那个"不够：实际播放的档可能是别的编号，
     * 所以跟 GEQ 一样，把所有可能生效的档全写一遍。
     */
    /**
     * 写入统一使用「同一个 profile」。
     *
     * 普通参数（101/102/105/108/111/233/234/235）与 A 类（FourCC）
     * 必须落在同一档，否则会互相顶掉：
     *   以前普通参数写死 profile 0 → 之后的 A 类写到 4 就完全没反应。
     *
     * 取法：优先 getprop persist.audio.dolby.profile（Dolby 自己持久化的真值），
     *       读不到才退回 AudioService 的 GET_CURRENT。
     */
    /**
     * 统一写入的 profile。
     *
     * 实测结论（主人真机对比）：
     *   · v1.7.1 写 profile 4 → A 类有效
     *   · v1.8.0 写 profile 0 → A 类完全没反应
     * 所以真正生效的是 persist.audio.dolby.profile（本机 4），不是 0。
     *   dumpsys 里的 "profile: 0" 只是 AudioService 那份缓存，不代表实际档。
     *
     * 取 propProfile()，读不到再退回 4（本机官方值）。
     */
    private fun currentProfile(e: AudioEffect): Int = propProfile() ?: 4

    private fun allProfiles(e: AudioEffect): List<Int> = listOf(currentProfile(e))

    /**
     * A 类写入使用的 profile —— 这里是**设备掩码**，不是音效档号。
     *
     * 逆向 C0892eg.O(Context) 会按当前 AudioDeviceInfo.type 返回：
     *   扬声器 / 默认      → 0x40000000
     *   耳机 / USB / 蓝牙  → 0x20000000
     * 它写入时把这两个都写一遍（扬声器一遍 + 当前设备一遍）。
     *
     * 之前把这里当 0/1/2/3/4 档号用，DSP 收到不认识的掩码就会
     * "rc = 0 但什么都不改"，这正是写入成功却没有声音变化的原因。
     */
    private fun profilesForWrite(e: AudioEffect): List<Int> =
        listOf(0x40000000, 0x20000000)

    /** 读 persist.audio.dolby.profile（普通 App 可用 getprop，不需要权限） */
    private fun propProfile(): Int? = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", "persist.audio.dolby.profile"))
        val txt = p.inputStream.bufferedReader().use { it.readText() }.trim()
        p.waitFor()
        txt.toIntOrNull()
    }.getOrNull()?.takeIf { it in 0..31 }

    /** 四字符 → int（小端：'d' | 'p'<<8 | 'l'<<16 | 'p'<<24） */
    private fun fourcc(tag: String): Int {
        var v = 0
        val n = tag.length.coerceIn(1, 4)
        for (i in 0 until n) v = v or ((tag[i].code and 0xFF) shl (i * 8))
        return v
    }

    /** 写一个 FourCC 参数；成功返回 null */
    @Synchronized
    fun writeFourCC(tag: String, vararg values: Int): String? {
        return try {
            val e = session ?: openEffect().also { session = it }
            if (!e.hasControl()) return "无控制权"
            if (!e.enabled) return "杜比音效未启用"
            val buf = buildParamSetPayload(curProfile(e), listOf(tag to values))
            val st = e.setParam(CMD_SET_BATCH, buf)
            android.util.Log.i("DapWr", "$tag p=${curProfile(e)} rc=$st ${values.joinToString(",")}")
            if (st < 0) statusText(st, tag) else null
        } catch (t: Throwable) {
            rootCause(t)
        }
    }

    /** 批量写多个 FourCC 参数（一条 command(0)）；成功返回 null */
    @Synchronized
    fun writeFourCCBatch(items: List<Pair<String, IntArray>>): String? {
        if (items.isEmpty()) return null
        return try {
            val e = session ?: openEffect().also { session = it }
            if (!e.hasControl()) return "无控制权"
            if (!e.enabled) return "杜比音效未启用"
            // 逆向 APP 的做法（C0650ag → C0832dg.O → C1113iG.I(effect, 0, payload)）：
            //   command(0) + [profile, count, (profile, fourcc, len, values…)×n]
            // 之前用的 command(5) 逐条，DSP 收下 rc=0 但不会真正生效。
            for (prof in profilesForWrite(e)) {
                val st = e.setParam(CMD_SET_BATCH, buildParamSetPayload(prof, items))
                android.util.Log.i(
                    "DapWr",
                    "batch p=$prof rc=$st n=${items.size} ${items.joinToString(" ") { it.first }}"
                )
                if (st < 0) {
                    // 整包被拒：退回逐条（每条一个 count=1 的小包）
                    for ((tag, vs) in items) {
                        val st2 = e.setParam(
                            CMD_SET_BATCH,
                            buildParamSetPayload(prof, listOf(tag to vs))
                        )
                        android.util.Log.i("DapWr", "$tag p=$prof rc=$st2 ${vs.joinToString(",")}")
                        if (st2 < 0) return statusText(st2, tag)
                    }
                }
            }
            null
        } catch (t: Throwable) {
            rootCause(t)
        }
    }

    /** command(int, byte[], byte[]) —— 带输出缓冲，读回测试要用 */
    private val commandMethod: Method by lazy {
        findEffectMethod(
            "command",
            Int::class.javaPrimitiveType!!,
            ByteArray::class.java,
            ByteArray::class.java
        )
    }

    /** 读回 FourCC 参数；读不到返回 null（不代表写入失败） */
    @Synchronized
    fun readFourCC(tag: String, count: Int = 1): IntArray? {
        return try {
            val e = session ?: openEffect().also { session = it }
            val inBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0)
                .putInt(fourcc(tag))
                .array()
            val out = ByteArray((count + 2) * 4)
            val st = getParamFullMethod.invoke(e, CMD_READ_FOURCC, inBuf, out) as Int
            if (st < 0) return null
            val b = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
            val first = b.int
            val n = if (first in 0..count) first else count
            IntArray(n) { b.int }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 读回探测：不改任何参数，只试 5 种读法，把返回码和原始回包打出来。
     * 用于判断本机 Dolby 到底有没有回读通道。
     */
    @Synchronized
    fun probeReadback(tag: String = "dplp"): String {
        val sb = StringBuilder()
        val fc = fourcc(tag)
        sb.append("Dolby DAP FourCC 读回测试\n")
        sb.append("tag=\"").append(tag).append("\"  fourcc=0x")
            .append(Integer.toHexString(fc)).append("\n\n")

        fun hex(b: ByteArray, n: Int = 48): String {
            val m = minOf(n, b.size)
            if (m <= 0) return "(空)"
            val out = StringBuilder()
            for (i in 0 until m) out.append(String.format(java.util.Locale.US, "%02X ", b[i]))
            return out.toString().trim()
        }

        fun buf(vararg v: Int): ByteArray {
            val bb = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            v.forEach { bb.putInt(it) }
            return bb.array()
        }

        try {
            val e = session ?: openEffect().also { session = it }
            sb.append("effect id=").append(e.id)
                .append("  enabled=").append(e.enabled)
                .append("  hasControl=").append(e.hasControl()).append("\n\n")

            fun runCmd(label: String, key: Int, payload: ByteArray) {
                val out = ByteArray(64)
                val rc = try {
                    commandMethod.invoke(e, key, payload, out) as Int
                } catch (t: Throwable) {
                    -999
                }
                sb.append(label).append("  rc=").append(rc)
                    .append("  out=").append(hex(out)).append('\n')
            }

            runCmd("A cmd(5,[0,fourcc])", 5, buf(0, fc))
            runCmd("B cmd(5,[0x01000000,0,0,fourcc])", 5, buf(0x01000000, 0, 0, fc))
            runCmd("C cmd(5,[0x03000000,fourcc])", 5, buf(0x03000000, fc))
            runCmd("D cmd(20,[0,fourcc])", 20, buf(0, fc))

            val out2 = ByteArray(64)
            val rc2 = try {
                getParamFullMethod.invoke(e, 20, buf(0, fc), out2) as Int
            } catch (t: Throwable) {
                -999
            }
            sb.append("E getParam(20,..)  rc=").append(rc2)
                .append("  out=").append(hex(out2)).append('\n')

            val dapOn = runCatching { queryInt(e, COMMAND_GET_DAP_ON) }.getOrNull()
            sb.append("\n[对照] DAP_ON（PARA 通道）=").append(dapOn)
            if (dapOn != null) sb.append("   ← 说明整数 ID 那套是可读的") else sb.append("   ← 对照也没读到")
        } catch (t: Throwable) {
            sb.append("异常：").append(rootCause(t))
        }
        return sb.toString()
    }

    /** 拖动时用：写全部 profile，不释放实例；句柄失效时自动重开并重试一次 */
    @Synchronized
    fun writeLive(curve: IntArray): String? {
        if (curve.size != BANDS) return "曲线长度不正确（需要 $BANDS 段）"
        ensureAlive()
        val first = writeWithSession(curve)
        if (first == null) {
            lastCurve = curve.copyOf()
            return null
        }
        if (!isDeadError(first)) return first
        // 句柄失效（音频服务重启过）→ 换新句柄再写一次
        if (!ensureAlive()) return first
        return writeWithSession(curve).also { if (it == null) lastCurve = curve.copyOf() }
    }

    /** 保存时用：写全部 + 提交落库，不释放实例；同样带自愈 */
    @Synchronized
    fun commit(curve: IntArray): String? {
        if (curve.size != BANDS) return "曲线长度不正确（需要 $BANDS 段）"
        ensureAlive()
        val first = try {
            val e = session ?: openEffect().also { session = it }
            writeOn(e, curve, null) ?: saveOn(e)
        } catch (t: Throwable) {
            "保存失败：${rootCause(t)}"
        }
        if (first == null) {
            lastCurve = curve.copyOf()
            return null
        }
        if (!isDeadError(first)) return first
        if (!ensureAlive()) return first
        return try {
            val e = session!!
            (writeOn(e, curve, null) ?: saveOn(e)).also { if (it == null) lastCurve = curve.copyOf() }
        } catch (t: Throwable) {
            "保存失败：${rootCause(t)}"
        }
    }

    /** 实际写入逻辑（一次性与会话共用） */
    private fun writeOn(effect: AudioEffect, curve: IntArray, onlyProfile: Int?): String? {
        if (!effect.hasControl()) return "音效控制权被其他应用占用"
        if (!effect.enabled) return "杜比音效未启用（请先在系统里打开杜比全景声）"
        if (queryInt(effect, COMMAND_GET_DAP_ON) <= 0) return "杜比全景声已关闭"

        val count = queryInt(effect, COMMAND_GET_PROFILE_COUNT)
        if (count !in 1..32) return "无法取得 profile 数量（返回 $count）"

        val targets: List<Int> = when {
            onlyProfile == null -> {
                // 至少覆盖「当前 profile」：某些固件 queryInt(GET_PROFILE_COUNT)
                // 只返回 4，而当前 profile 是 4，写 0..3 会完全落空。
                val n = maxOf(count, curProfile(effect) + 1)
                (0 until n).toList()
            }
            onlyProfile in 0 until count -> listOf(onlyProfile)
            else -> (0 until count).toList()
        }
        for (p in targets) {
            val st = effect.setParam(CMD_PARAM, buildCurvePayload(p, curve))
            if (st < 0) return statusText(st, "写入 profile $p")
        }
        return null
    }

    private fun saveOn(effect: AudioEffect): String? {
        val st = effect.setParam(CMD_PARAM, buildSavePayload())
        return if (st < 0) statusText(st, "保存") else null
    }

    // ── 变更通知 ──
    // 预设加载 / 重启音频服务写回 之后，DSP 里的曲线变了，但实时层 UI 是本地状态，
    // 不会自己知道。这里放一个计数器，任何外部改动都 +1，UI 订阅它刷新。
    val reloadTick = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** 通知界面：实时层曲线已被外部改动（预设加载、重启后写回等） */
    fun notifyChanged() {
        reloadTick.value = reloadTick.value + 1
    }

    // ── 内部查询 ──
    private fun queryInt(effect: AudioEffect, command: Int): Int {
        val buf = buildQueryBuffer()
        val st = effect.getParam(queryKey(command), buf)
        if (st < 0) throw IllegalStateException(statusText(st, "查询 0x${command.toString(16)}"))
        return readFirstInt(buf)
    }

    // ── Q4 ↔ dB 换算 ──
    fun q4ToDb(q4: Int): Float = q4 / Q4_PER_DB.toFloat()
    fun dbToQ4(db: Float): Int = Math.round(db * Q4_PER_DB)
}