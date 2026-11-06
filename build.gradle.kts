plugins {
    `java-library`
    id("org.strangeeons.plugin")
}

group = "org.strangeeons.plugins"
version = "1.3.0-SNAPSHOT"
description = "Bulk image export plug-in for Strange Eons."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val strangeEonsDir = providers.gradleProperty("strangeEonsDir")
    .map { file(it) }
    .orElse(file("../../strange-eons"))

dependencies {
    // The app API is supplied by Strange Eons at runtime, not bundled here.
    compileOnly(files(strangeEonsDir.map { File(it, "deployment/assembled/strange-eons.jar") }))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
}

strangeEonsPlugin {
    bundleName = "BulkExport"
    bundleType = "seplugin"
}
