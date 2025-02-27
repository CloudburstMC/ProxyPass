import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

description = "Proxy pass allows developers to MITM a vanilla client and server without modifying them."

plugins {
    id("java")
    id("application")
    alias(libs.plugins.shadow)
    id("org.openjfx.javafxplugin") version "0.0.14"
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
    maven("https://repo.opencollab.dev/maven-releases")
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.spotbugs.annotations)
    implementation(libs.bedrock.codec)
    implementation(libs.bedrock.common)
    implementation(libs.bedrock.connection)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.common)
    implementation(libs.jansi)
    implementation(libs.jline.reader)
}

// 配置 JavaFX 插件
javafx {
    version = "17.0.14"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

application {
    mainClass.set("org.cloudburstmc.proxypass.ProxyPass")
}

tasks.shadowJar {
    archiveBaseName.set("ProxyPass")
    archiveClassifier.set("")
    archiveVersion.set("")
    transform(Log4j2PluginsCacheFileTransformer())
    manifest {
        attributes["Main-Class"] = "org.cloudburstmc.proxypass.ProxyPass"
    }
}


tasks.jar {
    enabled = false
}

tasks.named("distZip") {
    dependsOn(tasks.shadowJar)
}

tasks.named("distTar") {
    dependsOn(tasks.shadowJar)
}

tasks.named("startScripts") {
    dependsOn(tasks.shadowJar)
}

tasks.named("startShadowScripts", CreateStartScripts::class) {
    dependsOn(tasks.shadowJar)
    classpath = files(tasks.shadowJar.flatMap { it.archiveFile })
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir.resolve("run")
    workingDir.mkdir()
}
