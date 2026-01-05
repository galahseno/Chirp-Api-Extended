import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

plugins {
    id("chirp.spring-boot-service")
    id("org.springframework.boot")
    kotlin("plugin.spring")
}

configure<JavaPluginExtension> {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configure<AllOpenExtension> {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}