package cn.sweetberry.codes.dglab.websocket.common.codes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ChannelTest {

    @Test
    fun `test fromNumber returns correct channel`() {
        assertEquals(Channel.CHANNEL_A, Channel.fromNumber("1"))
        assertEquals(Channel.CHANNEL_B, Channel.fromNumber("2"))
    }

    @Test
    fun `test fromNumber returns null for invalid code`() {
        assertNull(Channel.fromNumber("3"))
        assertNull(Channel.fromNumber("0"))
        assertNull(Channel.fromNumber("A"))
    }

    @Test
    fun `test fromLetter returns correct channel`() {
        assertEquals(Channel.CHANNEL_A, Channel.fromLetter("A"))
        assertEquals(Channel.CHANNEL_B, Channel.fromLetter("B"))
    }

    @Test
    fun `test fromLetter returns null for invalid code`() {
        assertNull(Channel.fromLetter("C"))
        assertNull(Channel.fromLetter("1"))
    }

    @Test
    fun `test channel codes`() {
        assertEquals("1", Channel.CHANNEL_A.numberCode)
        assertEquals("A", Channel.CHANNEL_A.letterCode)
        assertEquals("2", Channel.CHANNEL_B.numberCode)
        assertEquals("B", Channel.CHANNEL_B.letterCode)
    }
}
