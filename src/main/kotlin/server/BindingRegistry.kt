package cn.sweetberry.codes.dglab.websocket.server

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 绑定关系注册表
 *
 * 管理 DGLab 协议中的客户端（APP）与目标终端（硬件设备）之间的绑定关系。
 * 支持一对一绑定：一个客户端只能绑定一个目标，一个目标也只能绑定一个客户端。
 *
 * 绑定关系用于：
 * - 消息路由：确定消息应该转发给哪个对端
 * - 身份验证：确保只有绑定双方才能互相通信
 * - 连接管理：断开连接时通知对端
 *
 * 该类是线程安全的，使用 ConcurrentHashMap 存储绑定关系。
 *
 * @see DgLabSocketServer 服务器实现
 * @see Role 终端角色枚举
 */
class BindingRegistry {

    /**
     * 客户端到目标的映射
     *
     * Key: 客户端 UUID（通常是 APP 端）
     * Value: 目标 UUID（通常是硬件设备端）
     */
    // Client -> Target
    private val clientToTarget = ConcurrentHashMap<UUID, UUID>()

    /**
     * 目标到客户端的映射
     *
     * 用于快速查找目标对应的客户端。
     * 当前协议为一对一绑定，如果将来需要一对多，可改为 Set。
     */
    // Target -> Client（一对一协议，若将来一对多，这里改成 Set）
    private val targetToClient = ConcurrentHashMap<UUID, UUID>()

    /**
     * 建立绑定关系
     *
     * 将客户端 UUID 与目标 UUID 进行绑定。绑定成功后，双方可以互相发送消息。
     *
     * 绑定条件：
     * - 客户端 UUID 尚未绑定其他目标
     * - 目标 UUID 尚未绑定其他客户端
     *
     * @param clientId 客户端 UUID
     * @param targetId 目标 UUID
     * @return 绑定是否成功，如果任一方已绑定则返回 false
     */
    fun bind(clientId: UUID, targetId: UUID): Boolean {
        // 已绑定直接拒绝
        if (clientToTarget.containsKey(clientId)) return false
        if (targetToClient.containsKey(targetId)) return false

        clientToTarget[clientId] = targetId
        targetToClient[targetId] = clientId
        return true
    }

    /**
     * 解除所有绑定关系
     *
     * 当终端断开连接时调用，解除该终端的所有绑定关系。
     * 如果该终端是客户端，则解除与目标的绑定；
     * 如果该终端是目标，则解除与客户端的绑定。
     *
     * @param id 要解除绑定的终端 UUID
     * @return 是否成功解除绑定
     */
    fun unbindAll(id: UUID) {
        unbindAsClient(id) || unbindAsTarget(id)
    }

    /**
     * 解除客户端绑定
     *
     * @param clientId 客户端 UUID
     * @return 是否成功解除
     */
    private fun unbindAsClient(clientId: UUID): Boolean {
        val targetId = clientToTarget.remove(clientId) ?: return false
        targetToClient.remove(targetId)
        return true
    }

    /**
     * 解除目标绑定
     *
     * @param targetId 目标 UUID
     * @return 是否成功解除
     */
    private fun unbindAsTarget(targetId: UUID): Boolean {
        val clientId = targetToClient.remove(targetId) ?: return false
        clientToTarget.remove(clientId)
        return true
    }

    /**
     * 获取绑定对端
     *
     * 查找指定终端绑定的对端 UUID。
     *
     * @param id 终端 UUID
     * @return 对端 UUID，如果未绑定则返回 null
     */
    fun peerOf(id: UUID): UUID? =
        clientToTarget[id] ?: targetToClient[id]

    /**
     * 获取终端角色
     *
     * 确定指定终端在绑定关系中的角色。
     *
     * @param id 终端 UUID
     * @return 角色（CLIENT 或 TARGET），如果未绑定则返回 null
     */
    fun roleOf(id: UUID): Role? = when {
        clientToTarget.containsKey(id) -> Role.CLIENT
        targetToClient.containsKey(id) -> Role.TARGET
        else -> null
    }

    /**
     * 终端角色枚举
     *
     * 表示终端在绑定关系中的角色：
     * - CLIENT: 客户端（通常是 APP 端）
     * - TARGET: 目标终端（通常是硬件设备端）
     */
    enum class Role { CLIENT, TARGET }
}
