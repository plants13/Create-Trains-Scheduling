# Create: Trains Scheduling

A NeoForge 1.21.1 addon for [Create](https://www.curseforge.com/minecraft/mc-mods/create) that adds
train dispatch tools: custom routes, red-signal override authorization, one-off route dispatch,
and an external timetable editor (JSON import/export).

Requires [Create Railways Navigator](https://modrinth.com/mod/create-railways-navigator) (hard dependency).

## Versions

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219+ |
| Create | 6.0.10 |
| CRN (Create Railways Navigator) | 0.10.0-C6+ |
| DragonLib | 3.0.28+ |
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

## Machine-local proxy setup (NOT committed)

Some networks cannot reach `maven.neoforged.net` directly (DNS is poisoned). Do **not** put proxy
settings in this repository's `gradle.properties` — GitHub Actions would pick them up and fail,
because the runner has no local proxy.

Put them in your user-level Gradle properties instead (`~/.gradle/gradle.properties`):

```properties
systemProp.http.proxyHost=<redacted>
systemProp.http.proxyPort=<redacted>
systemProp.https.proxyHost=<redacted>
systemProp.https.proxyPort=<redacted>
```

If the Gradle wrapper itself has to download a distribution, export `GRADLE_OPTS` with the same
`-D` proxy flags before running `./gradlew`.

## Dependency repositories

* Create / Ponder / Flywheel — <https://maven.createmod.net>
* Registrate — <https://mvn.devos.one/snapshots>
* Create Railways Navigator — <https://api.modrinth.com/maven> (use the version ID, e.g. `rlsRwxq3`)
* DragonLib — <https://maven.mrjulsen.net>
* DragonLib transitive deps (Architectury, ForgeConfigAPIPort) —
  <https://maven.architectury.dev> / <https://raw.githubusercontent.com/Fuzss/modresources/main/maven>
