package cn.sweetberry.codes.dglab.websocket.common.data

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PulseDataTest {

    @Test
    fun `test empty creates empty pulse data`() {
        val pulseData = PulseData.empty()
        
        assertEquals(0, pulseData.size())
    }

    @Test
    fun `test fromHexData creates pulse data with valid hex`() {
        val hexes = listOf(
            "0123456789abcdef",
            "fedcba9876543210"
        )
        
        val pulseData = PulseData.fromHexData(hexes)
        
        assertEquals(2, pulseData.size())
    }

    @Test
    fun `test fromHexData normalizes to lowercase`() {
        val hexes = listOf("ABCDEF1234567890")
        
        val pulseData = PulseData.fromHexData(hexes)
        val json = pulseData.toJson()
        
        assertTrue(json.contains("abcdef1234567890"))
    }

    @Test
    fun `test fromHexData throws on invalid length`() {
        val hexes = listOf("0123456789abc") // 13 chars, should be 16
        
        assertThrows(IllegalArgumentException::class.java) {
            PulseData.fromHexData(hexes)
        }
    }

    @Test
    fun `test fromHexData throws on invalid hex characters`() {
        val hexes = listOf("0123456789abcggh") // 'g' and 'h' are invalid
        
        assertThrows(IllegalArgumentException::class.java) {
            PulseData.fromHexData(hexes)
        }
    }

    @Test
    fun `test addWaveUnit adds valid hex`() {
        val pulseData = PulseData.empty()
        
        pulseData.addWaveUnit("0123456789abcdef")
        
        assertEquals(1, pulseData.size())
    }

    @Test
    fun `test addWaveUnit normalizes to lowercase`() {
        val pulseData = PulseData.empty()
        
        pulseData.addWaveUnit("ABCDEF1234567890")
        val json = pulseData.toJson()
        
        assertTrue(json.contains("abcdef1234567890"))
    }

    @Test
    fun `test addWaveUnit throws on invalid length`() {
        val pulseData = PulseData.empty()
        
        assertThrows(IllegalArgumentException::class.java) {
            pulseData.addWaveUnit("0123456789abc")
        }
    }

    @Test
    fun `test addWaveUnits adds multiple hex strings`() {
        val pulseData = PulseData.empty()
        val hexes = listOf(
            "0123456789abcdef",
            "fedcba9876543210",
            "aabbccdd11223344"
        )
        
        pulseData.addWaveUnits(hexes)
        
        assertEquals(3, pulseData.size())
    }

    @Test
    fun `test toJson returns valid json array`() {
        val hexes = listOf("0123456789abcdef", "fedcba9876543210")
        val pulseData = PulseData.fromHexData(hexes)
        
        val json = pulseData.toJson()
        
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("0123456789abcdef"))
        assertTrue(json.contains("fedcba9876543210"))
    }

    @Test
    fun `test toJson returns empty array for empty data`() {
        val pulseData = PulseData.empty()
        
        val json = pulseData.toJson()
        
        assertEquals("[]", json)
    }

    @Test
    fun `test pulse data with maximum length array`() {
        // 协议: 数组最大长度为 100
        val hexes = List(100) { "0123456789abcdef" }
        
        val pulseData = PulseData.fromHexData(hexes)
        
        assertEquals(100, pulseData.size())
    }

    @Test
    fun `test single pulse data represents 100ms`() {
        // 协议: 每条波形数据代表了 100ms 的数据
        val pulseData = PulseData.fromHexData(listOf("0123456789abcdef"))
        
        assertEquals(1, pulseData.size())
        // 1 条 = 100ms, 10 条 = 1s
        val tenPulses = PulseData.fromHexData(List(10) { "0123456789abcdef" })
        assertEquals(10, tenPulses.size())
    }
}
