// Root build file — versions pinned and verified compatible (AGP 8.5.2 + Kotlin 2.0.20 + Gradle 8.7)
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
    id("androidx.room") version "2.6.1" apply false
}
