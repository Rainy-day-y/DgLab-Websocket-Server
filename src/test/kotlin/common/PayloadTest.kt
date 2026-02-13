package cn.sweetberry.codes.dglab.websocket.common

import cn.sweetberry.codes.dglab.websocket.common.codes.Channel
import cn.sweetberry.codes.dglab.websocket.common.codes.Error
import cn.sweetberry.codes.dglab.websocket.common.codes.FeedbackIndex
import cn.sweetberry.codes.dglab.websocket.common.codes.StrengthSettingMode
import cn.sweetberry.codes.dglab.websocket.common.data.PulseData
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.*

class PayloadTest {

    @Test
    fun `test connect payload creation`() {
        val uuid = UUID.randomUUID()
        val payload = Payload.connect(uuid)
        
        assertEquals("bind", payload.type)
        assertEquals(uuid.toString(), payload.clientId)
        assertEquals("targetId", payload.message)
        assertEquals("", payload.targetId)
    }

    @Test
    fun `test bindAttempt payload creation`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val payload = Payload.bindAttempt(clientId, targetId)
        
        assertEquals("bind", payload.type)
        assertEquals(clientId.toString(), payload.clientId)
        assertEquals(targetId.toString(), payload.targetId)
        assertEquals("DGLAB", payload.message)
    }

    @Test
    fun `test bindResult payload creation`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val payload = Payload.bindResult(clientId, targetId, Error.SUCCESS)
        
        assertEquals("bind", payload.type)
        assertEquals(Error.SUCCESS.code, payload.message)
    }

    @Test
    fun `test command payload creation`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val message = "test-message"
        val payload = Payload.command(clientId, targetId, message)
        
        assertEquals("msg", payload.type)
        assertEquals(message, payload.message)
    }

    @Test
    fun `test syncStrength payload creation`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val payload = Payload.syncStrength(clientId, targetId, 10, 100, 20, 100)
        
        assertEquals("msg", payload.type)
        assertEquals("strength-10+20+100+100", payload.message)
    }

    @Test
    fun `test setStrength payload creation for channel A increase`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        // 协议: strength-通道+模式+数值, 通道:1-A, 模式:1-增加
        val payload = Payload.setStrength(
            clientId, targetId,
            Channel.CHANNEL_A,
            StrengthSettingMode.INCREASE,
            5
        )

        assertEquals("msg", payload.type)
        assertEquals("strength-1+1+5", payload.message)
    }

    @Test
    fun `test setStrength payload creation for channel B decrease`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        // 协议: strength-通道+模式+数值, 通道:2-B, 模式:0-减少
        val payload = Payload.setStrength(
            clientId, targetId,
            Channel.CHANNEL_B,
            StrengthSettingMode.DECREASE,
            20
        )

        assertEquals("msg", payload.type)
        assertEquals("strength-2+0+20", payload.message)
    }

    @Test
    fun `test setStrength payload creation for channel B set to zero`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        // 协议: strength-通道+模式+数值, 通道:2-B, 模式:2-指定值
        val payload = Payload.setStrength(
            clientId, targetId,
            Channel.CHANNEL_B,
            StrengthSettingMode.SET_TO,
            0
        )

        assertEquals("msg", payload.type)
        assertEquals("strength-2+2+0", payload.message)
    }

    @Test
    fun `test setStrength payload creation for channel A set to specific value`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        // 协议: strength-通道+模式+数值, 通道:1-A, 模式:2-指定值
        val payload = Payload.setStrength(
            clientId, targetId,
            Channel.CHANNEL_A,
            StrengthSettingMode.SET_TO,
            35
        )

        assertEquals("msg", payload.type)
        assertEquals("strength-1+2+35", payload.message)
    }

    @Test
    fun `test clearPulse payload creation`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val payload = Payload.clearPulse(clientId, targetId, Channel.CHANNEL_B)
        
        assertEquals("msg", payload.type)
        assertEquals("clear-2", payload.message)
    }

    @Test
    fun `test sendPulse payload creation`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val pulseData = PulseData.fromHexData(listOf("0123456789abcdef", "fedcba9876543210"))
        val payload = Payload.sendPulse(clientId, targetId, Channel.CHANNEL_A, pulseData)
        
        assertEquals("msg", payload.type)
        assertTrue(payload.message.startsWith("pulse-A:"))
        assertTrue(payload.message.contains("0123456789abcdef"))
        assertTrue(payload.message.contains("fedcba9876543210"))
    }

    @Test
    fun `test feedback payload creation`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val payload = Payload.feedback(clientId, targetId, FeedbackIndex.A_CIRCLE)
        
        assertEquals("msg", payload.type)
        assertEquals("feedback-0", payload.message)
    }

    @Test
    fun `test heartbeat payload creation`() {
        val clientId = UUID.randomUUID()
        val payload = Payload.heartbeat(clientId, Error.SUCCESS)
        
        assertEquals("heartbeat", payload.type)
        assertEquals(Error.SUCCESS.code, payload.message)
        assertEquals("", payload.targetId)
    }

    @Test
    fun `test close payload creation`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val payload = Payload.close(clientId, targetId, Error.CLIENT_OFFLINE)
        
        assertEquals("break", payload.type)
        assertEquals(Error.CLIENT_OFFLINE.code, payload.message)
    }

    @Test
    fun `test error payload creation`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val payload = Payload.error(clientId, targetId, Error.NOT_BOUND)
        
        assertEquals("error", payload.type)
        assertEquals(Error.NOT_BOUND.code, payload.message)
    }

    @Test
    fun `test toChannelStrengthLimit with valid message`() {
        val payload = Payload(
            type = "msg",
            clientId = "",
            targetId = "",
            message = "strength-10+20+100+100"
        )
        
        val result = payload.toChannelStrengthLimit()
        
        assertNotNull(result)
        assertEquals(10, result?.strength?.get(Channel.CHANNEL_A))
        assertEquals(20, result?.strength?.get(Channel.CHANNEL_B))
        assertEquals(100, result?.limit?.get(Channel.CHANNEL_A))
        assertEquals(100, result?.limit?.get(Channel.CHANNEL_B))
    }

    @Test
    fun `test toChannelStrengthLimit with invalid command type`() {
        val payload = Payload(
            type = "msg",
            clientId = "",
            targetId = "",
            message = "invalid-10+20+100+100"
        )
        
        val result = payload.toChannelStrengthLimit()
        
        assertNull(result)
    }

    @Test
    fun `test toChannelStrengthLimit with invalid data size`() {
        val payload = Payload(
            type = "msg",
            clientId = "",
            targetId = "",
            message = "strength-10+20"
        )
        
        val result = payload.toChannelStrengthLimit()
        
        assertNull(result)
    }

    @Test
    fun `test toChannelStrengthLimit with non-numeric values`() {
        val payload = Payload(
            type = "msg",
            clientId = "",
            targetId = "",
            message = "strength-abc+def+ghi+jkl"
        )
        
        val result = payload.toChannelStrengthLimit()
        
        assertNotNull(result)
        assertEquals(0, result?.strength?.get(Channel.CHANNEL_A))
        assertEquals(0, result?.strength?.get(Channel.CHANNEL_B))
    }

    @Test
    fun `test toChannelStrengthLimit with boundary values`() {
        // 协议规定值范围 0～200
        val payload = Payload(
            type = "msg",
            clientId = "",
            targetId = "",
            message = "strength-0+200+0+200"
        )
        
        val result = payload.toChannelStrengthLimit()
        
        assertNotNull(result)
        assertEquals(0, result?.strength?.get(Channel.CHANNEL_A))
        assertEquals(200, result?.strength?.get(Channel.CHANNEL_B))
        assertEquals(0, result?.limit?.get(Channel.CHANNEL_A))
        assertEquals(200, result?.limit?.get(Channel.CHANNEL_B))
    }

    @Test
    fun `test syncStrength generates correct message format`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        // 协议: strength-A通道强度+B通道强度+A强度上限+B强度上限
        val payload = Payload.syncStrength(clientId, targetId, 11, 100, 7, 35)
        
        assertEquals("msg", payload.type)
        // 验证格式: strength-11+7+100+35
        assertEquals("strength-11+7+100+35", payload.message)
    }

    @Test
    fun `test toFeedbackIndexCode with valid message`() {
        val payload = Payload(
            type = "msg",
            clientId = "",
            targetId = "",
            message = "feedback-0"
        )
        
        val result = payload.toFeedbackIndexCode()
        
        assertEquals(FeedbackIndex.A_CIRCLE, result)
    }

    @Test
    fun `test toFeedbackIndexCode with B channel index`() {
        // 协议: index: A通道 0,1,2,3,4; B通道 5,6,7,8,9
        val payload = Payload(
            type = "msg",
            clientId = "",
            targetId = "",
            message = "feedback-5"
        )
        
        val result = payload.toFeedbackIndexCode()
        
        assertEquals(FeedbackIndex.B_CIRCLE, result)
    }

    @Test
    fun `test toFeedbackIndexCode with all A channel indices`() {
        // A通道: 0,1,2,3,4
        assertEquals(FeedbackIndex.A_CIRCLE, Payload("msg", "", "", "feedback-0").toFeedbackIndexCode())
        assertEquals(FeedbackIndex.A_TRIANGLE, Payload("msg", "", "", "feedback-1").toFeedbackIndexCode())
        assertEquals(FeedbackIndex.A_SQUARE, Payload("msg", "", "", "feedback-2").toFeedbackIndexCode())
        assertEquals(FeedbackIndex.A_STAR, Payload("msg", "", "", "feedback-3").toFeedbackIndexCode())
        assertEquals(FeedbackIndex.A_HEXAGON, Payload("msg", "", "", "feedback-4").toFeedbackIndexCode())
    }

    @Test
    fun `test toFeedbackIndexCode with all B channel indices`() {
        // B通道: 5,6,7,8,9
        assertEquals(FeedbackIndex.B_CIRCLE, Payload("msg", "", "", "feedback-5").toFeedbackIndexCode())
        assertEquals(FeedbackIndex.B_TRIANGLE, Payload("msg", "", "", "feedback-6").toFeedbackIndexCode())
        assertEquals(FeedbackIndex.B_SQUARE, Payload("msg", "", "", "feedback-7").toFeedbackIndexCode())
        assertEquals(FeedbackIndex.B_STAR, Payload("msg", "", "", "feedback-8").toFeedbackIndexCode())
        assertEquals(FeedbackIndex.B_HEXAGON, Payload("msg", "", "", "feedback-9").toFeedbackIndexCode())
    }

    @Test
    fun `test toFeedbackIndexCode with invalid command type`() {
        val payload = Payload(
            type = "msg",
            clientId = "",
            targetId = "",
            message = "invalid-feedback-0"
        )
        
        val result = payload.toFeedbackIndexCode()
        
        assertNull(result)
    }

    @Test
    fun `test payload serialization and deserialization`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val payload = Payload.bindAttempt(clientId, targetId)
        
        val json = Json.encodeToString(payload)
        val decoded = Json.decodeFromString<Payload>(json)
        
        assertEquals(payload.type, decoded.type)
        assertEquals(payload.clientId, decoded.clientId)
        assertEquals(payload.targetId, decoded.targetId)
        assertEquals(payload.message, decoded.message)
    }
}
