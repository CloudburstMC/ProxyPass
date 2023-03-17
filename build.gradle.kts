import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

description = "Proxy pass allows developers to MITM a vanilla client and server without modifying them."

plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "8.0.0"
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
    compileOnly("org.projectlombok:lombok:1.18.26")
    annotationProcessor("org.projectlombok:lombok:1.18.26")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.cloudburstmc.protocol:bedrock-connection:3.0.0.Beta1-SNAPSHOT")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.4")
    implementation("com.nukkitx:common:1.0.1-SNAPSHOT")
    implementation("org.fusesource.jansi:jansi:2.4.0")
    implementation("org.jline:jline-reader:3.20.0")
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
