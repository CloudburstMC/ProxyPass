import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

description = "Proxy pass allows developers to MITM a vanilla client and server without modifying them."

plugins {
    id("java")
    id("application")
    @Suppress("DSL_SCOPE_VIOLATION") // Temp workaround for KTIJ-19369 until Gradle 8.1 is released
    alias(libs.plugins.shadow)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-snapshots")
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.jsr305)
    implementation(libs.bedrock.connection)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.common)
    implementation(libs.jansi)
    implementation(libs.jline.reader)
}

application {
    mainClass.set("org.cloudburstmc.proxypass.ProxyPass")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveVersion.set("")
    transform(Log4j2PluginsCacheFileTransformer())
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir.resolve("run")
    workingDir.mkdir()
}
