# DgLab WebSocket Server

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-111%20passing-brightgreen.svg)](src/test)

English | [简体中文](README.md)

A Kotlin-based WebSocket server library for communicating with **Coyote 3.0 E-Stim Device**. This library fully implements the DgLab official WebSocket protocol, supporting N-to-N terminal connection mode.

## Features

- ✅ **Complete Protocol Implementation** - Supports all protocol commands: binding, message forwarding, strength control, waveform transmission
- ✅ **N-to-N Connection Mode** - Multiple APPs and multiple third-party terminals can connect simultaneously
- ✅ **Local Endpoint Support** - Provides `Endpoint.Local` for internal integration (e.g., Minecraft Mods)
- ✅ **High Concurrency Design** - Uses Kotlin coroutines and thread-safe collections
- ✅ **Comprehensive Error Handling** - Implements all protocol error codes
- ✅ **111 Unit Tests** - 100% core functionality coverage

## Quick Start

### Gradle Dependency

#### JitPack (Recommended)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Rainy-day-y:DgLab-Websocket-Server:1.0.0")
}
```

#### Maven Local

```bash
./gradlew publishToMavenLocal
```

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("cn.sweetberry.codes.dglab.websocket:dglab-websocket-server:1.0.0")
}
```

### Basic Usage

```kotlin
import cn.sweetberry.codes.dglab.websocket.server.DgLabSocketService
import cn.sweetberry.codes.dglab.websocket.common.Payload
import cn.sweetberry.codes.dglab.websocket.common.codes.Channel
import cn.sweetberry.codes.dglab.websocket.common.codes.StrengthSettingMode
import kotlinx.serialization.json.Json

fun main() {
    // 1. Start WebSocket server (default port 17479)
    DgLabSocketService.start(port = 17479)
    
    // 2. Create local endpoint (for receiving/sending messages)
    val localEndpoint = DgLabSocketService.openLocal { message: String ->
        // Handle received message
        println("Received: $message")
    } ?: return
    
    // 3. Wait for APP connection and binding...
    // When bind message is received, binding confirmation is needed
    
    // 4. Example: Increase Channel A strength
    val increasePayload = Payload.setStrength(
        clientId = localEndpoint.id,
        targetId = appId,  // APP's ID
        targetChannel = Channel.CHANNEL_A,
        settingMode = StrengthSettingMode.INCREASE,
        targetStrength = 5
    )
    
    // 5. Stop server
    DgLabSocketService.stop()
}
```

## Protocol Support

This library fully implements the [DgLab Official WebSocket Protocol](https://github.com/DG-LAB-OPENSOURCE/DG-LAB-OPENSOURCE/blob/main/socket/README.md):

### Message Types (type)

| Type | Description | Direction |
|------|-------------|-----------|
| `bind` | Binding request/result | Bidirectional |
| `msg` | Data messages (strength/waveform/feedback) | Bidirectional |
| `break` | Connection break notification | Server→Terminal |
| `heartbeat` | Heartbeat | Server→Terminal |
| `error` | Error message | Bidirectional |

### Supported Commands

#### Strength Operations
```
strength-1+1+5     // Channel A strength +5
strength-2+0+20    // Channel B strength -20  
strength-2+2+0     // Channel B set to 0
strength-1+2+35    // Channel A set to 35
```

#### Waveform Data
```
pulse-A:["0123456789abcdef",...]  // Channel A waveform (max 100 items)
pulse-B:["0123456789abcdef",...]  // Channel B waveform
```

#### Clear Queue
```
clear-1   // Clear Channel A queue
clear-2   // Clear Channel B queue
```

#### APP Feedback
```
feedback-0   // Channel A button 0 (circle)
feedback-5   // Channel B button 0 (circle)
```

## API Documentation

### DgLabSocketService (Singleton)

Entry point for WebSocket service, providing server lifecycle management.

```kotlin
object DgLabSocketService {
    // Start server (idempotent)
    fun start(port: Int = 17479)
    
    // Stop server
    fun stop(graceful: Boolean = true)
    
    // Check running status
    fun isRunning(): Boolean
    
    // Create local endpoint
    fun openLocal(sender: (String) -> Unit): Endpoint.Local?
    
    // Close local endpoint
    fun closeLocal(local: Endpoint.Local): Endpoint?
}
```

### Payload (Data Class)

Common data structure for all messages.

```kotlin
@Serializable
data class Payload(
    val type: String,      // Message type
    val clientId: String,  // Terminal ID
    val targetId: String,  // Target ID
    val message: String    // Message content
) {
    // Factory methods
    companion object {
        fun connect(uuid: UUID): Payload
        fun bindAttempt(clientId: UUID, targetId: UUID): Payload
        fun bindResult(clientId: UUID, targetId: UUID, error: Error): Payload
        fun command(clientId: UUID, targetId: UUID, message: String): Payload
        fun syncStrength(clientId: UUID, targetId: UUID, ...): Payload
        fun setStrength(clientId: UUID, targetId: UUID, ...): Payload
        fun sendPulse(clientId: UUID, targetId: UUID, ...): Payload
        fun clearPulse(clientId: UUID, targetId: UUID, channel: Channel): Payload
        fun feedback(clientId: UUID, targetId: UUID, feedbackIndex: FeedbackIndex): Payload
        fun heartbeat(clientId: UUID, errorCode: Error): Payload
        fun close(clientId: UUID, targetId: UUID, errorCode: Error): Payload
        fun error(clientId: UUID, targetId: UUID, errorCode: Error): Payload
    }
    
    // Parsing methods
    fun toChannelStrengthLimit(): ChannelStrengthLimit?
    fun toFeedbackIndexCode(): FeedbackIndex?
}
```

### Enum Types

#### Channel
```kotlin
enum class Channel(val numberCode: String, val letterCode: String) {
    CHANNEL_A("1", "A"),  // Channel A
    CHANNEL_B("2", "B")   // Channel B
}
```

#### Error
```kotlin
enum class Error(val code: String, val message: String) {
    SUCCESS("200", "Success"),
    CLIENT_OFFLINE("209", "Client offline"),
    ID_ALREADY_BOUND("400", "ID already bound"),
    CLIENT_NOT_EXIST("401", "Client does not exist"),
    NOT_BOUND("402", "Not bound relationship"),
    INVALID_JSON("403", "Invalid JSON"),
    RECEIVER_NOT_FOUND("404", "Receiver not found"),
    MESSAGE_TOO_LONG("405", "Message too long"),
    INTERNAL_ERROR("500", "Internal error")
}
```

#### StrengthSettingMode
```kotlin
enum class StrengthSettingMode(val code: String, val officialCode: String) {
    DECREASE("0", "1"),   // Decrease
    INCREASE("1", "2"),   // Increase
    SET_TO("2", "4")      // Set to specific value
}
```

#### FeedbackIndex
```kotlin
enum class FeedbackIndex(val code: String, val desc: String) {
    A_CIRCLE("0", "Channel A: ○"),
    A_TRIANGLE("1", "Channel A: △"),
    // ... Channel A 0-4, Channel B 5-9
}
```

## Fabric Mod Integration Example

```kotlin
// build.gradle.kts
repositories {
    maven("https://jitpack.io")
}

dependencies {
    modImplementation("com.github.Rainy-day-y:DgLab-Websocket-Server:1.0.0")
    // Java-WebSocket needs to be included
    include("org.java-websocket:Java-WebSocket:1.6.0")
}

// Mod code
class MyMod : ModInitializer {
    override fun onInitialize() {
        // Start service
        DgLabSocketService.start(17479)
        
        // Register command
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(CommandManager.literal("dglab")
                .executes { context ->
                    val player = context.source.player ?: return@executes 0
                    
                    // Create local endpoint
                    val endpoint = DgLabSocketService.openLocal { message ->
                        player.sendMessage(Text.literal("Received: $message"))
                    } ?: return@executes 0
                    
                    player.sendMessage(Text.literal("Terminal ID: ${endpoint.id}"))
                    1
                }
            )
        }
    }
}
```

## Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "PayloadTest"

# Generate test report
./gradlew test
# Report location: build/reports/tests/test/index.html
```

## Project Structure

```
src/main/kotlin/
├── common/
│   ├── Payload.kt              # Message data structure
│   ├── Endpoint.kt             # Endpoint abstraction (WebSocket/Local)
│   ├── codes/
│   │   ├── Channel.kt          # Channel enum
│   │   ├── Error.kt            # Error code enum
│   │   ├── FeedbackIndex.kt    # Feedback button enum
│   │   └── StrengthSettingMode.kt  # Strength mode enum
│   └── data/
│       ├── ChannelStrengthLimit.kt
│       └── PulseData.kt        # Waveform data
└── server/
    ├── DgLabSocketServer.kt    # WebSocket server implementation
    ├── DgLabSocketService.kt   # Singleton service entry
    └── BindingRegistry.kt      # Binding relationship management

src/test/kotlin/                # 111 unit tests
```

## License

This project is licensed under the [MIT License](LICENSE).

## Acknowledgments

- [DgLab Open Source Project](https://github.com/DG-LAB-OPENSOURCE) - Official protocol documentation
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) - WebSocket implementation
- [Kotlinx.Serialization](https://github.com/Kotlin/kotlinx.serialization) - JSON serialization

## Links

- [Official Protocol Documentation](https://github.com/DG-LAB-OPENSOURCE/DG-LAB-OPENSOURCE/blob/main/socket/README.md)
- [JitPack Repository](https://jitpack.io/#Rainy-day-y/DgLab-Websocket-Server)
- [Issue Tracker](https://github.com/Rainy-day-y/DgLab-Websocket-Server/issues)
