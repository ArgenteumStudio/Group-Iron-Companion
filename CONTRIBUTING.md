# Plugin development

This repository contains the RuneLite plugin only. The server, web dashboard,
Discord bot and their tests are maintained separately in
[GroupIronCompanion-Backend](https://github.com/ArgenteumStudio/GroupIronCompanion-Backend).

## Build and check

Use Java 11 or newer and the included Gradle wrapper. From this repository's root
on Windows:

```powershell
.\gradlew.bat jar verifyFeatures
```

The optional helper performs the same build and checks:

```powershell
.\build_and_install.ps1
```

To compile against a local RuneLite client JAR, pass its full path:

```powershell
.\build_and_install.ps1 -ClientJar 'C:\path\to\client.jar'
```

The helper does not install or launch the plugin.

## Development launcher

Launch the plugin in RuneLite developer mode:

```powershell
.\gradlew.bat run
```

The launcher uses `com.bowlsofmayo.DevelopmentLauncher`. For server setup or
dashboard previews, follow the separate backend repository's README; no backend
code or JavaScript dependencies are included in this plugin repository.

## Code layout

The plugin coordinates RuneLite events and lifecycle. Upload filtering and
retries live in `UploadCoordinator`, status ownership in `SyncStatus`, bank
collection in `BankSnapshots`, and image decoding/scaling in `TrackerImages`.

The editable companion-shield artwork is in
[assets/group-iron-companion.svg](assets/group-iron-companion.svg).
