pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://dl.bintray.com/rikkaw/Shizuku") }
        maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
        // ffmpeg-kit-local.aar 等本地 aar
        flatDir { dirs(file("app/libs")) }
    }
}

rootProject.name = "Operit"

include(":app")

// ---- A 模式：原生 proot Ubuntu 终端（OperitTerminalCore）----
include(":terminal")

// ---- B 模式：Termux 终端内核（内嵌源码，非 submodule）----
include(":termuxlib:termux-shared")
include(":termuxlib:terminal-emulator")
include(":termuxlib:terminal-view")

include(":showerclient")
include(":quickjs")
