package cn.sweetberry.codes.dglab.websocket.common

import java.util.UUID

/**
 * 通信端点抽象
 *
 * 代表一个可以发送和接收消息的通信端点。
 * 支持两种实现：
 * - WebSocket: 网络连接的客户端
 * - Local: 程序内部创建的本地端点（用于集成）
 *
 * 使用场景：
 * - WebSocket 端点：接收来自 APP 或终端的网络连接
 * - 本地端点：在程序内部创建，用于直接发送/接收消息（如 Minecraft Mod）
 *
 * @see DgLabSocketServer 服务器
 * @see DgLabSocketService 服务入口
 */
/**
 * 通信端点抽象
 *
 * 代表一个可以发送和接收消息的通信端点。
 * 支持两种实现：
 * - WebSocket: 网络连接的客户端
 * - Local: 程序内部创建的本地端点（用于集成）
 *
 * 使用场景：
 * - WebSocket 端点：接收来自 APP 或终端的网络连接
 * - 本地端点：在程序内部创建，用于直接发送/接收消息（如 Minecraft Mod）
 *
 * @see DgLabSocketServer 服务器
 * @see DgLabSocketService 服务入口
 */
sealed class Endpoint(
    /**
     * 端点唯一标识符
     *
     * 每个端点都有一个唯一的 UUID，用于在服务器中识别和路由消息
     */
    val id: UUID
) {
    /**
     * 发送消息
     *
     * @param message 要发送的消息字符串（JSON 格式）
     */
    abstract fun send(message: String)

    /**
     * 本地端点
     *
     * 用于程序内部集成的端点，无需网络连接。
     * 适用于需要直接控制消息收发的场景。
     *
     * @param id 端点 UUID
     * @param sender 消息发送回调
     * @param emitHandler 消息接收处理回调
     * @see DgLabSocketService.openLocal 创建本地端点
     */
    class Local(
        id: UUID,
        private val sender: (String) -> Unit,
        private val emitHandler: (Endpoint, Payload) -> Unit
    ) : Endpoint(id) {
        /**
         * 发送消息到回调
         */
        override fun send(message: String) = sender(message)
        
        /**
         * 手动触发消息接收
         *
         * 用于程序内部主动触发消息处理
         */
        fun emit(message: Payload) = emitHandler(this, message)
    }

    /**
     * WebSocket 端点
     *
     * 通过 WebSocket 协议连接的远程客户端。
     *
     * @param id 端点 UUID
     * @param conn WebSocket 连接对象
     */
    class WebSocket(
        id: UUID,
        val conn: org.java_websocket.WebSocket
    ) : Endpoint(id) {
        /**
         * 发送消息到 WebSocket 连接
         *
         * 只在连接打开时发送消息
         */
        override fun send(message: String) {
            if (conn.isOpen) {
                conn.send(message)
            }
        }
    }
}