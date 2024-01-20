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

tasks.distTar {
    dependsOn("shadowJar");
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.distZip {
    dependsOn("shadowJar");
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.startScripts {
    dependsOn("shadowJar");
}

tasks.startShadowScripts {
    dependsOn("jar");
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
    compileOnly(libs.jsr305)
    implementation(files("C:\\Users\\15425\\IdeaProjects\\Protocol\\bedrock-codec\\build\\libs\\bedrock-codec-3.0.0.Beta1-SNAPSHOT.jar"))
    implementation(libs.bedrock.common)
    implementation(libs.bedrock.connection)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.common)
    implementation(libs.jansi)
    implementation(libs.reflections)
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
