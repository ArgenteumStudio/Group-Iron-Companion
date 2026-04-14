BowlsofMayo RuneLite plugin
===========================

This folder contains the RuneLite plugin source for the BowlsofMayo clan tracker.

Build
-----

1. Install Maven (3.6+) and JDK (matching your RuneLite client, 8+).
2. From this folder run:

```bash
mvn clean package
```

Notes:
- The POM includes a `runelite.version` property you may need to change to match your RuneLite client version.
- Dependencies for RuneLite are marked `provided` so the produced jar will not bundle the client.

Install locally (private use)
----------------------------

- After `mvn package` you will find the artifact at `target/bowlsofmayo-rune-lite-plugin-0.1.0.jar`.
- To use in the RuneLite client, copy that jar into your RuneLite plugins directory (or your RuneLite build's `plugins/` folder) and restart RuneLite.

Convenience script
-------------------

There is a helper PowerShell script `build_and_install.ps1` in this folder that will attempt to build the plugin using your local `mvn` or, if not available, Docker. It can optionally copy the produced jar into your RuneLite `plugins` folder.

Example (PowerShell):

```powershell
cd C:\Users\John\Desktop\bowlsofmayo\rune-lite-plugin
# build only
.\build_and_install.ps1
# build and install to a specific folder
.\build_and_install.ps1 -InstallDir 'C:\Path\To\RuneLite\plugins\'
```

Configuration
-------------

- Open RuneLite settings and enable "BowlsofMayo Clan Tracker". In the plugin config set `Server URL` to `http://localhost:4000` (or your hosted bridge URL) and `Clan Name` to `BowlsofMayo`.

Privacy
-------

- This repository and built artifacts are intended for private use only. Do not publish the plugin jar or private `config.json` files to public package registries or public Git repos.

Troubleshooting
---------------

- If Maven cannot resolve RuneLite artifacts, update `<runelite.version>` in `pom.xml` to match a version available in the configured repository, or build inside the RuneLite dev environment.
- If you prefer, you can also build the plugin as part of the RuneLite workspace (recommended for development/debugging).
