package cn.sweetberry.codes.dglab.websocket.common.codes

/**
 * 错误码枚举
 *
 * 定义 DGLab 协议中使用的所有错误码。
 * 错误码分为以下类别：
 * - 2xx: 成功/信息
 * - 4xx: 客户端错误
 * - 5xx: 服务器内部错误
 *
 * @property code 错误码字符串
 * @property message 错误描述（中文）
 */
enum class Error(val code: String, val message: String) {
    SUCCESS("200", "成功"),
    CLIENT_OFFLINE("209", "对方客户端已断开"),
    NO_VALID_CLIENT_ID("210", "二维码中没有有效的 clientID"),
    SOCKET_CONNECTED_NO_APP_ID("211", "socket 连接上了，但服务器迟迟不下发 app 端的 id 来绑定"),

    ID_ALREADY_BOUND("400", "此 id 已被其他客户端绑定关系"),
    CLIENT_NOT_EXIST("401", "要绑定的目标客户端不存在"),
    NOT_BOUND("402", "收信方和寄信方不是绑定关系"),
    INVALID_JSON("403", "发送的内容不是标准 json 对象"),
    RECEIVER_NOT_FOUND("404", "未找到收信人（离线）"),
    MESSAGE_TOO_LONG("405", "下发的 message 长度大于 1950"),

    INTERNAL_ERROR("500", "服务器内部异常");

    companion object {
        fun fromCode(code: String): Error =
            entries.firstOrNull { it.code == code } ?: INTERNAL_ERROR
    }
}
