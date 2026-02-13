# DgLab WebSocket Server

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-111%20passing-brightgreen.svg)](src/test)

[English](README-en.md) | 简体中文

DgLab WebSocket Server 是一个基于 Kotlin 实现的 WebSocket 服务端库，用于与 **郊狼脉冲主机 3.0 (Coyote 3.0)** 进行通信。该库完整实现了 DgLab 官方的 WebSocket 通信协议，支持 N 对 N 的终端连接模式。

## 功能特性

- ✅ **完整的协议实现** - 支持绑定、消息转发、强度控制、波形下发等所有协议指令
- ✅ **N 对 N 连接模式** - 支持多个 APP 与多个第三方终端同时连接
- ✅ **本地端点支持** - 提供 `Endpoint.Local` 用于程序内部集成（如 Minecraft Mod）
- ✅ **高并发设计** - 使用 Kotlin 协程和线程安全集合
- ✅ **完善的错误处理** - 实现了所有协议错误码
- ✅ **111 个单元测试** - 100% 覆盖核心功能

## 快速开始

### Gradle 依赖

#### JitPack (推荐)

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

### 基础使用

```kotlin
import cn.sweetberry.codes.dglab.websocket.server.DgLabSocketService
import cn.sweetberry.codes.dglab.websocket.common.Payload
import cn.sweetberry.codes.dglab.websocket.common.codes.Channel
import cn.sweetberry.codes.dglab.websocket.common.codes.StrengthSettingMode
import kotlinx.serialization.json.Json

fun main() {
    // 1. 启动 WebSocket 服务器（默认端口 17479）
    DgLabSocketService.start(port = 17479)
    
    // 2. 创建本地端点（用于接收/发送消息）
    val localEndpoint = DgLabSocketService.openLocal { message: String ->
        // 处理收到的消息
        println("收到消息: $message")
    } ?: return
    
    // 3. 等待 APP 连接并绑定...
    // 当收到 bind 消息时，需要进行绑定确认
    
    // 4. 发送指令示例：增加 A 通道强度
    val increasePayload = Payload.setStrength(
        clientId = localEndpoint.id,
        targetId = appId,  // APP 的 ID
        targetChannel = Channel.CHANNEL_A,
        settingMode = StrengthSettingMode.INCREASE,
        targetStrength = 5
    )
    
    // 5. 停止服务器
    DgLabSocketService.stop()
}
```

## 协议支持

本库完整实现了 [DgLab 官方 WebSocket 协议](https://github.com/DG-LAB-OPENSOURCE/DG-LAB-OPENSOURCE/blob/main/socket/README.md)：

### 消息类型 (type)

| 类型 | 说明 | 方向 |
|------|------|------|
| `bind` | 绑定请求/结果 | 双向 |
| `msg` | 数据消息（强度/波形/反馈） | 双向 |
| `break` | 连接断开通知 | 服务器→终端 |
| `heartbeat` | 心跳包 | 服务器→终端 |
| `error` | 错误消息 | 双向 |

### 支持的指令

#### 强度操作
```
strength-1+1+5     // A通道强度+5
strength-2+0+20    // B通道强度-20  
strength-2+2+0     // B通道归零
strength-1+2+35    // A通道设为35
```

#### 波形数据
```
pulse-A:["0123456789abcdef",...]  // A通道波形（最大100条）
pulse-B:["0123456789abcdef",...]  // B通道波形
```

#### 清空队列
```
clear-1   // 清空A通道队列
clear-2   // 清空B通道队列
```

#### APP 反馈
```
feedback-0   // A通道按钮0（圆形）
feedback-5   // B通道按钮0（圆形）
```

## API 文档

### DgLabSocketService（单例）

WebSocket 服务的入口类，提供服务器生命周期管理。

```kotlin
object DgLabSocketService {
    // 启动服务器（幂等）
    fun start(port: Int = 17479)
    
    // 停止服务器
    fun stop(graceful: Boolean = true)
    
    // 检查运行状态
    fun isRunning(): Boolean
    
    // 创建本地端点
    fun openLocal(sender: (String) -> Unit): Endpoint.Local?
    
    // 关闭本地端点
    fun closeLocal(local: Endpoint.Local): Endpoint?
}
```

### Payload（数据类）

所有消息的通用数据结构。

```kotlin
@Serializable
data class Payload(
    val type: String,      // 消息类型
    val clientId: String,  // 终端ID
    val targetId: String,  // 目标ID
    val message: String    // 消息内容
) {
    // 工厂方法
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
    
    // 解析方法
    fun toChannelStrengthLimit(): ChannelStrengthLimit?
    fun toFeedbackIndexCode(): FeedbackIndex?
}
```

### 枚举类型

#### Channel（通道）
```kotlin
enum class Channel(val numberCode: String, val letterCode: String) {
    CHANNEL_A("1", "A"),  // A通道
    CHANNEL_B("2", "B")   // B通道
}
```

#### Error（错误码）
```kotlin
enum class Error(val code: String, val message: String) {
    SUCCESS("200", "成功"),
    CLIENT_OFFLINE("209", "对方客户端已断开"),
    ID_ALREADY_BOUND("400", "此 id 已被其他客户端绑定关系"),
    CLIENT_NOT_EXIST("401", "要绑定的目标客户端不存在"),
    NOT_BOUND("402", "收信方和寄信方不是绑定关系"),
    INVALID_JSON("403", "发送的内容不是标准 json 对象"),
    RECEIVER_NOT_FOUND("404", "未找到收信人"),
    MESSAGE_TOO_LONG("405", "下发的 message 长度大于 1950"),
    INTERNAL_ERROR("500", "服务器内部异常")
}
```

#### StrengthSettingMode（强度设置模式）
```kotlin
enum class StrengthSettingMode(val code: String, val officialCode: String) {
    DECREASE("0", "1"),   // 减少
    INCREASE("1", "2"),   // 增加
    SET_TO("2", "4")      // 设为指定值
}
```

#### FeedbackIndex（反馈按钮）
```kotlin
enum class FeedbackIndex(val code: String, val desc: String) {
    A_CIRCLE("0", "A通道：○"),
    A_TRIANGLE("1", "A通道：△"),
    // ... A通道 0-4, B通道 5-9
}
```

## Fabric Mod 集成示例

```kotlin
// build.gradle.kts
repositories {
    maven("https://jitpack.io")
}

dependencies {
    modImplementation("com.github.Rainy-day-y:DgLab-Websocket-Server:1.0.0")
    // Java-WebSocket 需要 include
    include("org.java-websocket:Java-WebSocket:1.6.0")
}

// Mod 代码
class MyMod : ModInitializer {
    override fun onInitialize() {
        // 启动服务
        DgLabSocketService.start(17479)
        
        // 注册命令
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(CommandManager.literal("dglab")
                .executes { context ->
                    val player = context.source.player ?: return@executes 0
                    
                    // 创建本地端点
                    val endpoint = DgLabSocketService.openLocal { message ->
                        player.sendMessage(Text.literal("收到: $message"))
                    } ?: return@executes 0
                    
                    player.sendMessage(Text.literal("终端ID: ${endpoint.id}"))
                    1
                }
            )
        }
    }
}
```

## 运行测试

```bash
# 运行所有测试
./gradlew test

# 运行特定测试类
./gradlew test --tests "PayloadTest"

# 生成测试报告
./gradlew test
# 报告位置: build/reports/tests/test/index.html
```

## 项目结构

```
src/main/kotlin/
├── common/
│   ├── Payload.kt              # 消息数据结构
│   ├── Endpoint.kt             # 端点抽象（WebSocket/Local）
│   ├── codes/
│   │   ├── Channel.kt          # 通道枚举
│   │   ├── Error.kt            # 错误码枚举
│   │   ├── FeedbackIndex.kt    # 反馈按钮枚举
│   │   └── StrengthSettingMode.kt  # 强度模式枚举
│   └── data/
│       ├── ChannelStrengthLimit.kt
│       └── PulseData.kt        # 波形数据
└── server/
    ├── DgLabSocketServer.kt    # WebSocket 服务器实现
    ├── DgLabSocketService.kt   # 单例服务入口
    └── BindingRegistry.kt      # 绑定关系管理

src/test/kotlin/                # 111 个单元测试
```

## 许可证

本项目采用 [MIT 许可证](LICENSE)。

## 致谢

- [DgLab 开源项目](https://github.com/DG-LAB-OPENSOURCE) - 提供官方协议文档
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) - WebSocket 实现
- [Kotlinx.Serialization](https://github.com/Kotlin/kotlinx.serialization) - JSON 序列化

## 链接

- [官方协议文档](https://github.com/DG-LAB-OPENSOURCE/DG-LAB-OPENSOURCE/blob/main/socket/README.md)
- [JitPack 仓库](https://jitpack.io/#Rainy-day-y/DgLab-Websocket-Server)
- [问题反馈](https://github.com/Rainy-day-y/DgLab-Websocket-Server/issues)
