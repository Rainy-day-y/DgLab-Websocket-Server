package cn.sweetberry.codes.dglab.websocket.common.data

import cn.sweetberry.codes.dglab.websocket.common.codes.Channel

/**
 * 通道强度与限制数据
 *
 * 存储两个通道（A 和 B）的当前强度值和强度上限。
 * 用于 APP 与终端之间的强度状态同步。
 *
 * 使用示例：
 * ```
 * val limit = ChannelStrengthLimit(
 *     strength = mapOf(Channel.CHANNEL_A to 50, Channel.CHANNEL_B to 30),
 *     limit = mapOf(Channel.CHANNEL_A to 100, Channel.CHANNEL_B to 80)
 * )
 *
 * // 从 Payload 解析
 * val parsed = payload.toChannelStrengthLimit()
 * ```
 *
 * @property strength 各通道的当前强度
 * @property limit 各通道的强度上限
 * @see Payload.toChannelStrengthLimit 解析方法
 * @see Payload.syncStrength 同步消息
 */
data class ChannelStrengthLimit(
    val strength: Map<Channel, Short>,
    val limit: Map<Channel, Short>
)