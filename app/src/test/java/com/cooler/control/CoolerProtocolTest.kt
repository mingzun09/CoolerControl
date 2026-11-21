package com.cooler.control

import com.cooler.control.protocol.CoolerMode
import com.cooler.control.protocol.CoolerProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoolerProtocolTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `encode get config matches captured frame`() {
        val frame = CoolerProtocol.encodeGetConfig(
            listOf(
                CoolerProtocol.ATTR_TEMP,
                CoolerProtocol.ATTR_MODE,
                CoolerProtocol.ATTR_TIMER,
                CoolerProtocol.ATTR_LIGHT,
            ),
            seq = 1,
        )
        assertArrayEquals(
            bytes(0xFE, 0xDC, 0xBA, 0xC1, 0xF3, 0x00, 0x09, 0x01,
                0x00, 0x3F, 0x00, 0x40, 0x00, 0x41, 0x00, 0x42, 0xEF),
            frame,
        )
    }

    @Test
    fun `encode set mode matches captured frame`() {
        val frame = CoolerProtocol.encodeSetConfig(
            listOf(CoolerProtocol.Tlv(CoolerProtocol.ATTR_MODE, CoolerProtocol.modeValue(2))),
            seq = 2,
        )
        assertArrayEquals(
            bytes(0xFE, 0xDC, 0xBA, 0xC1, 0xF2, 0x00, 0x05, 0x02, 0x03, 0x00, 0x40, 0x02, 0xEF),
            frame,
        )
    }

    @Test
    fun `decode config response`() {
        val raw = bytes(
            0xFE, 0xDC, 0xBA, 0x01, 0xF3, 0x00, 0x14, 0x00,
            0x01, 0x03, 0x00, 0x3F, 0x0C,
            0x03, 0x00, 0x40, 0x00,
            0x04, 0x00, 0x41, 0xFF, 0xFF,
            0x04, 0x00, 0x42, 0x01, 0x00,
            0xEF,
        )
        val frame = CoolerProtocol.decode(raw)
        assertNotNull(frame)
        frame!!
        assertEquals(CoolerProtocol.OP_GET_CONFIG, frame.op)
        assertEquals(0, frame.status)
        assertEquals(12, CoolerProtocol.tempOf(frame.tlv(CoolerProtocol.ATTR_TEMP)))
        assertEquals(0, CoolerProtocol.modeOf(frame.tlv(CoolerProtocol.ATTR_MODE)))
        assertEquals(CoolerMode.AUTO, CoolerMode.from(0))
        assertEquals(CoolerMode.SILENT, CoolerMode.from(1))
        assertEquals(CoolerMode.ICE, CoolerMode.from(2))
        assertNull(CoolerProtocol.timerOf(frame.tlv(CoolerProtocol.ATTR_TIMER)))
        assertEquals(1, CoolerProtocol.lightModeOf(frame.tlv(CoolerProtocol.ATTR_LIGHT)))
        assertEquals(0, CoolerProtocol.lightColorOf(frame.tlv(CoolerProtocol.ATTR_LIGHT)))
    }

    @Test
    fun `decode temperature notification`() {
        val raw = bytes(0xFE, 0xDC, 0xBA, 0x81, 0xF4, 0x00, 0x05, 0xED, 0x03, 0x00, 0x3F, 0x0D, 0xEF)
        val frame = CoolerProtocol.decode(raw)
        assertNotNull(frame)
        assertEquals(CoolerProtocol.OP_NOTIFY_CONFIG, frame!!.op)
        assertEquals(13, CoolerProtocol.tempOf(frame.tlv(CoolerProtocol.ATTR_TEMP)))
    }

    @Test
    fun `negative temperature is signed`() {
        val raw = bytes(0xFE, 0xDC, 0xBA, 0x81, 0xF4, 0x00, 0x05, 0x01, 0x03, 0x00, 0x3F, 0xF6, 0xEF)
        val frame = CoolerProtocol.decode(raw)!!
        assertEquals(-10, CoolerProtocol.tempOf(frame.tlv(CoolerProtocol.ATTR_TEMP)))
    }

    @Test
    fun `timer value round trip`() {
        assertArrayEquals(bytes(0x0E, 0x10), CoolerProtocol.timerValue(3600))
        assertArrayEquals(bytes(0xFF, 0xFF), CoolerProtocol.timerValue(null))
        assertEquals(3600, CoolerProtocol.timerOf(bytes(0x0E, 0x10)))
        assertNull(CoolerProtocol.timerOf(bytes(0xFF, 0xFF)))
    }

    @Test
    fun `decodes multiple frames in one notification buffer`() {
        val raw = bytes(
            0xFE, 0xDC, 0xBA, 0x81, 0xF4, 0x00, 0x05, 0xED, 0x03, 0x00, 0x3F, 0x0D, 0xEF,
            0xFE, 0xDC, 0xBA, 0x81, 0xF4, 0x00, 0x05, 0xEE, 0x03, 0x00, 0x40, 0x00, 0xEF,
        )
        val frames = CoolerProtocol.decodeAll(raw)
        assertEquals(2, frames.size)
        assertEquals(13, CoolerProtocol.tempOf(frames[0].tlv(CoolerProtocol.ATTR_TEMP)))
        assertEquals(0, CoolerProtocol.modeOf(frames[1].tlv(CoolerProtocol.ATTR_MODE)))
    }
}