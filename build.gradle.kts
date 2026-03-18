plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
}

kotlin {
    jvmToolchain(17)
}

intellij {
    version = providers.gradleProperty("platformVersion").get()
    type = providers.gradleProperty("platformType").get()
    plugins = providers.gradleProperty("platformBundledPlugins").get()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

tasks {
    processResources {
        // docs/skills/ is the single source of truth for the bundled skill Markdown files.
        // Copying them here avoids a duplicate copy in src/main/resources/skills/ that
        // could silently drift out of sync with the docs.
        from("docs/skills") {
            into("skills")
        }
    }

    patchPluginXml {
        sinceBuild = providers.gradleProperty("pluginSinceBuild").get()
        untilBuild.set("")  // ← Add this line to remove upper bound
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    signPlugin {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishPlugin {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
