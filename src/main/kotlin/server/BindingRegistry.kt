package cn.sweetberry.codes.dglab.websocket.server

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BindingRegistry {

    // Client -> Target
    private val clientToTarget = ConcurrentHashMap<UUID, UUID>()

    // Target -> Client（一对一协议，若将来一对多，这里改成 Set）
    private val targetToClient = ConcurrentHashMap<UUID, UUID>()

    fun bind(clientId: UUID, targetId: UUID): Boolean {
        // 已绑定直接拒绝
        if (clientToTarget.containsKey(clientId)) return false
        if (targetToClient.containsKey(targetId)) return false

        clientToTarget[clientId] = targetId
        targetToClient[targetId] = clientId
        return true
    }

    fun unbindAll(id: UUID) {
        unbindAsClient(id) || unbindAsTarget(id)
    }

    private fun unbindAsClient(clientId: UUID): Boolean {
        val targetId = clientToTarget.remove(clientId) ?: return false
        targetToClient.remove(targetId)
        return true
    }

    private fun unbindAsTarget(targetId: UUID): Boolean {
        val clientId = targetToClient.remove(targetId) ?: return false
        clientToTarget.remove(clientId)
        return true
    }

    fun peerOf(id: UUID): UUID? =
        clientToTarget[id] ?: targetToClient[id]

    fun roleOf(id: UUID): Role? = when {
        clientToTarget.containsKey(id) -> Role.CLIENT
        targetToClient.containsKey(id) -> Role.TARGET
        else -> null
    }

    enum class Role { CLIENT, TARGET }
}
