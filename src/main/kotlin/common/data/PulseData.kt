package cn.sweetberry.codes.dglab.websocket.common.data

import kotlinx.serialization.json.Json

class PulseData private constructor(
    private val data: MutableList<String>
) {

    fun addWaveUnit(hex: String) {
        data.add(validateAndNormalize(hex))
    }

    fun addWaveUnits(hexes: List<String>) {
        hexes.forEach { addWaveUnit(it) }
    }

    fun toJson(): String =
        Json.encodeToString(data)

    companion object {
        fun empty(): PulseData =
            PulseData(mutableListOf())

        fun fromHexData(hexes: List<String>): PulseData =
            PulseData(mutableListOf<String>().apply {
                hexes.forEach { add(validateAndNormalize(it)) }
            })

        private fun validateAndNormalize(hex: String): String {
            require(hex.length == 16) {
                "Wave string must be 16 hex characters (8 bytes)"
            }
            require(hex.all { it in "0123456789abcdefABCDEF" }) {
                "Wave string must be a valid hex string"
            }
            return hex.lowercase()
        }
    }

    fun size(): Int {
        return data.size
    }
}