pluginManagement {
    val gradlePluginDir = providers.gradleProperty("strangeEonsGradlePluginDir")
        .getOrElse("../../strange-eons-gradle-plugin")
    includeBuild(file(gradlePluginDir))
}

rootProject.name = "strange-eons-bulk-export-plugin"
