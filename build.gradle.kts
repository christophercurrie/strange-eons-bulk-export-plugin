plugins {
    `java-library`
    id("org.strangeeons.plugin") version "1.0.0"
    id("net.researchgate.release") version "3.2.0"
}

group = "org.strangeeons.plugins"
version = providers.gradleProperty("version").get()
description = "Bulk image export plug-in for Strange Eons."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val strangeEonsDir = providers.gradleProperty("strangeEonsDir")
    .map { file(it) }
    .orElse(file("../../strange-eons"))

repositories {
    mavenCentral()
}

val strangeEonsJar = files(strangeEonsDir.map { File(it, "deployment/assembled/strange-eons.jar") })

dependencies {
    // The app API is supplied by Strange Eons at runtime, not bundled here.
    compileOnly(strangeEonsJar)

    testImplementation(strangeEonsJar)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
}

strangeEonsPlugin {
    bundleName = "BulkExport"
    bundleType = "seplugin"
}

release {
    // The release plugin runs its buildTasks through a nested GradleBuild,
    // which cannot include this project's composite build. Run the build in
    // the outer invocation instead.
    buildTasks.set(emptyList())
    git {
        // Create release commits and tags locally; review and push explicitly.
        pushToRemote = false
    }
}

tasks.named("runBuildTasks") {
    enabled = false
}

tasks.named("release") {
    dependsOn("assemble")
}
