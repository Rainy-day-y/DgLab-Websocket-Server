package cn.sweetberry.codes.dglab.websocket.server

import cn.sweetberry.codes.dglab.websocket.common.Endpoint
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

object DgLabSocketService {

    @Volatile
    private var server: DgLabSocketServer? = null

    private val lock = Any()

    /**
     * 启动服务器（非阻塞）
     *
     * - 多次调用是幂等的
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
     * @param graceful 是否优雅关闭
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
     * 服务是否正在运行
     */
    fun isRunning(): Boolean {
        return server != null
    }

    /**
     * 打开一个本地 Endpoint
     *
     * 注意：server 未运行时直接返回 null
     */
    fun openLocal(
        sender: (String) -> Unit
    ): Endpoint.Local? {
        return server?.openLocal(sender)
    }

    /**
     * 关闭一个本地 Endpoint
     */
    fun closeLocal(
        local: Endpoint.Local
    ): Endpoint? {
        return server?.localClose(local)
    }

    /**
     * 获取当前 server（只读）
     *
     * 用于高级控制，不建议普通业务使用
     */
    fun getServer(): DgLabSocketServer? = server
}

