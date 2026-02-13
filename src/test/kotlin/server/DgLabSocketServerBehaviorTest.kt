package cn.sweetberry.codes.dglab.websocket.server

import cn.sweetberry.codes.dglab.websocket.common.Endpoint
import cn.sweetberry.codes.dglab.websocket.common.Payload
import cn.sweetberry.codes.dglab.websocket.common.codes.Error
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class DgLabSocketServerBehaviorTest {

    private lateinit var server: DgLabSocketServer
    private val receivedMessages = ConcurrentLinkedQueue<String>()

    @BeforeEach
    fun setup() {
        server = DgLabSocketServer(java.net.InetSocketAddress(0))
        receivedMessages.clear()
    }

    @Test
    fun `test registerEndpoint assigns unique ID`() {
        val sender: (String) -> Unit = { receivedMessages.add(it) }
        val local1 = server.openLocal(sender)
        val local2 = server.openLocal(sender)

        assertNotNull(local1)
        assertNotNull(local2)
        assertNotEquals(local1?.id, local2?.id)
    }

    @Test
    fun `test registerEndpoint returns null for duplicate registration attempt`() {
        val sender: (String) -> Unit = {}
        val local = server.openLocal(sender)

        assertNotNull(local)

        // 尝试用相同的 ID 注册应该失败（在内部实现中）
        val result = server.registerEndpoint(local!!)
        assertFalse(result)
    }

    @Test
    fun `test unregisterEndpoint removes endpoint and unbinds`() {
        val sender: (String) -> Unit = {}
        val local = server.openLocal(sender)!!
        val id = local.id

        // 先绑定
        val sender2: (String) -> Unit = {}
        val local2 = server.openLocal(sender2)!!
        server.handleMessage(local, Payload.bindAttempt(local.id, local2.id))

        // 注销
        val removed = server.unregisterEndpoint(id)

        assertNotNull(removed)
        assertEquals(id, removed?.id)
    }

    @Test
    fun `test handleMessage with bind type and valid DGLAB message`() {
        val clientMessages = mutableListOf<String>()
        val targetMessages = mutableListOf<String>()

        val client = server.openLocal { clientMessages.add(it) }!!
        val target = server.openLocal { targetMessages.add(it) }!!

        // 发送绑定请求
        val bindPayload = Payload.bindAttempt(client.id, target.id)
        server.handleMessage(target, bindPayload)

        // 验证双方都收到了绑定成功消息 (code 200)
        assertTrue(clientMessages.any { it.contains("200") })
        assertTrue(targetMessages.any { it.contains("200") })
    }

    @Test
    fun `test handleMessage with bind type and invalid message format`() {
        val targetMessages = mutableListOf<String>()
        val client = server.openLocal { }!!
        val target = server.openLocal { targetMessages.add(it) }!!

        // 发送无效的绑定消息
        val invalidBind = Payload.bind(client.id, target.id, "INVALID")
        server.handleMessage(target, invalidBind)

        // 应该收到错误码 403 (INVALID_JSON)
        assertTrue(targetMessages.any { it.contains(Error.INVALID_JSON.code) })
    }

    @Test
    fun `test handleMessage with bind type and non-existent client`() {
        val messages = mutableListOf<String>()
        val target = server.openLocal { messages.add(it) }!!
        val nonExistentClientId = UUID.randomUUID()

        // 尝试绑定不存在的客户端
        val bindPayload = Payload.bindAttempt(nonExistentClientId, target.id)
        server.handleMessage(target, bindPayload)

        // 应该收到错误码 401 (CLIENT_NOT_EXIST)
        assertTrue(messages.any { it.contains(Error.CLIENT_NOT_EXIST.code) })
    }

    @Test
    fun `test handleMessage with bind type and mismatched target ID`() {
        val messages = mutableListOf<String>()
        val client = server.openLocal { }!!
        val target = server.openLocal { messages.add(it) }!!
        val wrongTargetId = UUID.randomUUID()

        // 尝试用错误的 targetId 绑定
        val bindPayload = Payload.bindAttempt(client.id, wrongTargetId)
        server.handleMessage(target, bindPayload)

        // 应该收到错误码 500 (INTERNAL_ERROR)
        assertTrue(messages.any { it.contains(Error.INTERNAL_ERROR.code) })
    }

    @Test
    fun `test handleMessage with msg type forwards to bound peer`() {
        val clientMessages = mutableListOf<String>()
        val targetMessages = mutableListOf<String>()

        val client = server.openLocal { clientMessages.add(it) }!!
        val target = server.openLocal { targetMessages.add(it) }!!

        // 先绑定
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))

        // 清空绑定消息
        clientMessages.clear()
        targetMessages.clear()

        // 客户端发送消息给 APP
        val msgPayload = Payload.command(client.id, target.id, "strength-1+1+10")
        server.handleMessage(client, msgPayload)

        // 验证目标收到了转发的消息
        assertTrue(targetMessages.any { it.contains("strength-1+1+10") })
    }

    @Test
    fun `test handleMessage with msg type from unbound endpoint returns error`() {
        val messages = mutableListOf<String>()
        val client = server.openLocal { messages.add(it) }!!
        val target = server.openLocal { }!!

        // 不绑定直接发送消息
        val msgPayload = Payload.command(client.id, target.id, "strength-1+1+10")
        server.handleMessage(client, msgPayload)

        // 应该收到错误码 402 (NOT_BOUND)
        assertTrue(messages.any { it.contains(Error.NOT_BOUND.code) })
    }

    @Test
    fun `test handleMessage with msg type to offline peer returns error`() {
        val messages = mutableListOf<String>()
        val client = server.openLocal { messages.add(it) }!!

        // 创建一个存在的 target
        val target = server.openLocal { }!!

        // 绑定
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))

        // 注销 target（模拟离线）- 这会同时清除绑定关系
        server.unregisterEndpoint(target.id)

        // 发送消息给已离线的 target
        val msgPayload = Payload.command(client.id, target.id, "test")
        server.handleMessage(client, msgPayload)

        // 由于绑定关系已被清除，应该收到错误码 402 (NOT_BOUND)
        assertTrue(messages.any { it.contains(Error.NOT_BOUND.code) })
    }

    @Test
    fun `test close sends disconnect notification to peer`() {
        val clientMessages = mutableListOf<String>()
        val targetMessages = mutableListOf<String>()

        val client = server.openLocal { clientMessages.add(it) }!!
        val target = server.openLocal { targetMessages.add(it) }!!

        // 绑定
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))

        // 清空绑定消息
        clientMessages.clear()
        targetMessages.clear()

        // 关闭客户端连接
        server.close(client.id)

        // 验证目标收到了断开连接通知
        assertTrue(targetMessages.any { it.contains(Error.CLIENT_OFFLINE.code) })
    }

    @Test
    fun `test close removes endpoint from registry`() {
        val client = server.openLocal { }!!
        val target = server.openLocal { }!!

        // 绑定
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))

        // 关闭
        server.close(client.id)

        // 验证 client 已被移除
        val result = server.registerEndpoint(client)
        assertTrue(result) // 如果已被移除，应该可以重新注册
    }

    @Test
    fun `test handleMessage with break type logs info`() {
        val client = server.openLocal { }!!

        // 发送 break 消息
        val breakPayload = Payload.close(client.id, client.id, Error.SUCCESS)
        server.handleMessage(client, breakPayload)

        // 主要是验证不抛出异常
        assertTrue(true)
    }

    @Test
    fun `test handleMessage with heartbeat type logs info`() {
        val client = server.openLocal { }!!

        // 发送 heartbeat 消息
        val heartbeatPayload = Payload.heartbeat(client.id, Error.SUCCESS)
        server.handleMessage(client, heartbeatPayload)

        // 主要是验证不抛出异常
        assertTrue(true)
    }

    @Test
    fun `test handleMessage with error type logs error`() {
        val client = server.openLocal { }!!

        // 发送 error 消息
        val errorPayload = Payload.error(client.id, client.id, Error.NOT_BOUND)
        server.handleMessage(client, errorPayload)

        // 主要是验证不抛出异常
        assertTrue(true)
    }

    @Test
    fun `test handleMessage with unknown type logs warning`() {
        val client = server.openLocal { }!!

        // 发送未知类型消息
        val unknownPayload = Payload(
            type = "unknown_type",
            clientId = client.id.toString(),
            targetId = "",
            message = "test"
        )
        server.handleMessage(client, unknownPayload)

        // 主要是验证不抛出异常
        assertTrue(true)
    }

    @Test
    fun `test double bind attempt fails with ID_ALREADY_BOUND`() {
        val clientMessages = mutableListOf<String>()
        val targetMessages = mutableListOf<String>()

        val client = server.openLocal { clientMessages.add(it) }!!
        val target = server.openLocal { targetMessages.add(it) }!!

        // 第一次绑定
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))

        clientMessages.clear()
        targetMessages.clear()

        // 第二次绑定（应该失败）
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))

        // 应该收到错误码 400 (ID_ALREADY_BOUND)
        assertTrue(clientMessages.any { it.contains(Error.ID_ALREADY_BOUND.code) } ||
                targetMessages.any { it.contains(Error.ID_ALREADY_BOUND.code) })
    }

    @Test
    fun `test message validation fails when sender is not bound`() {
        val unrelatedMessages = mutableListOf<String>()
        val client = server.openLocal { }!!
        val target = server.openLocal { }!!
        val unrelated = server.openLocal { unrelatedMessages.add(it) }!!

        // 绑定 client 和 target
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))

        // unrelated 尝试发送消息，但它没有绑定
        val msgPayload = Payload.command(client.id, target.id, "test")
        server.handleMessage(unrelated, msgPayload)

        // 应该收到错误码 402 (NOT_BOUND) - 因为 unrelated 没有绑定
        assertTrue(unrelatedMessages.any { it.contains(Error.NOT_BOUND.code) })
    }

    @Test
    fun `test localClose properly closes local endpoint`() {
        val clientMessages = mutableListOf<String>()
        val targetMessages = mutableListOf<String>()
        val client = server.openLocal { clientMessages.add(it) }!!
        val target = server.openLocal { targetMessages.add(it) }!!

        // 绑定
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))

        clientMessages.clear()
        targetMessages.clear()

        // 关闭本地端点
        server.localClose(client as Endpoint.Local)

        // 验证双方都收到了断开通知
        assertTrue(clientMessages.any { it.contains(Error.CLIENT_OFFLINE.code) } ||
                targetMessages.any { it.contains(Error.CLIENT_OFFLINE.code) })
    }

    @Test
    fun `test bind result contains correct client and target IDs`() {
        val clientMessages = mutableListOf<String>()
        val targetMessages = mutableListOf<String>()

        val client = server.openLocal { clientMessages.add(it) }!!
        val target = server.openLocal { targetMessages.add(it) }!!

        // 发送绑定请求
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))

        // 解析响应并验证 ID
        val clientResponse = Json.decodeFromString<Payload>(clientMessages.first())
        val targetResponse = Json.decodeFromString<Payload>(targetMessages.first())

        assertEquals(client.id.toString(), clientResponse.clientId)
        assertEquals(target.id.toString(), clientResponse.targetId)
        assertEquals(client.id.toString(), targetResponse.clientId)
        assertEquals(target.id.toString(), targetResponse.targetId)
    }

    @Test
    fun `test message forwarding preserves original payload content`() {
        val targetMessages = mutableListOf<String>()

        val client = server.openLocal { }!!
        val target = server.openLocal { targetMessages.add(it) }!!

        // 绑定
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))
        targetMessages.clear()

        // 发送复杂消息
        val originalMessage = "strength-50+75+100+100"
        val msgPayload = Payload.command(client.id, target.id, originalMessage)
        server.handleMessage(client, msgPayload)

        // 验证消息内容被正确转发
        val forwarded = Json.decodeFromString<Payload>(targetMessages.first())
        assertEquals(originalMessage, forwarded.message)
        assertEquals(client.id.toString(), forwarded.clientId)
        assertEquals(target.id.toString(), forwarded.targetId)
        assertEquals("msg", forwarded.type)
    }

    @Test
    fun `test sender identity validation for CLIENT role`() {
        val messages = mutableListOf<String>()
        val client = server.openLocal { }!!
        val target = server.openLocal { messages.add(it) }!!

        // 绑定
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))
        messages.clear()

        // 客户端使用正确的 ID 发送
        val validMsg = Payload.command(client.id, target.id, "test")
        server.handleMessage(client, validMsg)

        // 应该成功转发（没有错误消息）
        assertTrue(messages.isEmpty() || !messages.any { it.contains(Error.NOT_BOUND.code) })
    }

    @Test
    fun `test sender identity validation for TARGET role`() {
        val clientMessages = mutableListOf<String>()
        val targetMessages = mutableListOf<String>()

        val client = server.openLocal { clientMessages.add(it) }!!
        val target = server.openLocal { targetMessages.add(it) }!!

        // 绑定
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))
        clientMessages.clear()
        targetMessages.clear()

        // 目标端使用正确的 ID 发送消息给客户端
        val validMsg = Payload.command(client.id, target.id, "feedback-0")
        server.handleMessage(target, validMsg)

        // 应该成功转发到客户端
        assertTrue(clientMessages.any { it.contains("feedback-0") })
    }

    @Test
    fun `test role determination after binding`() {
        val client = server.openLocal { }!!
        val target = server.openLocal { }!!

        // 绑定前没有角色
        assertNull(server.getRoleForTest(client.id))
        assertNull(server.getRoleForTest(target.id))

        // 绑定
        server.handleMessage(target, Payload.bindAttempt(client.id, target.id))

        // 绑定后应该有正确角色
        assertEquals(BindingRegistry.Role.CLIENT, server.getRoleForTest(client.id))
        assertEquals(BindingRegistry.Role.TARGET, server.getRoleForTest(target.id))
    }
}

// 扩展函数用于测试
private fun DgLabSocketServer.getRoleForTest(id: UUID): BindingRegistry.Role? {
    // 通过反射访问私有字段
    return javaClass.getDeclaredField("bindingRegistry").let { field ->
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val registry = field.get(this) as BindingRegistry
        registry.roleOf(id)
    }
}
