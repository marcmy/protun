/*
 * Copyright (c) 2025. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    alias(coreLibs.plugins.android.library)
    alias(coreLibs.plugins.kotlin.android)
    alias(libs.plugins.vanniktech.mavenpublish)
    id("kotlin-parcelize")
}

// By default (standalone library) vpn-core-rust will provide rust library with bindings. In embedded mode
// parent library will provide alternative rust module.
private val rustProviderModule = findProperty("protunCoreRustProviderModule") as String? ?: ":vpn-core-rust"
private val coreArtifactId = findProperty("protunCoreArtifactId") as String? ?: "vpn-core"
private val versionName = findProperty("protunCoreVersionName") as? String ?: getRepoVersionName()

private val githubRepo = "github.com/ProtonVPN/protun"

android {
    namespace = "me.proton.vpn.core"
    compileSdk = coreLibs.versions.compileSdk.get().toInt()
    ndkVersion = "28.1.13356709"

    defaultConfig {
        aarMetadata {
            minCompileSdk = coreLibs.versions.minCompileSdk.get().toInt()
        }
        minSdk = coreLibs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Let's leave it to library users to minify and optimize.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            packaging.jniLibs.keepDebugSymbols.add("**/*.so")
        }
    }

    // Java 11 is used because of dokka issue with publishing with Java 17:
    // https://github.com/Kotlin/dokka/issues/2956
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    mavenPublishing {
        publishToMavenCentral(automaticRelease = true)
        signAllPublications()

        val groupId = "me.proton.vpn"
        val artifactId = coreArtifactId

        coordinates(groupId, artifactId, versionName)
        pom {
            name = "$groupId:$artifactId"
            description = "Proton VPN core libraries for Android"
            url = "https://protonvpn.com"
            licenses {
                license {
                    name = "GNU GENERAL PUBLIC LICENSE, Version 3.0"
                    url = "https://www.gnu.org/licenses/gpl-3.0.en.html"
                }
            }
            developers {
                developer {
                    id = "opensource@proton.me"
                    name = "Open Source Proton"
                    email = "opensource@proton.me"
                }
            }
            scm {
                connection = "scm:git:git://${githubRepo}.git"
                developerConnection = "scm:git:ssh://${githubRepo}.git"
                url = "https://${githubRepo}"
            }
        }
    }
}

dependencies {
    implementation(coreLibs.androidx.annotation)
    implementation(coreLibs.androidx.core)
    implementation(coreLibs.core.ktx)
    implementation(coreLibs.coroutines.core)
    implementation(coreLibs.coroutines.android)
    api(project(rustProviderModule))
}

// Read the crate version from protun's Cargo.toml ([package] version).
fun getRepoVersionName(): String {
    val cargoToml = file("../../Cargo.toml")
    if (!cargoToml.isFile) {
        throw RuntimeException("Unable to find crate manifest at $cargoToml")
    }
    val lines = cargoToml.readLines()
    val packageStart = lines.indexOfFirst { it.trim() == "[package]" }
    if (packageStart < 0) {
        throw RuntimeException("No [package] section in $cargoToml")
    }

    // First `version = "..."` within the [package] table
    val versionRegex = Regex("""^\s*version\s*=\s*"([^"]+)""")
    for (line in lines.drop(packageStart + 1)) {
        if (line.trimStart().startsWith("[")) break
        versionRegex.find(line)?.let { return it.groupValues[1] }
    }
    throw RuntimeException("No version found in [package] of $cargoToml")
}
