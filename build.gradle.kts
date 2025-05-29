import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "uz.mobiledv"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation("org.apache.poi:poi-ooxml:5.4.0") // Use the latest version

    // For Log4j2
    implementation("org.apache.logging.log4j:log4j-api:2.17.1") // Or newer
    implementation("org.apache.logging.log4j:log4j-core:2.17.1") // Or newer
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.1") // If you use SLF4J

    implementation(project.dependencies.platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:auth-kt")

    implementation("io.insert-koin:koin-core:3.5.6") // Check for the latest version
    implementation("io.insert-koin:koin-test:3.5.6") // Optional: for testing
    implementation("io.insert-koin:koin-logger-slf4j:3.5.6") // Optional: for logging
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AKT"
            packageVersion = "1.0.0"
            description = "Hujjatlarni avtomatik to'ldirish dasturi"
            copyright = "© 2025 MobileDv"
            vendor = "MobileDv"

            macOS {
                bundleID = "uz.mobiledv.hujjattuldiruvchi" // Replace with your actual bundle ID
                iconFile.set(project.file("src/main/resources/icons/mac_icon.icns")) // Or your chosen path
                // You might also need to set:
                // dockName = "Hujjat To'ldiruvchi"
            }

            // For Windows (.msi)
            windows {
                // menuGroup = "My Application Suite" // Optional
                // shortcut = true // Optional
                iconFile.set(project.file("src/main/resources/icons/win_icon.ico")) // Or your chosen path
            }
        }
    }
}
