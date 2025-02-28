plugins {
    id("com.android.application") version "8.8.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
