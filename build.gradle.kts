plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.golproductions"
version = "1.0.0"

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
        name = "GOL Check - Anti-Hallucination Firewall"
        version = "1.0.0"
        description = """
            Validates AI-generated commands before execution. Catches hallucinated commands,
            fake APIs, and invalid operations before they break your project.

            Works with all JetBrains IDEs: IntelliJ IDEA, WebStorm, PyCharm, GoLand,
            Rider, PHPStorm, RubyMine, and more.
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
