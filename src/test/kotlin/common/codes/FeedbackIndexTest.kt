package cn.sweetberry.codes.dglab.websocket.common.codes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FeedbackIndexTest {

    @Test
    fun `test fromCode returns correct enum`() {
        assertEquals(FeedbackIndex.A_CIRCLE, FeedbackIndex.fromCode("0"))
        assertEquals(FeedbackIndex.A_TRIANGLE, FeedbackIndex.fromCode("1"))
        assertEquals(FeedbackIndex.A_SQUARE, FeedbackIndex.fromCode("2"))
        assertEquals(FeedbackIndex.B_CIRCLE, FeedbackIndex.fromCode("5"))
        assertEquals(FeedbackIndex.B_HEXAGON, FeedbackIndex.fromCode("9"))
    }

    @Test
    fun `test fromCode returns null for invalid code`() {
        assertNull(FeedbackIndex.fromCode("99"))
        assertNull(FeedbackIndex.fromCode("invalid"))
        assertNull(FeedbackIndex.fromCode("feedback-0"))
    }

    @Test
    fun `test all entries have unique codes`() {
        val codes = FeedbackIndex.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `test code format is numeric`() {
        FeedbackIndex.entries.forEach { feedback ->
            assertTrue(feedback.code.all { it.isDigit() })
            assertTrue(feedback.code.toInt() in 0..9)
        }
    }
}
