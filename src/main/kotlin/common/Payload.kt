package cn.sweetberry.codes.dglab.websocket.common

import cn.sweetberry.codes.dglab.websocket.common.codes.Channel
import cn.sweetberry.codes.dglab.websocket.common.codes.Error
import cn.sweetberry.codes.dglab.websocket.common.codes.FeedbackIndex
import cn.sweetberry.codes.dglab.websocket.common.codes.StrengthSettingMode
import cn.sweetberry.codes.dglab.websocket.common.data.ChannelStrengthLimit
import cn.sweetberry.codes.dglab.websocket.common.data.PulseData
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Payload(
    val type: String, val clientId: String, val targetId: String, val message: String
) {
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

    fun toFeedbackIndexCode(): FeedbackIndex? {
        val commandType = message.split("-")[0]
        val codeString = message.split("-")[1]
        if (commandType != "feedback") return null
        return FeedbackIndex.fromCode(codeString)
    }

    companion object {

        fun connect(uuid: UUID) = Payload(
            type = "bind", clientId = uuid.toString(), message = "targetId", targetId = ""
        )

        fun bind(clientId: UUID, targetId: UUID, msg: String) = Payload(
            type = "bind", clientId = clientId.toString(), // 终端ID
            targetId = targetId.toString(), // APP ID
            message = msg
        )

        fun bindAttempt(clientId: UUID, targetId: UUID) = bind(clientId, targetId, "DGLAB")

        fun bindResult(clientId: UUID, targetId: UUID, error: Error) = bind(
            clientId, // 终端ID
            targetId, // APP ID
            error.code
        )

        fun command(clientId: UUID, targetId: UUID, message: String) = Payload(
            type = "msg", clientId = clientId.toString(), targetId = targetId.toString(), message = message
        )

        fun syncStrength(
            clientId: UUID, targetId: UUID, aStrength: Short, aLimit: Short, bStrength: Short, bLimit: Short
        ): Payload {
            val message = "strength-$aStrength+$bStrength+$aLimit+$bLimit"
            return command(clientId, targetId, message)
        }

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

        fun sendPulse(
            clientId: UUID, targetId: UUID, channel: Channel, pulseData: PulseData
        ): Payload {
            val message = "pulse-${channel.letterCode}:${pulseData.toJson()}"
            return command(clientId, targetId, message)
        }

        fun clearPulse(clientId: UUID, targetId: UUID, channel: Channel) = command(
            clientId, targetId, "clear-${channel.numberCode}"
        )

        fun feedback(clientId: UUID, targetId: UUID, feedbackIndex: FeedbackIndex) = command(
            clientId, targetId, "feedback-${feedbackIndex.code}"
        )

        fun heartbeat(clientId: UUID, errorCode: Error) = Payload(
            type = "heartbeat",
            clientId = clientId.toString(),
            targetId = "",
            message = errorCode.code
        )

        fun close(clientId: UUID, targetId: UUID, errorCode: Error) = Payload(
            type = "break",
            clientId = clientId.toString(),
            targetId = targetId.toString(),
            message = errorCode.code
        )

        fun error(clientId: UUID, targetId: UUID, errorCode: Error) = Payload(
            "error",
            clientId = clientId.toString(),
            targetId = targetId.toString(),
            message = errorCode.code
        )
    }
}