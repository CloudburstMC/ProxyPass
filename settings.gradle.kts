rootProject.name = "ProxyPass"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.4.0")
}

sourceControl {
    gitRepository(uri("https://github.com/Kas-tle/Protocol.git")) {
        producesModule("org.cloudburstmc.protocol:bedrock-codec")
        producesModule("org.cloudburstmc.protocol:common")
        producesModule("org.cloudburstmc.protocol:bedrock-connection")
    }
}
