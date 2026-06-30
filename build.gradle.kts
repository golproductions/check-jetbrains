plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.golproductions"
version = "1.0.2"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.golproductions.check"
        name = "GOL Check - Anti-Hallucination Engine"
        version = "1.0.2"
        description = """
            The universal anti-hallucination engine. AI agents hallucinate. Check catches it
            before it reaches your project. Works with all JetBrains IDEs.
        """.trimIndent()
        vendor {
            name = "GOL Productions"
            url = "https://www.golproductions.com"
        }
        ideaVersion {
            sinceBuild = "241"
        }
    }
    instrumentCode = false
}

tasks {
    compileKotlin { kotlinOptions.jvmTarget = "17" }
    compileJava { sourceCompatibility = "17"; targetCompatibility = "17" }
}
