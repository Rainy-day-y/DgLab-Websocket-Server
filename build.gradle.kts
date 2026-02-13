import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    `maven-publish`
}

group = "cn.sweetberry.codes.dglab.websocket"
version = "1.2.0"

repositories {
    mavenCentral()
}

dependencies {
    // Fabric 项目 loom 会提供这些依赖，使用 compileOnly 避免重复
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    
    // Fabric 不会提供，需要实际引入
    implementation("org.java-websocket:Java-WebSocket:1.6.0")

    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

// 生成 sources jar
tasks.register<Jar>("sourcesJar") {
    archiveClassifier = "sources"
    from(sourceSets.main.get().allSource)
}

// 生成 javadoc jar
tasks.register<Jar>("javadocJar") {
    archiveClassifier = "javadoc"
    from(tasks.javadoc)
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            
            pom {
                name.set("DgLab WebSocket Server")
                description.set("WebSocket server library for DgLab integration")
                url.set("https://github.com/Rainy-day-y/DgLab-Websocket-Server")
                
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("Rainy-day-y")
                        name.set("Rainy雨霏")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/Rainy-day-y/DgLab-Websocket-Server.git")
                    developerConnection.set("scm:git:ssh://git@github.com:Rainy-day-y/DgLab-Websocket-Server.git")
                    url.set("https://github.com/Rainy-day-y/DgLab-Websocket-Server")
                }
            }
        }
    }
}
