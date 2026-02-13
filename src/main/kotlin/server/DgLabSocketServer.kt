package cn.sweetberry.codes.dglab.websocket.server

import cn.sweetberry.codes.dglab.websocket.common.Endpoint
import cn.sweetberry.codes.dglab.websocket.common.Payload
import cn.sweetberry.codes.dglab.websocket.common.codes.Error
import cn.sweetberry.codes.dglab.websocket.server.BindingRegistry.Role.CLIENT
import cn.sweetberry.codes.dglab.websocket.server.BindingRegistry.Role.TARGET
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * DGLab WebSocket 服务器实现
 *
 * 该类是 DGLab 协议的核心服务器实现，继承自 Java-WebSocket 库的 WebSocketServer。
 * 支持 N 对 N 的终端连接模式，管理客户端连接、消息路由和绑定关系。
 *
 * 主要功能：
 * - 处理 WebSocket 连接建立与断开
 * - 管理终端绑定关系（Client ↔ Target）
 * - 消息路由与转发
 * - 心跳维持
 * - 错误处理与日志记录
 *
 * @param address 服务器绑定的地址和端口
 * @see DgLabSocketService 服务入口类
 * @see BindingRegistry 绑定关系管理
 * @see Endpoint 终端抽象
 */
class DgLabSocketServer(address: InetSocketAddress) : WebSocketServer(address) {

    /**
     * 服务器协程作用域
     *
     * 用于管理服务器内部所有协程的生命周期，包括心跳任务等
     */
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logger = LoggerFactory.getLogger(DgLabSocketServer::class.java)

    /**
     * 所有活跃的终端映射
     *
     * Key: 终端唯一标识 UUID
     * Value: 终端对象（WebSocket 或 Local）
     */
    private val endpoints = ConcurrentHashMap<UUID, Endpoint>()
    
    /**
     * 绑定关系注册表
     *
     * 管理客户端（APP）与目标终端（硬件设备）之间的绑定关系
     */
    private val bindingRegistry = BindingRegistry()

    /**
     * 心跳定时任务
     *
     * 定期向所有活跃终端发送心跳包以维持连接
     */
    private var heartbeatJob: Job? = null
    
    /**
     * 心跳发送间隔
     *
     * 单位：毫秒，默认 60 秒
     */
    private val heartbeatIntervalMs = 60_000L // 每分钟发送一次

    /**
     * WebSocket 连接建立回调
     *
     * 当新的 WebSocket 客户端连接时自动调用。
     * 系统会自动分配 UUID 并发送连接确认消息。
     *
     * @param conn WebSocket 连接对象
     * @param handshake 客户端握手请求
     */
    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val newEndpoint = Endpoint.WebSocket(UUID.randomUUID(), conn)
        val connectMessage = Json.encodeToString(Payload.connect(newEndpoint.id))
        serverScope.launch(Dispatchers.IO) {
            try {
                check(registerEndpoint(newEndpoint))
                newEndpoint.send(connectMessage)
            } catch (e: Exception) {
                logger.error("${conn.remoteSocketAddress} connect failed: ${e.message}")
                unregisterEndpoint(newEndpoint.id)
            }
        }
    }

    /**
     * 创建一个本地端点（用于程序内部集成）
     *
     * 本地端点允许程序内部直接与服务器进行通信，
     * 适用于 Minecraft Mod 等需要直接发送/接收消息的场景。
     *
     * @param sender 消息发送回调，当需要发送消息到对端时调用
     * @return 创建成功的本地端点，如果服务器未运行则返回 null
     * @see Endpoint.Local
     * @see DgLabSocketService.openLocal
     */
    internal fun openLocal(sender: (String) -> Unit): Endpoint.Local? {
        val newEndpoint = Endpoint.Local(UUID.randomUUID(), sender, ::handleMessage)
        return if (registerEndpoint(newEndpoint)) newEndpoint
        else null
    }

    /**
     * 注册终端到服务器
     *
     * 将新创建的终端添加到管理映射中。每个终端都有唯一的 UUID。
     *
     * @param endpoint 要注册的终端
     * @return 如果该 UUID 已被占用返回 false，否则返回 true
     */
    internal fun registerEndpoint(endpoint: Endpoint): Boolean {
        return endpoints.putIfAbsent(endpoint.id, endpoint) == null
    }

    /**
     * 注销终端
     *
     * 从服务器移除终端，同时解除所有绑定关系。
     *
     * @param id 要注销的终端 UUID
     * @return 被移除的终端对象，如果不存在则返回 null
     */
    internal fun unregisterEndpoint(id: UUID): Endpoint? {
        bindingRegistry.unbindAll(id)
        return endpoints.remove(id)
    }

    /**
     * WebSocket 连接关闭回调
     *
     * 当 WebSocket 连接关闭时自动调用。执行以下操作：
     * 1. 查找对应的终端
     * 2. 关闭终端并清理绑定关系
     * 3. 通知对端连接已断开
     *
     * @param conn 关闭的 WebSocket 连接
     * @param code 关闭状态码
     * @param reason 关闭原因描述
     * @param remote 是否由远程端发起关闭
     */
    override fun onClose(
        conn: WebSocket, code: Int, reason: String?, remote: Boolean
    ) {
        // 找到对应 Endpoint
        val endpoint = endpoints.values.find { it is Endpoint.WebSocket && it.conn == conn } ?: return

        // 注销 endpoint
        close(endpoint.id)

        logger.info("Endpoint ${endpoint.id} closed. Code: $code, Reason: $reason, Remote: $remote")
    }

    /**
     * 关闭本地端点
     *
     * 与 onClose 类似，但用于本地端点。
     *
     * @param local 要关闭的本地端点
     * @return 被关闭的终端对象，如果不存在则返回 null
     */
    internal fun localClose(local: Endpoint.Local): Endpoint? {
        logger.info("Local endpoint ${local.id} closed")
        return close(local.id)
    }

    /**
     * 关闭终端
     *
     * 关闭指定的终端，包括：
     * 1. 查找终端的角色（Client 或 Target）
     * 2. 查找绑定的对端
     * 3. 向对端发送断开连接通知
     * 4. 注销终端
     *
     * @param endpointId 要关闭的终端 UUID
     * @return 被关闭的终端对象，如果不存在则返回 null
     */
    internal fun close(endpointId: UUID): Endpoint? {
        val role = bindingRegistry.roleOf(endpointId)
        val peerId = bindingRegistry.peerOf(endpointId)
        if (role != null && peerId != null) {
            val closePayload = when (role) {
                CLIENT -> Payload.close(endpointId, peerId, Error.CLIENT_OFFLINE)
                TARGET -> Payload.close(peerId, peerId, Error.CLIENT_OFFLINE)
            }
            endpoints[peerId]?.send(Json.encodeToString(closePayload))
        }

        return unregisterEndpoint(endpointId)
    }

    /**
     * WebSocket 消息接收回调
     *
     * 当服务器收到 WebSocket 消息时自动调用。
     * 消息被解析为 Payload 对象后转发给 handleMessage 处理。
     *
     * @param conn 发送消息的 WebSocket 连接
     * @param message 接收到的消息字符串（JSON 格式）
     */
    override fun onMessage(conn: WebSocket, message: String) {
        val endpoint = endpoints.values.find { it is Endpoint.WebSocket && it.conn == conn } ?: return
        try {
            val payload = Json.decodeFromString<Payload>(message)
            handleMessage(endpoint, payload)
        } catch (e: Exception) {
            logger.error("Failed to parse message from endpoint ${endpoint.id}: ${e.message}", e)
        }
    }

    /**
     * 处理接收到的消息
     *
     * 根据消息类型分发到不同的处理函数：
     * - bind: 处理绑定请求
     * - msg: 转发数据消息
     * - break: 连接断开通知
     * - heartbeat: 心跳包
     * - error: 错误消息
     *
     * @param endpoint 消息来源终端
     * @param message 解析后的 Payload 对象
     */
    internal fun handleMessage(endpoint: Endpoint, message: Payload) {
        when (message.type) {
            // 绑定请求处理
            "bind" -> handleBind(endpoint, message)

            // 消息转发处理
            "msg" -> handleMessageForward(endpoint, message)

            // 连接断开通知（文档未清晰描述，观察到通常由服务器发送，客户端不应主动发送）
            "break" -> logger.info("Received connection break notification: $message")

            // 心跳包（文档未清晰描述，观察到通常由服务器发送，客户端不应主动发送）
            "heartbeat" -> logger.info("Received heartbeat from client: ${message.clientId}")

            // 错误消息（文档未清晰描述，观察到通常由服务器发送，客户端不应主动发送）
            "error" -> logger.error("Received error message from client: ${message.message}")

            // 未知消息类型
            else -> logger.warn("Received unknown message type: ${message.type}")
        }
    }

    /**
     * 处理消息转发
     *
     * 将接收到的消息验证后转发给绑定的对端。
     * 验证发送者身份确保消息来源合法。
     *
     * @param endpoint 发送方终端
     * @param message 消息 Payload
     */
    private fun handleMessageForward(endpoint: Endpoint, message: Payload) {
        val role = bindingRegistry.roleOf(endpoint.id)
        val clientUUID = UUID.fromString(message.clientId)
        val targetUUID = UUID.fromString(message.targetId)

        // 验证消息发送者身份合法性
        if (!validateSenderIdentity(endpoint.id, clientUUID, targetUUID, role)) {
            logger.warn("Message sender identity validation failed. Endpoint: ${endpoint.id}, Client: $clientUUID, Target: $targetUUID, Role: $role")
            sendErrorResponse(endpoint, clientUUID, targetUUID, Error.NOT_BOUND)
            return
        }

        // 转发消息给对应的端点
        forwardMessageToPeer(endpoint, clientUUID, targetUUID, message, role)
    }

    /**
     * 处理绑定请求
     *
     * 处理 APP 与终端之间的绑定请求。绑定是 DGLab 通信的基础，
     * 只有绑定后的双方才能互相发送消息。
     *
     * 验证流程：
     * 1. 验证绑定消息格式（message 必须为 "DGLAB"）
     * 2. 验证目标终端 ID 匹配
     * 3. 检查客户端是否存在
     * 4. 执行绑定操作
     *
     * @param endpoint 发起绑定的终端
     * @param message 绑定请求 Payload
     */
    private fun handleBind(endpoint: Endpoint, message: Payload) {
        val clientUUID = UUID.fromString(message.clientId)
        val targetUUID = UUID.fromString(message.targetId)

        // 验证绑定消息格式
        if (!validateBindMessage(message)) {
            logger.warn("Invalid bind message format. Expected 'DGLAB' but got: ${message.message}")
            sendBindResult(endpoint, clientUUID, targetUUID, Error.INVALID_JSON)
            return
        }

        // 验证目标端ID匹配
        if (!validateTargetEndpointId(targetUUID, endpoint.id)) {
            logger.warn("Target endpoint ID mismatch. Expected: ${endpoint.id}, Received: $targetUUID")
            sendBindResult(endpoint, clientUUID, targetUUID, Error.INTERNAL_ERROR)
            return
        }

        // 检查客户端是否存在
        val client = endpoints[clientUUID]
        if (client == null) {
            logger.warn("Client not found for binding. Client UUID: $clientUUID")
            sendBindResult(endpoint, clientUUID, targetUUID, Error.CLIENT_NOT_EXIST)
            return
        }

        // 执行绑定操作
        executeBinding(endpoint, client, clientUUID, targetUUID)
    }

// =============== 私有辅助方法 ===============

    /**
     * 验证发送方身份
     *
     * 确保消息发送者与其声明的身份一致，防止伪造消息。
     *
     * @param endpointId 实际发送方的 UUID
     * @param clientUUID 消息中声明的客户端 UUID
     * @param targetUUID 消息中声明的目标 UUID
     * @param role 发送方的绑定角色
     * @return 身份验证是否通过
     */
    private fun validateSenderIdentity(
        endpointId: UUID,
        clientUUID: UUID,
        targetUUID: UUID,
        role: BindingRegistry.Role?
    ): Boolean {
        return when (role) {
            CLIENT -> clientUUID == endpointId && targetUUID == bindingRegistry.peerOf(endpointId)
            TARGET -> targetUUID == endpointId && clientUUID == bindingRegistry.peerOf(endpointId)
            null -> false
        }
    }

    /**
     * 转发消息到对端
     *
     * 根据发送方角色将消息路由到对应的接收方。
     *
     * @param senderEndpoint 发送方终端
     * @param clientUUID 客户端 UUID
     * @param targetUUID 目标 UUID
     * @param message 要转发的消息
     * @param role 发送方角色
     */
    private fun forwardMessageToPeer(
        senderEndpoint: Endpoint,
        clientUUID: UUID,
        targetUUID: UUID,
        message: Payload,
        role: BindingRegistry.Role?
    ) {
        val receiverEndpoint = when (role) {
            CLIENT -> endpoints[targetUUID]
            TARGET -> endpoints[clientUUID]
            else -> null
        }

        if (receiverEndpoint != null) {
            try {
                receiverEndpoint.send(Json.encodeToString(message))
                logger.debug("Message forwarded successfully. From: {}, To: {}, Role: {}", clientUUID, targetUUID, role)
            } catch (e: Exception) {
                logger.error("Failed to forward message to peer. Error: ${e.message}")
            }
        } else {
            logger.warn("Message receiver endpoint not found. Client: $clientUUID, Target: $targetUUID")
            sendErrorResponse(senderEndpoint, clientUUID, targetUUID, Error.RECEIVER_NOT_FOUND)
        }
    }

    /**
     * 验证绑定消息格式
     *
     * DGLAB 协议要求绑定消息的 message 字段必须为 "DGLAB"
     *
     * @param message 绑定的 Payload
     * @return 格式是否有效
     */
    private fun validateBindMessage(message: Payload): Boolean {
        return message.message == "DGLAB"
    }

    /**
     * 验证目标终端 ID
     *
     * 确保绑定请求中的 targetId 与实际发送方的 ID 一致
     *
     * @param receivedTargetId 消息中接收到的 targetId
     * @param endpointId 实际终端的 UUID
     * @return ID 是否匹配
     */
    private fun validateTargetEndpointId(receivedTargetId: UUID, endpointId: UUID): Boolean {
        return receivedTargetId == endpointId
    }

    /**
     * 执行绑定操作
     *
     * 在 BindingRegistry 中注册客户端与目标之间的绑定关系，
     * 并向双方发送绑定结果通知。
     *
     * @param targetEndpoint 目标终端
     * @param clientEndpoint 客户端终端
     * @param clientUUID 客户端 UUID
     * @param targetUUID 目标 UUID
     */
    private fun executeBinding(
        targetEndpoint: Endpoint,
        clientEndpoint: Endpoint,
        clientUUID: UUID,
        targetUUID: UUID
    ) {
        if (bindingRegistry.bind(clientUUID, targetUUID)) {
            logger.info("Binding successful. Client: $clientUUID, Target: $targetUUID")
            sendBindResult(targetEndpoint, clientUUID, targetUUID, Error.SUCCESS)
            sendBindResult(clientEndpoint, clientUUID, targetUUID, Error.SUCCESS)
        } else {
            logger.warn("Binding failed - ID already bound. Client: $clientUUID, Target: $targetUUID")
            sendBindResult(targetEndpoint, clientUUID, targetUUID, Error.ID_ALREADY_BOUND)
        }
    }

// =============== 响应发送函数 ===============

    /**
     * 发送错误响应
     *
     * 向指定终端发送错误消息
     *
     * @param endpoint 目标终端
     * @param clientId 客户端 UUID
     * @param targetId 目标 UUID
     * @param error 错误类型
     */
    private fun sendErrorResponse(
        endpoint: Endpoint,
        clientId: UUID,
        targetId: UUID,
        error: Error
    ) {
        val payload = Payload.error(clientId, targetId, error)
        sendPayload(endpoint, payload, "error response")
    }

    /**
     * 发送绑定结果
     *
     * 向指定终端发送绑定操作的结果
     *
     * @param endpoint 目标终端
     * @param clientId 客户端 UUID
     * @param targetId 目标 UUID
     * @param error 错误类型（成功或失败原因）
     */
    private fun sendBindResult(
        endpoint: Endpoint,
        clientId: UUID,
        targetId: UUID,
        error: Error
    ) {
        val payload = Payload.bindResult(clientId, targetId, error)
        sendPayload(endpoint, payload, "bind result")
    }

    /**
     * 发送 Payload 到终端
     *
     * 通用的消息发送方法，处理序列化与异常捕获
     *
     * @param endpoint 目标终端
     * @param payload 要发送的数据
     * @param payloadType 日志中描述的消息类型
     */
    private fun sendPayload(
        endpoint: Endpoint,
        payload: Payload,
        payloadType: String
    ) {
        try {
            endpoint.send(Json.encodeToString(payload))
            logger.debug("{} sent successfully to endpoint: {}", payloadType, endpoint.id)
        } catch (e: Exception) {
            logger.error("Failed to send $payloadType. Error: ${e.message}")
        }
    }

    /**
     * WebSocket 错误回调
     *
     * 当 WebSocket 连接发生错误时调用
     *
     * @param conn 发生错误的连接
     * @param ex 异常对象
     */
    override fun onError(conn: WebSocket, ex: Exception) {
        val endpointId = endpoints.values.find { it is Endpoint.WebSocket && it.conn == conn }?.id
        logger.error("Error on endpoint $endpointId: ${ex.message}", ex)
    }

    /**
     * 服务器启动回调
     *
     * 当 WebSocket 服务器成功启动后调用
     * 启动心跳定时任务
     */
    override fun onStart() {
        logger.info("WebSocket server started on ${this.address}:${this.port}")
        startHeartbeat()
    }

    /**
     * 启动心跳定时器
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serverScope.launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                sendHeartbeatToAll()
            }
        }
    }

    /**
     * 向所有客户端发送心跳包
     */
    private fun sendHeartbeatToAll() {
        if (endpoints.isEmpty()) return

        logger.debug("Sending heartbeat to ${endpoints.size} endpoints")

        endpoints.forEach { (clientId, endpoint) ->
            try {
                // 获取绑定的对端ID，用于心跳消息的 targetId
                val peerId = bindingRegistry.peerOf(clientId)
                // 使用工厂方法创建心跳消息
                val heartbeatPayload = Payload.heartbeat(clientId, peerId)
                endpoint.send(Json.encodeToString(heartbeatPayload))
            } catch (e: Exception) {
                logger.error("Failed to send heartbeat to $clientId: ${e.message}")
            }
        }
    }

    /**
     * 停止心跳定时器
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    override fun stop() {
        stop(0)
    }

    override fun stop(timeout: Int) {
        stop(timeout, "")
    }

    override fun stop(timeout: Int, closeMessage: String) {
        stopHeartbeat()
        super.stop(timeout, closeMessage)
    }
}