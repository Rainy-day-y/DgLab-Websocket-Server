package cn.sweetberry.codes.dglab.websocket.common.codes

enum class StrengthSettingMode(val code: String, val officialCode: String) {
    DECREASE("0","1"),   // 通道强度减少
    INCREASE("1","2"),   // 通道强度增加
    SET_TO("2","4");     // 通道强度变化为指定数值

    companion object {
        fun fromCode(code: String): StrengthSettingMode? =
            entries.firstOrNull { it.code == code }
    }
}
