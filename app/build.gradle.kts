import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("chirp.spring-boot-app")
}

group = "com.seno"
version = "0.0.1-SNAPSHOT"
description = "Chirp Backend"

tasks {
    named<BootJar>("bootJar") {
        from(project(":notification").projectDir.resolve("src/main/resources")) {
            into("")
        }
        from(project(":user").projectDir.resolve("src/main/resources")) {
            into("")
        }
    }
}

dependencies {
    implementation(projects.chat)
    implementation(projects.notification)
    implementation(projects.user)
    implementation(projects.common)

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.mail)

    implementation(libs.kotlin.reflect)
    runtimeOnly(libs.postgresql)
}
