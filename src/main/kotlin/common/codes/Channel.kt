package cn.sweetberry.codes.dglab.websocket.common.codes

enum class Channel(val numberCode: String, val letterCode: String) {
    CHANNEL_A("1","A"),
    CHANNEL_B("2","B");

    companion object {
        fun fromNumber(code: String): Channel? =
            entries.firstOrNull { it.numberCode == code }

        fun fromLetter(code: String): Channel? =
            entries.firstOrNull { it.letterCode == code }
    }
}