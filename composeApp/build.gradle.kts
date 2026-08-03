import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp)
    jacoco
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.room.ktx)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.java)
            implementation(libs.kotlinx.dateTime)
            // Room with bundled SQLite for Desktop
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.dateTime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ksoup)
            // Room with version catalog (using bundled SQLite for native support)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            // Navigation 3 for KMP. Exclude AndroidX Compose JVM stubs from desktop runtime:
            // they shadow JetBrains Compose classes and crash hotRunJvm with InlineClassHelper stubs.
            implementation("androidx.navigation3:navigation3-runtime:${libs.versions.androidx.navigation3.get()}") {
                exclude(group = "androidx.compose.animation")
                exclude(group = "androidx.compose.foundation")
                exclude(group = "androidx.compose.runtime")
                exclude(group = "androidx.compose.ui")
            }
            implementation("androidx.navigation3:navigation3-ui:${libs.versions.androidx.navigation3.get()}") {
                exclude(group = "androidx.compose.animation")
                exclude(group = "androidx.compose.foundation")
                exclude(group = "androidx.compose.runtime")
                exclude(group = "androidx.compose.ui")
            }
            implementation(libs.cash.paging.common)
            // Koin for DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.kotlinx.dateTime)
            implementation(libs.ktor.client.mock)
        }
    }
}

// Room KSP configuration for all targets
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/jvmMain/kotlin",
            "src/commonTest/kotlin",
            "src/jvmTest/kotlin",
        ),
    )
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    exclude("**/build/**")
    exclude("**/generated/**")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

android {
    namespace = "com.arny.habrrss"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    
    defaultConfig {
        applicationId = "com.arny.habrrss"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        checkTestSources = false
        ignoreTestSources = true
        sarifReport = true
        htmlReport = true
        xmlReport = true
        disable += setOf(
            "GradleDependency",
            "ObsoleteLintCustomCheck",
        )
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val coverageExcludes = listOf(
    "**/*Screen*.*",
    "**/*Scaffold*.*",
    "**/*Navigation*.*",
    "**/*Images*.*",
    "**/*Activity*.*",
    "**/*Preview*.*",
    "**/*ComposableSingletons*.*",
    "**/*Kt\$*Preview*.*",
    "**/Platform*.*",
    "**/RefreshBox*.*",
    "**/AppKt*.*",
    "**/ComponentsKt*.*",
    "**/ArticleActions*.*",
    "**/*ArticleActions*.*",
    "**/ArticleScrollContainer*.*",
    "**/Greeting*.*",
    "**/MainKt*.*",
    "**/generated/**",
    "habrrss/composeapp/generated/**",
    "**/Res*.*",
    "**/Drawable*.*",
    "**/ActualResourceCollectors*.*",
)

val jvmMainClasses = layout.buildDirectory.dir("classes/kotlin/jvm/main")
val jvmCoverageClassDirectories = files(
    jvmMainClasses.map { classesDir ->
        fileTree(classesDir) {
            exclude(coverageExcludes)
        }
    },
)

tasks.register<JacocoReport>("jacocoJvmReport") {
    dependsOn("jvmTest")

    executionData.setFrom(layout.buildDirectory.file("jacoco/jvmTest.exec"))
    classDirectories.setFrom(jvmCoverageClassDirectories)
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoJvmCoverageVerification") {
    dependsOn("jacocoJvmReport")

    executionData.setFrom(layout.buildDirectory.file("jacoco/jvmTest.exec"))
    classDirectories.setFrom(jvmCoverageClassDirectories)
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.register("qualityCheck") {
    dependsOn(
        "detekt",
        "lintDebug",
        "jvmTest",
        "testDebugUnitTest",
        "jacocoJvmCoverageVerification",
    )
}

compose.desktop {
    application {
        mainClass = "com.arny.habrrss.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.arny.habrrss"
            packageVersion = "1.0.0"
        }
    }
}
