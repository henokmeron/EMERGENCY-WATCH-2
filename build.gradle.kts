// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// OneDrive locks files under Desktop\...\build — keep intermediates on a local disk.
val localBuildRoot = file("${System.getProperty("user.home")}/AppData/Local/EmergencyWatchBuild")
layout.buildDirectory.set(localBuildRoot.resolve("root"))
subprojects {
    layout.buildDirectory.set(localBuildRoot.resolve(name))
}