import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import groovy.util.Node

description = "Proxy pass allows developers to MITM a vanilla client and server without modifying them."

plugins {
    id("eclipse")
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
    //mavenLocal()
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-snapshots")
    maven("https://repo.opencollab.dev/maven-releases")
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.jsr305)
    implementation(libs.bedrock.codec) { version { branch = "3.0-proxypass" } }
    implementation(libs.bedrock.common) { version { branch = "3.0-proxypass" } }
    implementation(libs.bedrock.connection) { version { branch = "3.0-proxypass" } }
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.common)
    implementation(libs.jansi)
    implementation(libs.jline.reader)
    implementation(libs.minecraftauth)
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

listOf("distZip", "distTar", "startScripts").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn("shadowJar")
    }
}

tasks.named("startShadowScripts") {
    dependsOn("jar")
}

eclipse {
    classpath {
        file {
            withXml {
                val classpath = asNode()
                val compositeBuildModuleNames = mutableSetOf<String>()
                configurations["runtimeClasspath"].resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    val id = artifact.id.componentIdentifier
                    if (id is ProjectComponentIdentifier && !id.build.isCurrentBuild) {
                        val classpathEntry = classpath.appendNode("classpathentry")
                        classpathEntry.attributes().put("kind", "lib")
                        classpathEntry.attributes().put("path", artifact.file.absolutePath)
                        compositeBuildModuleNames.add(artifact.moduleVersion.id.name)
                    }
                }
                classpath.children().listIterator().apply {
                    while (hasNext()) {
                        val entry = next() as Node
                        val kind = entry.attribute("kind") as? String
                        val path = entry.attribute("path") as? String
                        if (kind == "src" && path != null && compositeBuildModuleNames.any { path.endsWith(it) }) {
                            remove()
                        }
                    }
                }
            }
        }
    }
}