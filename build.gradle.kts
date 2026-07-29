import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "2.4.10"
    `maven-publish`
}

group = "pw.vodes"
version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-local")

val hostOsName = System.getProperty("os.name").lowercase()
val hostIsLinux = hostOsName.contains("linux")
val hostIsWindows = hostOsName.contains("windows")
val hostIsMacOs = hostOsName.contains("mac") || hostOsName.contains("darwin")
val hostArchitecture = System.getProperty("os.arch").lowercase()
val hostNativeTarget =
    when {
        hostIsLinux && hostArchitecture in setOf("x86_64", "amd64") -> "linuxX64"
        hostIsLinux && hostArchitecture in setOf("aarch64", "arm64") -> "linuxArm64"
        hostIsWindows && hostArchitecture in setOf("x86_64", "amd64") -> "mingwX64"
        hostIsMacOs && hostArchitecture in setOf("aarch64", "arm64") -> "macosArm64"
        else -> null
    }
val hostResourceDirectory =
    when (hostNativeTarget) {
        "linuxX64" -> "linux-x64"
        "linuxArm64" -> "linux-arm64"
        "mingwX64" -> "windows-x64"
        "macosArm64" -> "macos-arm64"
        else -> null
    }
val hostJniLibraryName =
    when {
        hostIsWindows -> "anitomy-kmp.dll"
        hostIsMacOs -> "libanitomy-kmp.dylib"
        else -> "libanitomy-kmp.so"
    }
val hostStaticLibraryName = if (hostIsWindows) "anitomy-bridge.lib" else "libanitomy-bridge.a"

val hostNativeBuildDirectory = layout.buildDirectory.dir("native/host")
val configureHostNative by tasks.registering(Exec::class) {
    group = "native"
    description = "Configures the native Anitomy bridge for the current host."

    inputs.files(
        fileTree("native") {
            include("CMakeLists.txt", "include/**", "src/**")
        },
        fileTree("vendor/anitomy/include"),
    )
    outputs.file(hostNativeBuildDirectory.map { it.file("CMakeCache.txt") })

    commandLine(
        "cmake",
        "-S",
        file("native").absolutePath,
        "-B",
        hostNativeBuildDirectory.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DANITOMY_KMP_BUILD_JNI=ON",
        "-DANITOMY_KMP_BUILD_TESTS=OFF",
    )
}

val buildHostNative by tasks.registering(Exec::class) {
    group = "native"
    description = "Builds the native Anitomy bridge and JNI library for the current host."
    dependsOn(configureHostNative)

    inputs.file(hostNativeBuildDirectory.map { it.file("CMakeCache.txt") })
    outputs.files(
        hostNativeBuildDirectory.map { it.file(hostJniLibraryName) },
        hostNativeBuildDirectory.map { it.file(hostStaticLibraryName) },
    )

    commandLine(
        "cmake",
        "--build",
        hostNativeBuildDirectory.get().asFile.absolutePath,
        "--config",
        "Release",
        "--parallel",
    )
}

val generatedJvmResources = layout.buildDirectory.dir("generated/jvmResources")
val prepareJvmNativeResources by tasks.registering(Sync::class) {
    group = "native"
    description = "Prepares bundled JNI libraries for the JVM publication."
    into(generatedJvmResources)

    val prebuiltDirectory = providers.gradleProperty("anitomy.jvmNativesDir")
    if (prebuiltDirectory.isPresent) {
        from(prebuiltDirectory)
    } else if (hostResourceDirectory != null) {
        dependsOn(buildHostNative)
        from(hostNativeBuildDirectory.map { it.file(hostJniLibraryName) }) {
            into("META-INF/anitomy-kmp/$hostResourceDirectory")
        }
    }
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withSourcesJar()
    }

    linuxX64()
    linuxArm64()
    mingwX64()
    macosArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        val nativeTarget = name
        val propertyName = "anitomy.nativeLibraryDir.$nativeTarget"
        val prebuiltDirectory = providers.gradleProperty(propertyName)
        val defaultLibrary =
            hostNativeBuildDirectory.map {
                it.file(hostStaticLibraryName)
            }
        val libraryDirectory =
            if (prebuiltDirectory.isPresent) {
                file(prebuiltDirectory.get())
            } else {
                defaultLibrary.get().asFile.parentFile
            }
        val bridgeLibrary =
            if (nativeTarget == "mingwX64") "libanitomy-bridge.a" else "libanitomy-bridge.a"
        val bundledRuntimeLibraries =
            when {
                !prebuiltDirectory.isPresent -> emptyList()
                nativeTarget.startsWith("linux") ->
                    listOf("libstdc++.a", "libgcc.a", "libgcc_eh.a")
                nativeTarget == "mingwX64" ->
                    listOf("libstdc++.a", "libgcc.a", "libgcc_eh.a", "libwinpthread.a")
                else -> emptyList()
            }

        compilations.getByName("main").cinterops.create("anitomy") {
            definitionFile.set(file("src/nativeInterop/cinterop/anitomy.def"))
            includeDirs.headerFilterOnly(file("native/include"))
            extraOpts(
                "-libraryPath",
                libraryDirectory.absolutePath,
                "-staticLibrary",
                bridgeLibrary,
            )
            bundledRuntimeLibraries.forEach { runtimeLibrary ->
                extraOpts("-staticLibrary", runtimeLibrary)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain {
            resources.srcDir(generatedJvmResources)
        }
    }
}

if (hostNativeTarget != null) {
    val hostCInteropTask = "cinteropAnitomy${hostNativeTarget.replaceFirstChar(Char::uppercase)}"
    tasks.matching { it.name == hostCInteropTask }.configureEach {
        dependsOn(buildHostNative)
    }
}

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(prepareJvmNativeResources)
}

publishing {
    repositories {
        maven {
            name = "Styx"
            url = uri("https://repo.styx.moe/releases")
            credentials {
                username = System.getenv("STYX_REPO_TOKEN")
                password = System.getenv("STYX_REPO_SECRET")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Anitomy KMP")
            description.set("Kotlin Multiplatform bindings for Anitomy v2")
            url.set("https://github.com/Vodes/anitomy-kmp")
            licenses {
                license {
                    name.set("Mozilla Public License 2.0")
                    url.set("https://www.mozilla.org/MPL/2.0/")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("Vodes")
                    name.set("Vodes")
                    url.set("https://github.com/Vodes")
                }
            }
            scm {
                url.set("https://github.com/Vodes/anitomy-kmp")
                connection.set("scm:git:https://github.com/Vodes/anitomy-kmp.git")
                developerConnection.set("scm:git:ssh://git@github.com/Vodes/anitomy-kmp.git")
            }
        }
    }
}
