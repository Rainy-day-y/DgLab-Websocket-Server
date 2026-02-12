package cn.sweetberry.codes.dglab.websocket.common.data

import cn.sweetberry.codes.dglab.websocket.common.codes.Channel

data class ChannelStrengthLimit(
    val strength: Map<Channel, Short>,
    val limit: Map<Channel, Short>
)