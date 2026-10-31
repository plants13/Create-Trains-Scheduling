# Create: Trains Scheduling

A NeoForge 1.21.1 addon for [Create](https://www.curseforge.com/minecraft/mc-mods/create) that adds
train scheduling tools.

## Versions

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219+ |
| Create | 6.0.10 |
| Flywheel | 1.0.6 |
| Ponder | 1.0.82 |
| Java | 21 |
| Gradle | 8.14.2 |
| ModDevGradle | 2.0.91 |

## Building

```bash
./gradlew build
```

The built jar ends up in `build/libs/`.

Run the game in a development environment:

```bash
./gradlew runClient
./gradlew runServer
./gradlew runData
```

## Note on networking (this machine)

`maven.neoforged.net` cannot be reached directly from this network (DNS is poisoned)
so Gradle is configured to use the local Clash proxy, see `gradle.properties`:

```properties
systemProp.http.proxyHost=<redacted>
systemProp.http.proxyPort=<redacted>
systemProp.https.proxyHost=<redacted>
systemProp.https.proxyPort=<redacted>
```

Remove those lines (or change the port) if you build on another machine or your proxy
settings differ. When the Gradle wrapper itself needs to download a new distribution,
also export `GRADLE_OPTS` with the same proxy settings.

## Dependency repositories

* Create / Ponder / Flywheel — <https://maven.createmod.net>
* Registrate — <https://mvn.devos.one/snapshots>
