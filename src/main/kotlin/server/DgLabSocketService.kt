package cn.sweetberry.codes.dglab.websocket.server

import cn.sweetberry.codes.dglab.websocket.common.Endpoint
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * DGLab WebSocket 服务入口
 *
 * 这是一个单例对象，提供 WebSocket 服务器的启动、停止和管理功能。
 * 是使用本库的主要入口点。
 *
 * 功能特性：
 * - 简单的服务器启动/停止接口
 * - 本地端点（Local Endpoint）管理
 * - 线程安全的单例模式
 *
 * 使用示例：
 * ```
 * // 启动服务器
 * DgLabSocketService.start(17479)
 *
 * // 创建本地端点
 * val endpoint = DgLabSocketService.openLocal { message ->
 *     println("收到: $message")
 * }
 *
 * // 停止服务器
 * DgLabSocketService.stop()
 * ```
 *
 * @see DgLabSocketServer 服务器实现
 * @see Endpoint 终端抽象
 */
object DgLabSocketService {

    /**
     * 服务器内部状态
     *
     * 使用 volatile 关键字确保多线程可见性
     */
    @Volatile
    private var server: DgLabSocketServer? = null

    /**
     * 同步锁
     *
     * 用于确保启动/停止操作的线程安全性
     */
    private val lock = Any()

    /**
     * 启动服务器（非阻塞）
     *
     * 在指定端口启动 WebSocket 服务器。该方法是幂等的：
     * - 如果服务器已在运行且端口相同，直接返回
     * - 如果服务器已在运行但端口不同，先停止旧服务器再启动新服务器
     *
     * @param port 监听端口，默认 17479（DGLAB 官方协议端口）
     * @throws IllegalStateException 如果端口已被占用
     *
     * @see stop 停止服务器
     * @see isRunning 检查运行状态
     */
    fun start(port: Int = 17479) {
        synchronized(lock) {
            val s = server
            if (s != null && s.port == port) return
            if (s != null) {
                stop()
            }
            val newServer = DgLabSocketServer(InetSocketAddress(port))
            server = newServer
            newServer.start()
        }
    }


    /**
     * 停止服务器
     *
     * 关闭 WebSocket 服务器并释放资源。
     *
     * @param graceful 是否优雅关闭。
     *                 true: 等待最多 1 秒让现有连接关闭
     *                 false: 立即关闭所有连接
     *
     * @see start 启动服务器
     * @see isRunning 检查运行状态
     */
    fun stop(graceful: Boolean = true) {
        val toStop: DgLabSocketServer?

        synchronized(lock) {
            toStop = server
            server = null
        }

        if (toStop != null) {
            try {
                if (graceful) {
                    toStop.stop(1000)
                } else {
                    toStop.stop()
                }
            } catch (e: Exception) {
                // 避免 stop 抛异常把调用方炸了
                LoggerFactory.getLogger("DgLabSocketService")
                    .warn("Failed to stop server", e)
            }
        }
    }

    /**
     * 检查服务器状态
     *
运行     * @return 服务器是否正在运行
     */
    fun isRunning(): Boolean {
        return server != null
    }

    /**
     * 创建本地端点
     *
     * 本地端点允许程序内部直接与服务器进行通信，
     * 无需通过网络连接。适用于 Minecraft Mod 集成等场景。
     *
     * @param sender 消息发送回调。当需要向对端发送消息时，
     *               此函数会被调用，参数为 JSON 字符串
     * @return 创建成功的本地端点，如果服务器未运行则返回 null
     *
     * @see Endpoint.Local
     * @see closeLocal 关闭本地端点
     */
    fun openLocal(
        sender: (String) -> Unit
    ): Endpoint.Local? {
        return server?.openLocal(sender)
    }

    /**
     * 关闭本地端点
     *
     * 关闭之前通过 openLocal 创建的本地端点。
     * 会通知对端连接已断开并清理绑定关系。
     *
     * @param local 要关闭的本地端点
     * @return 被关闭的终端对象，如果不存在则返回 null
     *
     * @see openLocal 创建本地端点
     */
    fun closeLocal(
        local: Endpoint.Local
    ): Endpoint? {
        return server?.localClose(local)
    }

    /**
     * 获取服务器实例
     *
     * 提供对内部 DgLabSocketServer 实例的直接访问。
     * 此方法用于高级控制场景，不建议普通业务使用。
     *
     * @return 当前服务器实例，如果未运行则返回 null
     *
     * @warning 谨慎使用，返回的对象可能在任何时候失效
     */
    fun getServer(): DgLabSocketServer? = server
}

