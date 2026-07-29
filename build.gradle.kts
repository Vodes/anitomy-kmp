import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.4.10"
    `maven-publish`
}

group = "pw.vodes"
version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-local")

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

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
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
