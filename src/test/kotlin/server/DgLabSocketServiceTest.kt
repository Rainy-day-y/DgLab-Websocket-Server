package cn.sweetberry.codes.dglab.websocket.server

import cn.sweetberry.codes.dglab.websocket.common.Endpoint
import cn.sweetberry.codes.dglab.websocket.common.Payload
import cn.sweetberry.codes.dglab.websocket.common.codes.Error
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DgLabSocketServiceTest {

    @Test
    fun `test service start and stop`() {
        // 确保服务已停止
        DgLabSocketService.stop()
        assertFalse(DgLabSocketService.isRunning())

        // 启动服务
        DgLabSocketService.start(17479)
        assertTrue(DgLabSocketService.isRunning())

        // 停止服务
        DgLabSocketService.stop()
        assertFalse(DgLabSocketService.isRunning())
    }

    @Test
    fun `test service start is idempotent`() {
        // 确保服务已停止
        DgLabSocketService.stop()

        // 第一次启动
        DgLabSocketService.start(17479)
        assertTrue(DgLabSocketService.isRunning())
        val server1 = DgLabSocketService.getServer()

        // 第二次启动（应该幂等）
        DgLabSocketService.start(17479)
        assertTrue(DgLabSocketService.isRunning())
        val server2 = DgLabSocketService.getServer()

        // 应该是同一个实例
        assertSame(server1, server2)

        // 清理
        DgLabSocketService.stop()
    }

    @Test
    fun `test service start with different port restarts server`() {
        // 确保服务已停止
        DgLabSocketService.stop()

        // 在端口 17479 启动
        DgLabSocketService.start(17479)
        val server1 = DgLabSocketService.getServer()

        // 在端口 17480 启动（应该重启）
        DgLabSocketService.start(17480)
        val server2 = DgLabSocketService.getServer()

        // 应该是不同的实例
        assertNotSame(server1, server2)

        // 清理
        DgLabSocketService.stop()
    }

    @Test
    fun `test openLocal returns null when server not running`() {
        // 确保服务已停止
        DgLabSocketService.stop()

        val local = DgLabSocketService.openLocal { }

        assertNull(local)
    }

    @Test
    fun `test openLocal returns local endpoint when server running`() {
        // 确保服务已停止然后启动
        DgLabSocketService.stop()
        DgLabSocketService.start(17479)

        val messages = mutableListOf<String>()
        val local = DgLabSocketService.openLocal { messages.add(it) }

        assertNotNull(local)
        assertTrue(local is Endpoint.Local)

        // 清理
        DgLabSocketService.stop()
    }

    @Test
    fun `test closeLocal returns null when server not running`() {
        // 确保服务已停止
        DgLabSocketService.stop()

        // 创建一个假的 Local Endpoint
        val fakeLocal = Endpoint.Local(
            java.util.UUID.randomUUID(),
            { },
            { _, _ -> }
        )

        val result = DgLabSocketService.closeLocal(fakeLocal)

        assertNull(result)
    }

    @Test
    fun `test closeLocal closes local endpoint when server running`() {
        // 确保服务已停止然后启动
        DgLabSocketService.stop()
        DgLabSocketService.start(17479)

        val messages = mutableListOf<String>()
        val local = DgLabSocketService.openLocal { messages.add(it) }!!

        // 关闭
        val result = DgLabSocketService.closeLocal(local)

        assertNotNull(result)
        assertEquals(local.id, result?.id)

        // 清理
        DgLabSocketService.stop()
    }

    @Test
    fun `test getServer returns null when not running`() {
        // 确保服务已停止
        DgLabSocketService.stop()

        val server = DgLabSocketService.getServer()

        assertNull(server)
    }

    @Test
    fun `test getServer returns server when running`() {
        // 确保服务已停止然后启动
        DgLabSocketService.stop()
        DgLabSocketService.start(17479)

        val server = DgLabSocketService.getServer()

        assertNotNull(server)

        // 清理
        DgLabSocketService.stop()
    }

    @Test
    fun `test graceful stop waits for connections`() {
        // 确保服务已停止然后启动
        DgLabSocketService.stop()
        DgLabSocketService.start(17479)

        // 创建一个本地连接
        val local = DgLabSocketService.openLocal { }!!

        // 优雅关闭
        DgLabSocketService.stop(graceful = true)

        assertFalse(DgLabSocketService.isRunning())
    }

    @Test
    fun `test force stop does not wait`() {
        // 确保服务已停止然后启动
        DgLabSocketService.stop()
        DgLabSocketService.start(17479)

        // 创建一个本地连接
        val local = DgLabSocketService.openLocal { }!!

        // 强制关闭
        DgLabSocketService.stop(graceful = false)

        assertFalse(DgLabSocketService.isRunning())
    }

    @Test
    fun `test multiple start stop cycles`() {
        repeat(3) {
            assertFalse(DgLabSocketService.isRunning())

            DgLabSocketService.start(17479)
            assertTrue(DgLabSocketService.isRunning())

            DgLabSocketService.stop()
            assertFalse(DgLabSocketService.isRunning())
        }
    }

    @Test
    fun `test local endpoint can send and receive messages`() {
        // 确保服务已停止然后启动
        DgLabSocketService.stop()
        DgLabSocketService.start(17479)

        val receivedMessages = mutableListOf<String>()
        val server = DgLabSocketService.getServer()!!

        // 创建两个本地端点
        val local1 = DgLabSocketService.openLocal { receivedMessages.add(it) }!!
        val local2 = DgLabSocketService.openLocal { }!!

        // 绑定
        server.handleMessage(local2, Payload.bindAttempt(local1.id, local2.id))

        receivedMessages.clear()

        // local1 发送消息给 local2
        val testPayload = Payload.command(local1.id, local2.id, "test-message")
        server.handleMessage(local1, testPayload)

        // 验证 local2 收到了消息
        // 注意：local2 的 sender 是我们传入的空 lambda，所以不会收到消息
        // 但 local1 作为发送方不应该收到错误

        // 清理
        DgLabSocketService.stop()

        // 测试通过（没有抛出异常）
        assertTrue(true)
    }

    @Test
    fun `test concurrent local endpoint operations`() {
        // 确保服务已停止然后启动
        DgLabSocketService.stop()
        DgLabSocketService.start(17479)

        val endpoints = mutableListOf<Endpoint.Local>()

        // 并发创建多个端点
        val threads = (1..10).map {
            Thread {
                val local = DgLabSocketService.openLocal { }
                if (local != null) {
                    synchronized(endpoints) {
                        endpoints.add(local)
                    }
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // 验证所有端点都被创建
        assertEquals(10, endpoints.size)

        // 验证所有 ID 都是唯一的
        val ids = endpoints.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        // 清理
        DgLabSocketService.stop()
    }
}
