import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm {
        mainRun {
            mainClass = "de.voculupo.historicalcamplayout.MainKt"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
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
            implementation(compose.desktop.uiTestJUnit4)
        }
    }
}

android {
    namespace = "de.voculupo.historicalcamplayout"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "de.voculupo.historicalcamplayout"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
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
            packageVersion = "1.0.0"
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
