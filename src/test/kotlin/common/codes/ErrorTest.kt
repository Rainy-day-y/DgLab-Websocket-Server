package cn.sweetberry.codes.dglab.websocket.common.codes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ErrorTest {

    @Test
    fun `test fromCode returns correct error`() {
        assertEquals(Error.SUCCESS, Error.fromCode("200"))
        assertEquals(Error.CLIENT_OFFLINE, Error.fromCode("209"))
        assertEquals(Error.ID_ALREADY_BOUND, Error.fromCode("400"))
        assertEquals(Error.INTERNAL_ERROR, Error.fromCode("500"))
    }

    @Test
    fun `test fromCode returns INTERNAL_ERROR for unknown code`() {
        assertEquals(Error.INTERNAL_ERROR, Error.fromCode("999"))
        assertEquals(Error.INTERNAL_ERROR, Error.fromCode("unknown"))
    }

    @Test
    fun `test error codes are unique`() {
        val codes = Error.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `test error codes are numeric strings`() {
        Error.entries.forEach { error ->
            assertTrue(error.code.all { it.isDigit() })
        }
    }
}
