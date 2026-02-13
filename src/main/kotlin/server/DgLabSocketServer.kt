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

class DgLabSocketServer(address: InetSocketAddress) : WebSocketServer(address) {

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logger = LoggerFactory.getLogger(DgLabSocketServer::class.java)

    private val endpoints = ConcurrentHashMap<UUID, Endpoint>()
    private val bindingRegistry = BindingRegistry()

    // 心跳定时器
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private val heartbeatIntervalMs = 60_000L // 每分钟发送一次

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

    fun openLocal(sender: (String) -> Unit): Endpoint.Local? {
        val newEndpoint = Endpoint.Local(UUID.randomUUID(), sender, ::handleMessage)
        return if (registerEndpoint(newEndpoint)) newEndpoint
        else null
    }

    fun registerEndpoint(endpoint: Endpoint): Boolean {
        return endpoints.putIfAbsent(endpoint.id, endpoint) == null
    }

    fun unregisterEndpoint(id: UUID): Endpoint? {
        bindingRegistry.unbindAll(id)
        return endpoints.remove(id)
    }

    override fun onClose(
        conn: WebSocket, code: Int, reason: String?, remote: Boolean
    ) {
        // 找到对应 Endpoint
        val endpoint = endpoints.values.find { it is Endpoint.WebSocket && it.conn == conn } ?: return

        // 注销 endpoint
        close(endpoint.id)

        logger.info("Endpoint ${endpoint.id} closed. Code: $code, Reason: $reason, Remote: $remote")
    }

    fun localClose(local: Endpoint.Local): Endpoint? {
        logger.info("Local endpoint ${local.id} closed")
        return close(local.id)
    }

    fun close(endpointId: UUID): Endpoint? {
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

    override fun onMessage(conn: WebSocket, message: String) {
        val endpoint = endpoints.values.find { it is Endpoint.WebSocket && it.conn == conn } ?: return
        try {
            val payload = Json.decodeFromString<Payload>(message)
            handleMessage(endpoint, payload)
        } catch (e: Exception) {
            logger.error("Failed to parse message from endpoint ${endpoint.id}: ${e.message}", e)
        }
    }

    fun handleMessage(endpoint: Endpoint, message: Payload) {
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

// =============== 通用工具函数 ===============

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

    private fun validateBindMessage(message: Payload): Boolean {
        return message.message == "DGLAB"
    }

    private fun validateTargetEndpointId(receivedTargetId: UUID, endpointId: UUID): Boolean {
        return receivedTargetId == endpointId
    }

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

    private fun sendErrorResponse(
        endpoint: Endpoint,
        clientId: UUID,
        targetId: UUID,
        error: Error
    ) {
        val payload = Payload.error(clientId, targetId, error)
        sendPayload(endpoint, payload, "error response")
    }

    private fun sendBindResult(
        endpoint: Endpoint,
        clientId: UUID,
        targetId: UUID,
        error: Error
    ) {
        val payload = Payload.bindResult(clientId, targetId, error)
        sendPayload(endpoint, payload, "bind result")
    }

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

    override fun onError(conn: WebSocket, ex: Exception) {
        val endpointId = endpoints.values.find { it is Endpoint.WebSocket && it.conn == conn }?.id
        logger.error("Error on endpoint $endpointId: ${ex.message}", ex)
    }

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