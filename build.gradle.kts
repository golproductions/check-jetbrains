plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.golproductions"
version = "1.0.18"

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
        name = "GOL Check - Anti-Hallucination Layer"
        version = "1.0.18"
        vendor {
            name = "GOL Productions"
            url = "https://www.golproductions.com"
        }
        ideaVersion {
            sinceBuild = "241"
            // No upper bound: the gradle plugin otherwise defaults untilBuild to the
            // target branch (241.*), which locked out every IDE newer than 2024.1.
            untilBuild = provider { null }
        }
    }
    instrumentCode = false
}

tasks {
    compileKotlin { kotlinOptions.jvmTarget = "17" }
    compileJava { sourceCompatibility = "17"; targetCompatibility = "17" }
}
