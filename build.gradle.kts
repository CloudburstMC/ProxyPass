import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

description = "Proxy pass allows developers to MITM a vanilla client and server without modifying them."

plugins {
    id("java")
    id("application")
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
    maven("https://repo.opencollab.dev/maven-releases")
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.spotbugs.annotations)
    implementation(libs.bedrock.codec)
    implementation(libs.bedrock.common)
    implementation(libs.bedrock.connection)
    implementation(libs.netty.transport.nethernet)
    implementation(libs.netty.transport.raknet)
    runtimeOnly(libs.libdatachannel.natives) {
        exclude(group = "dev.opencollab", module = "libdatachannel-java")
    }
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(platform(libs.log4j.bom))
    implementation(libs.log4j.api)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.log4j.core)
    runtimeOnly(libs.log4j.slf4j2.impl)
    implementation(libs.jansi)
    implementation(libs.jline.reader)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("cloudburst.validateEncryption", "true")
}

application {
    mainClass.set("org.cloudburstmc.proxypass.ProxyPass")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    failOnDuplicateEntries = true

    filesMatching(
        listOf(
            "META-INF/services/**",
            "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat"
        )
    ) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    manifest {
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }
    transform(Log4j2PluginsCacheFileTransformer())
    mergeServiceFiles()
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir.resolve("run")
    workingDir.mkdir()
}

tasks.named("distZip") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("distTar") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("startScripts") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("startShadowScripts") {
    dependsOn(tasks.named("jar"))
}
