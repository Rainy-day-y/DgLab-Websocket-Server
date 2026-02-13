package cn.sweetberry.codes.dglab.websocket.server

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.util.*

class BindingRegistryTest {

    private lateinit var registry: BindingRegistry

    @BeforeEach
    fun setup() {
        registry = BindingRegistry()
    }

    @Test
    fun `test bind two endpoints successfully`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        
        val result = registry.bind(clientId, targetId)
        
        assertTrue(result)
    }

    @Test
    fun `test bind same client twice fails`() {
        val clientId = UUID.randomUUID()
        val targetId1 = UUID.randomUUID()
        val targetId2 = UUID.randomUUID()
        
        registry.bind(clientId, targetId1)
        val result = registry.bind(clientId, targetId2)
        
        assertFalse(result)
    }

    @Test
    fun `test bind same target twice fails`() {
        val clientId1 = UUID.randomUUID()
        val clientId2 = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        
        registry.bind(clientId1, targetId)
        val result = registry.bind(clientId2, targetId)
        
        assertFalse(result)
    }

    @Test
    fun `test peerOf returns correct target for client`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        
        registry.bind(clientId, targetId)
        val peer = registry.peerOf(clientId)
        
        assertEquals(targetId, peer)
    }

    @Test
    fun `test peerOf returns correct client for target`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        
        registry.bind(clientId, targetId)
        val peer = registry.peerOf(targetId)
        
        assertEquals(clientId, peer)
    }

    @Test
    fun `test peerOf returns null for unbound id`() {
        val id = UUID.randomUUID()
        
        val peer = registry.peerOf(id)
        
        assertNull(peer)
    }

    @Test
    fun `test roleOf returns CLIENT for client id`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        
        registry.bind(clientId, targetId)
        val role = registry.roleOf(clientId)
        
        assertEquals(BindingRegistry.Role.CLIENT, role)
    }

    @Test
    fun `test roleOf returns TARGET for target id`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        
        registry.bind(clientId, targetId)
        val role = registry.roleOf(targetId)
        
        assertEquals(BindingRegistry.Role.TARGET, role)
    }

    @Test
    fun `test roleOf returns null for unbound id`() {
        val id = UUID.randomUUID()
        
        val role = registry.roleOf(id)
        
        assertNull(role)
    }

    @Test
    fun `test unbindAll removes binding as client`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        
        registry.bind(clientId, targetId)
        registry.unbindAll(clientId)
        
        assertNull(registry.peerOf(clientId))
        assertNull(registry.peerOf(targetId))
        assertNull(registry.roleOf(clientId))
        assertNull(registry.roleOf(targetId))
    }

    @Test
    fun `test unbindAll removes binding as target`() {
        val clientId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        
        registry.bind(clientId, targetId)
        registry.unbindAll(targetId)
        
        assertNull(registry.peerOf(clientId))
        assertNull(registry.peerOf(targetId))
        assertNull(registry.roleOf(clientId))
        assertNull(registry.roleOf(targetId))
    }

    @Test
    fun `test bind after unbind works`() {
        val clientId = UUID.randomUUID()
        val targetId1 = UUID.randomUUID()
        val targetId2 = UUID.randomUUID()
        
        registry.bind(clientId, targetId1)
        registry.unbindAll(clientId)
        val result = registry.bind(clientId, targetId2)
        
        assertTrue(result)
        assertEquals(targetId2, registry.peerOf(clientId))
    }
}
