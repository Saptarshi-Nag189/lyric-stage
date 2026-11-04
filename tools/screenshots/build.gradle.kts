// Renders docs/screenshots with the app's own layout engine (core module) and Java2D.
// Run: ./render.sh   (needs Python fontTools for the static font weights)
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}
sourceSets["main"].kotlin.srcDirs("../../core/src/main/kotlin", "src")
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
application { mainClass.set("shots.RenderKt") }
