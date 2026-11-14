import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

kotlin {
    // Shared code as an Android library; the installable app shell lives in :androidApp (AGP 9 split).
    android {
        namespace = "de.voculupo.historicalcamplayout.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        androidResources {
            // Compose Multiplatform resources (strings, icons, parchment) are packaged as Android resources.
            enable = true
        }
    }

    jvm {
        mainRun {
            mainClass = "de.voculupo.historicalcamplayout.MainKt"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            api(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            api(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        jvmTest.dependencies {
            implementation(libs.compose.uiTestJunit4)
        }
    }
}

dependencies {
    ktlintRuleset(libs.compose.rules.ktlint)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(files("$rootDir/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    setSource(
        files(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/jvmMain/kotlin",
            "src/commonTest/kotlin",
        ),
    )
    include("**/*.kt", "**/*.kts")
    exclude("**/build/**")
}

ktlint {
    version.set("1.8.0")
    filter {
        include("**/src/**/*.kt")
        include("**/*.kts")
        exclude("**/build/**")
        exclude { element -> element.file.path.contains("${File.separator}build${File.separator}") }
    }
}

compose.desktop {
    application {
        mainClass = "de.voculupo.historicalcamplayout.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Product name for desktop distributions.
            packageName = "Lagerplaner"
            packageVersion = System.getenv("APP_VERSION") ?: "1.0.0"
            description = "Zelt- und Lagerflaechenplanung fuer Maerkte"
            copyright = "(c) Benedikt Konrad"
            vendor = "Benedikt Konrad"

            linux {
                debMaintainer = "Benedikt Konrad"
            }

            windows {
                // Desktop shortcut and start menu entry (MSI/EXE installer).
                shortcut = true
                menu = true
                menuGroup = "Lagerplaner"
                iconFile.set(project.file("branding/windows/lagerplaner.ico"))
            }
        }
    }
}
