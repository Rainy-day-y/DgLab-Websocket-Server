package cn.sweetberry.codes.dglab.websocket.common

import cn.sweetberry.codes.dglab.websocket.common.codes.Channel
import cn.sweetberry.codes.dglab.websocket.common.codes.Error
import cn.sweetberry.codes.dglab.websocket.common.codes.FeedbackIndex
import cn.sweetberry.codes.dglab.websocket.common.codes.StrengthSettingMode
import cn.sweetberry.codes.dglab.websocket.common.data.ChannelStrengthLimit
import cn.sweetberry.codes.dglab.websocket.common.data.PulseData
import kotlinx.serialization.Serializable
import java.util.*

/**
 * DGLab 协议消息载体
 *
 * 所有 WebSocket 消息都使用此数据类进行序列化和反序列化。
 * 遵循 DGLab 官方 WebSocket 通信协议。
 *
 * 消息结构：
 * - type: 消息类型（bind/msg/break/heartbeat/error）
 * - clientId: 发送方/客户端 UUID
 * - targetId: 接收方/目标 UUID
 * - message: 消息内容（不同类型消息有不同格式）
 *
 * 使用示例：
 * ```
 * // 解析收到的消息
 * val payload = Json.decodeFromString<Payload>(messageString)
 *
 * // 创建绑定请求
 * val bindPayload = Payload.bindAttempt(clientId, targetId)
 *
 * // 创建强度设置命令
 * val strengthPayload = Payload.setStrength(
 *     clientId, targetId, Channel.CHANNEL_A, StrengthSettingMode.INCREASE, 5
 * )
 * ```
 *
 * @see <a href="https://github.com/DG-LAB-OPENSOURCE/DG-LAB-OPENSOURCE/blob/main/socket/README.md">官方协议文档</a>
 */
@Serializable
data class Payload(
    val type: String, val clientId: String, val targetId: String, val message: String
) {
    /**
     * 将消息解析为通道强度限制
     *
     * 解析 message 字段中包含的强度同步信息。
     * 消息格式：strength-{aStrength}+{bStrength}+{aLimit}+{bLimit}
     *
     * @return ChannelStrengthLimit 对象，解析失败返回 null
     * @see ChannelStrengthLimit
     */
    fun toChannelStrengthLimit(): ChannelStrengthLimit? {
        val splitStrings = message.split("-")

        val commandType = splitStrings[0]
        val dataStrings = splitStrings[1].split("+")

        if (commandType != "strength" || dataStrings.size != 4) return null

        val aStrength = dataStrings[0].toShortOrNull() ?: 0
        val bStrength = dataStrings[1].toShortOrNull() ?: 0
        val aLimit = dataStrings[2].toShortOrNull() ?: 0
        val bLimit = dataStrings[3].toShortOrNull() ?: 0

        val strengthMap = mapOf(Channel.CHANNEL_A to aStrength, Channel.CHANNEL_B to bStrength)
        val limitMap = mapOf(Channel.CHANNEL_A to aLimit, Channel.CHANNEL_B to bLimit)

        return ChannelStrengthLimit(strengthMap,limitMap)
    }

    /**
     * 将消息解析为反馈索引
     *
     * 解析 message 字段中包含的 APP 反馈按钮信息。
     * 消息格式：feedback-{code}
     *
     * @return FeedbackIndex 枚举值，解析失败返回 null
     * @see FeedbackIndex
     */
    fun toFeedbackIndexCode(): FeedbackIndex? {
        val commandType = message.split("-")[0]
        val codeString = message.split("-")[1]
        if (commandType != "feedback") return null
        return FeedbackIndex.fromCode(codeString)
    }

    companion object {

        /**
         * 创建连接确认消息
         *
         * 当新终端连接成功时，服务器发送此消息分配 UUID。
         *
         * @param uuid 分配给新终端的 UUID
         * @return 连接确认 Payload
         */
        fun connect(uuid: UUID) = Payload(
            type = "bind", clientId = uuid.toString(), message = "targetId", targetId = ""
        )

        /**
         * 创建绑定请求
         *
         * @param clientId 客户端 UUID
         * @param targetId 目标 UUID
         * @param msg 绑定消息
         * @return 绑定请求 Payload
         */
        fun bind(clientId: UUID, targetId: UUID, msg: String) = Payload(
            type = "bind", clientId = clientId.toString(), // 终端ID
            targetId = targetId.toString(), // APP ID
            message = msg
        )

        /**
         * 创建绑定尝试消息
         *
         * 标准格式的绑定请求，message 字段为 "DGLAB"
         *
         * @param clientId 客户端 UUID
         * @param targetId 目标 UUID
         * @return 绑定请求 Payload
         */
        fun bindAttempt(clientId: UUID, targetId: UUID) = bind(clientId, targetId, "DGLAB")

        /**
         * 创建绑定结果消息
         *
         * @param clientId 客户端 UUID
         * @param targetId 目标 UUID
         * @param error 错误/结果码
         * @return 绑定结果 Payload
         */
        fun bindResult(clientId: UUID, targetId: UUID, error: Error) = bind(
            clientId, // 终端ID
            targetId, // APP ID
            error.code
        )

        /**
         * 创建通用命令消息
         *
         * 用于发送任意命令数据的基本方法。
         *
         * @param clientId 发送方 UUID
         * @param targetId 接收方 UUID
         * @param message 命令内容
         * @return 命令 Payload
         */
        fun command(clientId: UUID, targetId: UUID, message: String) = Payload(
            type = "msg", clientId = clientId.toString(), targetId = targetId.toString(), message = message
        )

        /**
         * 创建强度同步消息
         *
         * 同步两个通道的当前强度和限制值。
         * 用于 APP 与终端之间的强度状态同步。
         *
         * @param clientId 发送方 UUID
         * @param targetId 接收方 UUID
         * @param aStrength A通道当前强度
         * @param aLimit A通道强度限制
         * @param bStrength B通道当前强度
         * @param bLimit B通道强度限制
         * @return 强度同步 Payload
         */
        fun syncStrength(
            clientId: UUID, targetId: UUID, aStrength: Short, aLimit: Short, bStrength: Short, bLimit: Short
        ): Payload {
            val message = "strength-$aStrength+$bStrength+$aLimit+$bLimit"
            return command(clientId, targetId, message)
        }

        /**
         * 创建强度设置命令
         *
         * 设置指定通道的强度值。
         * 支持三种模式：减少、增加、设为指定值
         *
         * @param clientId 发送方 UUID
         * @param targetId 接收方 UUID
         * @param targetChannel 目标通道
         * @param settingMode 设置模式
         * @param targetStrength 目标强度值
         * @return 强度设置 Payload
         * @see StrengthSettingMode
         * @see Channel
         */
        fun setStrength(
            clientId: UUID,
            targetId: UUID,
            targetChannel: Channel,
            settingMode: StrengthSettingMode,
            targetStrength: Short
        ): Payload {
            val message = "strength-${targetChannel.numberCode}+${settingMode.code}+$targetStrength"
            return command(clientId, targetId, message)
        }

        /**
         * 创建脉冲/波形发送命令
         *
         * 向指定通道发送脉冲波形数据。
         * 波形数据由 16 进制字符串列表组成，每条代表一个波形单元。
         *
         * @param clientId 发送方 UUID
         * @param targetId 接收方 UUID
         * @param channel 目标通道
         * @param pulseData 脉冲数据
         * @return 脉冲发送 Payload
         * @see PulseData
         * @see Channel
         */
        fun sendPulse(
            clientId: UUID, targetId: UUID, channel: Channel, pulseData: PulseData
        ): Payload {
            val message = "pulse-${channel.letterCode}:${pulseData.toJson()}"
            return command(clientId, targetId, message)
        }

        /**
         * 创建清空队列命令
         *
         * 清空指定通道的波形队列，停止该通道的刺激输出。
         *
         * @param clientId 发送方 UUID
         * @param targetId 接收方 UUID
         * @param channel 要清空的通道
         * @return 清空队列 Payload
         */
        fun clearPulse(clientId: UUID, targetId: UUID, channel: Channel) = command(
            clientId, targetId, "clear-${channel.numberCode}"
        )

        /**
         * 创建反馈命令
         *
         * 从 APP 端发送的按钮反馈信息。
         * 表示用户按下了某个通道的某个按钮。
         *
         * @param clientId 发送方 UUID（APP）
         * @param targetId 接收方 UUID（终端）
         * @param feedbackIndex 反馈按钮索引
         * @return 反馈 Payload
         * @see FeedbackIndex
         */
        fun feedback(clientId: UUID, targetId: UUID, feedbackIndex: FeedbackIndex) = command(
            clientId, targetId, "feedback-${feedbackIndex.code}"
        )

        /**
         * 创建心跳包
         *
         * 用于维持连接活跃。服务器定期向所有终端发送心跳包。
         *
         * @param clientId 发送方 UUID
         * @param targetId 接收方 UUID（可选）
         * @param message 心跳消息内容，默认 "200"
         * @return 心跳 Payload
         */
        fun heartbeat(clientId: UUID, targetId: UUID? = null, message: String = "200") = Payload(
            type = "heartbeat",
            clientId = clientId.toString(),
            targetId = targetId?.toString() ?: "",
            message = message
        )

        /**
         * 创建断开连接通知
         *
         * 通知对端连接已断开。
         *
         * @param clientId 断开方 UUID
         * @param targetId 被通知方 UUID
         * @param errorCode 断开原因错误码
         * @return 断开连接 Payload
         */
        fun close(clientId: UUID, targetId: UUID, errorCode: Error) = Payload(
            type = "break",
            clientId = clientId.toString(),
            targetId = targetId.toString(),
            message = errorCode.code
        )

        /**
         * 创建错误消息
         *
         * 发送错误信息给对端。
         *
         * @param clientId 发送方 UUID
         * @param targetId 接收方 UUID
         * @param errorCode 错误码
         * @return 错误 Payload
         */
        fun error(clientId: UUID, targetId: UUID, errorCode: Error) = Payload(
            "error",
            clientId = clientId.toString(),
            targetId = targetId.toString(),
            message = errorCode.code
        )
    }
}