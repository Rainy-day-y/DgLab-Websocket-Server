package cn.sweetberry.codes.dglab.websocket.common

import java.util.UUID

sealed class Endpoint(
    val id: UUID
) {
    abstract fun send(message: String)

    class Local(
        id: UUID,
        private val sender: (String) -> Unit,
        private val emitHandler: (Endpoint, Payload) -> Unit
    ) : Endpoint(id) {
        override fun send(message: String) = sender(message)
        fun emit(message: Payload) = emitHandler(this, message)
    }

    class WebSocket(
        id: UUID,
        val conn: org.java_websocket.WebSocket
    ) : Endpoint(id) {
        override fun send(message: String) {
            if (conn.isOpen) {
                conn.send(message)
            }
        }
    }
}