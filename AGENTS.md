# 项目指南

## 开发环境要求

- Kotlin 2.3.0
- Java 17+
- Gradle 9.0

## 运行测试

```bash
# 运行所有测试
gradlew test

# 运行特定测试类
gradlew test --tests "PayloadTest"

# 生成测试报告
gradlew test
# 报告位置: build/reports/tests/test/index.html
```

## 构建项目

```bash
# 编译项目
gradlew build

# 构建 JAR
gradlew jar

# 发布到 Maven 本地
gradlew publishToMavenLocal
```

## 代码检查

```bash
# 运行所有检查（包括测试）
gradlew check
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

src/test/kotlin/                # 单元测试
```

## 主要入口

- `DgLabSocketService` - 服务启动/停止入口
- `DgLabSocketServer` - WebSocket 服务器实现
