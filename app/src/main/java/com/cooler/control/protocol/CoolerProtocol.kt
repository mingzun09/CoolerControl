package com.cooler.control.protocol

import java.util.UUID

/**
 * 小米冰封散热背夹 · 蓝牙协议编解码。
 *
 * 帧结构（实测确认）：
 *   FE DC BA | flags | opcode | len(2, BE) | seq | payload(len-1) | EF
 *
 * 配置项为 TLV：[len][type(2, BE)][value]，len = 2 + value 长度。
 */
object CoolerProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("0000af00-0000-1000-8000-00805f9b34fb")
    val WRITE_UUID: UUID = UUID.fromString("0000af07-0000-1000-8000-00805f9b34fb")
    val NOTIFY_UUID: UUID = UUID.fromString("0000af08-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val OP_GET_TARGET_INFO = 0x02
    const val OP_SET_CONFIG = 0xF2
    const val OP_GET_CONFIG = 0xF3
    const val OP_NOTIFY_CONFIG = 0xF4

    const val ATTR_TEMP = 0x003F
    const val ATTR_MODE = 0x0040
    const val ATTR_TIMER = 0x0041
    const val ATTR_LIGHT = 0x0042

    /** 定时关闭：不启用 */
    const val TIMER_OFF = 0xFFFF

    private const val FLAGS_COMMAND = 0xC1
    private const val HEAD_0 = 0xFE.toByte()
    private const val HEAD_1 = 0xDC.toByte()
    private const val HEAD_2 = 0xBA.toByte()
    private const val TAIL = 0xEF.toByte()

    data class Tlv(val type: Int, val value: ByteArray)

    data class Frame(
        /** 1 = 命令，0 = 响应 */
        val isCommand: Boolean,
        val op: Int,
        /** 响应状态，0 = 成功；通知帧恒为 0 */
        val status: Int,
        val seq: Int,
        val tlvs: List<Tlv>,
    ) {
        fun tlv(type: Int): ByteArray? = tlvs.firstOrNull { it.type == type }?.value
    }

    /** 组帧 */
    fun encode(op: Int, payload: ByteArray = ByteArray(0), seq: Int = 0): ByteArray {
        val len = payload.size + 1
        val out = ByteArray(8 + payload.size + 1)
        out[0] = HEAD_0
        out[1] = HEAD_1
        out[2] = HEAD_2
        out[3] = FLAGS_COMMAND.toByte()
        out[4] = op.toByte()
        out[5] = ((len shr 8) and 0xFF).toByte()
        out[6] = (len and 0xFF).toByte()
        out[7] = (seq and 0xFF).toByte()
        payload.copyInto(out, 8)
        out[out.size - 1] = TAIL
        return out
    }

    /** 读配置请求：payload 为属性 ID 的 2 字节大端串 */
    fun encodeGetConfig(attrs: List<Int>, seq: Int): ByteArray {
        val payload = ByteArray(attrs.size * 2)
        attrs.forEachIndexed { i, a ->
            payload[i * 2] = ((a shr 8) and 0xFF).toByte()
            payload[i * 2 + 1] = (a and 0xFF).toByte()
        }
        return encode(OP_GET_CONFIG, payload, seq)
    }

    /** 写配置请求：payload 为 TLV 串 */
    fun encodeSetConfig(items: List<Tlv>, seq: Int): ByteArray =
        encode(OP_SET_CONFIG, encodeTlvs(items), seq)

    fun encodeTlv(type: Int, value: ByteArray): ByteArray {
        val out = ByteArray(3 + value.size)
        out[0] = (2 + value.size).toByte()
        out[1] = ((type shr 8) and 0xFF).toByte()
        out[2] = (type and 0xFF).toByte()
        value.copyInto(out, 3)
        return out
    }

    fun encodeTlvs(items: List<Tlv>): ByteArray {
        val parts = items.map { encodeTlv(it.type, it.value) }
        val out = ByteArray(parts.sumOf { it.size })
        var p = 0
        for (part in parts) { part.copyInto(out, p); p += part.size }
        return out
    }

    /**
     * 解析一帧。传入字节里可能包含多帧（设备会连发），这里只取第一帧。
     */
    fun decode(raw: ByteArray, offset: Int = 0): Frame? {
        val start = findHeader(raw, offset) ?: return null
        if (start + 8 > raw.size) return null
        val flags = raw[start + 3].toInt() and 0xFF
        val op = raw[start + 4].toInt() and 0xFF
        val len = ((raw[start + 5].toInt() and 0xFF) shl 8) or (raw[start + 6].toInt() and 0xFF)
        if (len < 1) return null
        // 帧头 3 + flags 1 + opcode 1 + len 2 = 7，body 从这里开始；EF 不计数
        val bodyStart = start + 7
        val bodyEnd = bodyStart + len
        if (bodyEnd > raw.size) return null
        val body = raw.copyOfRange(bodyStart, bodyEnd)
        // 0x81 是设备主动上报（bit7 也为 1），不能只按 bit7 判定
        val isCommand = (flags and 0x80) != 0 && op != OP_NOTIFY_CONFIG
        return if (op == OP_GET_CONFIG || op == OP_SET_CONFIG) {
            if (body.size < 2) return null
            Frame(isCommand, op, body[0].toInt() and 0xFF, body[1].toInt() and 0xFF, parseTlvs(body, 2))
        } else {
            Frame(isCommand, op, 0, body[0].toInt() and 0xFF, parseTlvs(body, 1))
        }
    }

    /** 拆分一个字节流里的所有完整帧 */
    fun decodeAll(raw: ByteArray): List<Frame> {
        val out = ArrayList<Frame>()
        var offset = 0
        while (true) {
            val start = findHeader(raw, offset) ?: break
            val frame = decode(raw, start) ?: break
            out.add(frame)
            val len = ((raw[start + 5].toInt() and 0xFF) shl 8) or (raw[start + 6].toInt() and 0xFF)
            offset = start + 7 + len + 1
        }
        return out
    }

    private fun findHeader(raw: ByteArray, from: Int): Int? {
        var i = from
        while (i + 3 <= raw.size) {
            if (raw[i] == HEAD_0 && raw[i + 1] == HEAD_1 && raw[i + 2] == HEAD_2) return i
            i++
        }
        return null
    }

    fun parseTlvs(body: ByteArray, from: Int): List<Tlv> {
        val out = ArrayList<Tlv>()
        var i = from
        while (i + 3 <= body.size) {
            val len = body[i].toInt() and 0xFF
            if (len < 2 || i + 1 + len > body.size) break
            val type = ((body[i + 1].toInt() and 0xFF) shl 8) or (body[i + 2].toInt() and 0xFF)
            out.add(Tlv(type, body.copyOfRange(i + 3, i + 1 + len)))
            i += 1 + len
        }
        return out
    }

    // ---- 语义转换 ----

    /** 温度：有符号字节，单位 ℃ */
    fun tempOf(value: ByteArray?): Int? = value?.takeIf { it.isNotEmpty() }?.let { it[0].toInt() }

    fun modeOf(value: ByteArray?): Int? = value?.takeIf { it.isNotEmpty() }?.let { it[0].toInt() and 0xFF }

    /** 定时剩余秒数；0xFFFF 表示未启用 */
    fun timerOf(value: ByteArray?): Int? {
        if (value == null || value.size < 2) return null
        val v = ((value[0].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
        return if (v == TIMER_OFF) null else v
    }

    fun lightModeOf(value: ByteArray?): Int? = value?.takeIf { it.isNotEmpty() }?.let { it[0].toInt() and 0xFF }

    fun lightColorOf(value: ByteArray?): Int? =
        value?.takeIf { it.size >= 2 }?.let { it[1].toInt() and 0xFF } ?: value?.takeIf { it.size == 1 }?.let { 0 }

    fun modeValue(mode: Int) = byteArrayOf(mode.toByte())

    fun lightValue(mode: Int, color: Int) = byteArrayOf(mode.toByte(), color.toByte())

    /** 定时时长（秒）；null = 取消 */
    fun timerValue(seconds: Int?): ByteArray {
        if (seconds == null) return byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val v = seconds.coerceIn(0, TIMER_OFF - 1)
        return byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())
    }
}