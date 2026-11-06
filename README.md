# Bulk export plug-in for Strange Eons

This repository contains the source code for the [bulk image export plug-in](http://se3docs.cgjennings.ca/um-bulk-export.html).

**Note:** If you simply want to use this plug-in, install it from the [plug-in catalogue](http://se3docs.cgjennings.ca/um-plugins-catalogue.html). A quick way to do that is to copy this text: `eonscat:961f` and then switch to the Strange Eons window.

## Build

The build uses the [`strange-eons-gradle-plugin`](https://github.com/strange-eons/strange-eons-gradle-plugin) composite build to create a Strange Eons bundle. By default, Gradle looks for these checkouts at:

```
../../strange-eons-gradle-plugin/
../../strange-eons/
```

Override those locations with Gradle properties when needed:

```sh
./gradlew assemble \
  -PstrangeEonsGradlePluginDir=/path/to/strange-eons-gradle-plugin \
  -PstrangeEonsDir=/path/to/strange-eons
```

The Strange Eons checkout must have `deployment/assembled/strange-eons.jar` built. The app JAR is used only to compile against the Strange Eons API; it is not included in the plug-in bundle.

The packaged plug-in is written to `build/libs/BulkExport.seplugin`. To copy it into the current Strange Eons user's plug-in folder, run:

```sh
./gradlew installPlugin
```

## Release

Run `./gradlew release` on a clean, pushed `main` branch to create the release commit and version tag, then bump `gradle.properties` to the next snapshot. The task does not push commits or tags; review them and push with `git push --follow-tags` when ready.

## License

See [LICENSE](LICENSE).
