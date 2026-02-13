package cn.sweetberry.codes.dglab.websocket.common.codes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class StrengthSettingModeTest {

    @Test
    fun `test fromCode returns correct enum`() {
        assertEquals(StrengthSettingMode.DECREASE, StrengthSettingMode.fromCode("0"))
        assertEquals(StrengthSettingMode.INCREASE, StrengthSettingMode.fromCode("1"))
        assertEquals(StrengthSettingMode.SET_TO, StrengthSettingMode.fromCode("2"))
    }

    @Test
    fun `test fromCode returns null for invalid code`() {
        assertNull(StrengthSettingMode.fromCode("3"))
        assertNull(StrengthSettingMode.fromCode("invalid"))
        assertNull(StrengthSettingMode.fromCode(""))
    }

    @Test
    fun `test code values`() {
        assertEquals("0", StrengthSettingMode.DECREASE.code)
        assertEquals("1", StrengthSettingMode.INCREASE.code)
        assertEquals("2", StrengthSettingMode.SET_TO.code)
    }

    @Test
    fun `test officialCode values`() {
        assertEquals("1", StrengthSettingMode.DECREASE.officialCode)
        assertEquals("2", StrengthSettingMode.INCREASE.officialCode)
        assertEquals("4", StrengthSettingMode.SET_TO.officialCode)
    }

    @Test
    fun `test all entries have unique code`() {
        val codes = StrengthSettingMode.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `test all entries have unique officialCode`() {
        val officialCodes = StrengthSettingMode.entries.map { it.officialCode }
        assertEquals(officialCodes.size, officialCodes.toSet().size)
    }

    @Test
    fun `test code is numeric string`() {
        StrengthSettingMode.entries.forEach { mode ->
            assertTrue(mode.code.all { it.isDigit() })
        }
    }

    @Test
    fun `test officialCode is numeric string`() {
        StrengthSettingMode.entries.forEach { mode ->
            assertTrue(mode.officialCode.all { it.isDigit() })
        }
    }
}
