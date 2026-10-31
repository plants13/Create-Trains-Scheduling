# Create: Trains Scheduling

[![License: MPL-2.0](https://img.shields.io/badge/License-MPL--2.0-brightgreen.svg)](LICENSE)

A NeoForge 1.21.1 addon for [Create](https://github.com/Creators-of-Create/Create) that adds train
dispatch tools, integrated into [Xaero's World Map](https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map).

Current version: **0.0.1-alpha**.

## Features

- **Xaero world map overlay** — station markers and per-carriage train highlight
- **Click a train on the map** to open the dispatch menu:
  - **Set destination** — pick a station on the map; the destination is dispatched by station UUID,
    so duplicate station names can never send the train to the wrong station
  - **Allow passing next red signal** — event-based: the grant expires right after the train actually
    passes a signal
  - **Show status** — server-authoritative state (can run automatically / has schedule / override state)
  - **Clear schedule**
- **Commands** — `/cts trains`, `/cts stations`, `/cts route`, `/cts override`, `/cts clear`, `/cts selftest`
- **Languages** — English and 简体中文

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219+ |
| Create | 6.0.10 |
| Create Railways Navigator | 0.10.0-C6+ (**hard dependency**) |
| DragonLib | 3.0.28+ (dependency of CRN) |
| Xaero's World Map | 1.21.1 (client only, optional but required for the map UI) |
| Java | 21 |

## Installation

**Client** (needed for the map UI): Create, Create Railways Navigator, DragonLib, Xaero's World Map,
NeoForge, and this mod.

**Server**: Create, Create Railways Navigator, DragonLib, NeoForge, and this mod.
Xaero's World Map is client-side and does not need to be installed on the server.

## Usage

### Map workflow

1. Open Xaero's World Map.
2. Left-click a train (hover first — all its carriages get a yellow highlight).
3. Use the menu:
   - **Set destination…** → all stations get a yellow frame; click one to send the train there
   - **Allow passing next red signal** → grants a one-shot override
   - **Show status** → expands server-authoritative train state
   - **Clear schedule**

`Esc` or right-click closes the menu / cancels station picking.

### Commands

| Command | Description |
| --- | --- |
| `/cts trains` | List trains with dispatch state |
| `/cts stations` | List station names |
| `/cts route <train> <station1,station2,...>` | Dispatch a one-off route |
| `/cts override <train> [ticks]` | Allow passing a red signal (no ticks = next signal; ticks = timed) |
| `/cts clear <train>` | Clear the train's schedule |
| `/cts selftest` | Development self-test |

Permissions: dispatching a destination requires OP or being the train owner; red-signal override
requires OP or membership in `forceReleasePlayers` (config).

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/create_trains_scheduling-0.0.1-alpha.jar`.

Development self-checks (`./gradlew runData`) verify the JSON ↔ Schedule round-trip and that all
mixins apply.

## Known limitations (alpha)

- Only Xaero's World Map is supported; JourneyMap is not integrated yet.
- When a station name is duplicated on a train's track network, the destination falls back to exact
  navigation — Create/CRN schedule displays will not show that trip.
- Alpha software: back up your worlds.

## License

This project is licensed under the **Mozilla Public License 2.0** — see [LICENSE](LICENSE).

Third-party components and their licenses are listed in [NOTICE](NOTICE).
