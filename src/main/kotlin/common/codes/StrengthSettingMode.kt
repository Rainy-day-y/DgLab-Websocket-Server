package cn.sweetberry.codes.dglab.websocket.common.codes

/**
 * 强度设置模式
 *
 * 定义设置通道强度时的三种操作模式：
 * - DECREASE: 减少当前强度
 * - INCREASE: 增加当前强度
 * - SET_TO: 设置为指定值
 *
 * @property code 内部使用代码
 * @property officialCode 官方协议代码
 */
enum class StrengthSettingMode(val code: String, val officialCode: String) {
    DECREASE("0","1"),   // 通道强度减少
    INCREASE("1","2"),   // 通道强度增加
    SET_TO("2","4");     // 通道强度变化为指定数值

    companion object {
        fun fromCode(code: String): StrengthSettingMode? =
            entries.firstOrNull { it.code == code }
    }
}
