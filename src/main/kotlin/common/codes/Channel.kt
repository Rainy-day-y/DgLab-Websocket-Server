package cn.sweetberry.codes.dglab.websocket.common.codes

/**
 * 通道枚举
 *
 * 表示 DGLab 设备的两个刺激通道：A 通道和 B 通道。
 *
 * 每个通道都有两种编码格式：
 * - numberCode: 数字编码（"1" 或 "2"），用于协议消息
 * - letterCode: 字母编码（"A" 或 "B"），用于波形消息
 *
 * @property numberCode 数字编码
 * @property letterCode 字母编码
 */
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