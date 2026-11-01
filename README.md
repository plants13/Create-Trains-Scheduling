# Create: Trains Scheduling

[![License: MPL-2.0](https://img.shields.io/badge/License-MPL--2.0-brightgreen.svg)](LICENSE)

A NeoForge 1.21.1 addon for [Create](https://github.com/Creators-of-Create/Create) that adds train
dispatch tools, integrated into [Xaero's World Map](https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map)
and [JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap).

Current version: **0.0.5-alpha-1.21.1Neo-C6** (Minecraft 1.21.1 / NeoForge / Create 6).

## Features

- **Map overlay** (Xaero's World Map and JourneyMap) — station markers and per-carriage train highlight;
  the overlay follows Create's own train map overlay toggle
- **Click a train on the map** to open the dispatch menu:
  - **Set destination** — pick a station on the map; the destination is dispatched by station UUID,
    so duplicate station names can never send the train to the wrong station
  - **Plan route** — click several stations in order and confirm; intermediate stations are *passed
    through without stopping* when Steam 'n' Rails is installed (falls back to normal stops otherwise)
  - **Allow passing next red signal** — event-based: the grant expires right after the train actually
    passes a signal
  - **Show status** — server-authoritative state (can run automatically / has schedule / paused /
    override state) plus CRN circuit data (line / terminus / category)
  - **Disable schedule** — manually placed schedules are only *interrupted* (paused and kept);
    auto schedules created by this mod are cleared
  - **Resume schedule** — continues an interrupted schedule
- **Permission system** — normal players can't dispatch by default; OPs grant per-feature permissions
- **Commands** — `/cts trains`, `/cts stations`, `/cts route`, `/cts override`, `/cts clear`, `/cts perm`, `/cts selftest`
- **ComputerCraft bridge** (optional) — a read-only `cts_station` peripheral on Create station blocks, exposing dispatch state and CRN circuit data to Lua (station/schedule data comes from Create's own `Create_Station` peripheral)
- **Languages** — English and 简体中文

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219+ |
| Create | 6.0.10 |
| Create Railways Navigator | 0.10.0-C6+ (**hard dependency**) |
| DragonLib | 3.0.28+ (dependency of CRN) |
| Xaero's World Map **or** JourneyMap | 1.21.1, client only, optional (needed for the map UI) |
| Steam 'n' Rails | optional, needed for pass-through waypoints |
| ComputerCraft / CC:Tweaked | optional, enables the `cts_station` peripheral |
| Java | 21 |

## Installation

**Client** (needed for the map UI): Create, Create Railways Navigator, DragonLib, NeoForge, this mod,
and either Xaero's World Map or JourneyMap.

**Server**: Create, Create Railways Navigator, DragonLib, NeoForge, and this mod.
The map mods are client-side and do not need to be installed on the server.

## Usage

### Map workflow

1. Open the fullscreen map (Xaero's World Map or JourneyMap).
2. Left-click a train (hover first — all its carriages get a yellow highlight).
3. Use the menu:
   - **Set destination…** → all stations get a yellow frame; click one to send the train there
   - **Allow passing next red signal** → grants a one-shot override
   - **Show status** → expands server-authoritative train state
   - **Disable / resume schedule** → pauses and keeps manually placed schedules; clears auto schedules

`Esc` or right-click closes the menu / cancels station picking.

### Commands

| Command | Description |
| --- | --- |
| `/cts trains` | List trains with dispatch state |
| `/cts stations` | List station names |
| `/cts route <train> <station1,station2,...>` | Dispatch a one-off route |
| `/cts override <train> [ticks]` | Allow passing a red signal (no ticks = next signal; ticks = timed) |
| `/cts clear <train>` | Disable the train's schedule (manual schedules are kept) |
| `/cts perm grant <player> <dispatch\|override\|schedule>` | OP only: grant a permission |
| `/cts perm revoke <player> <permission>` | OP only: revoke a permission |
| `/cts perm list [player]` | OP only: list granted permissions |
| `/cts selftest` | Development self-test |

### Permissions

Normal players have **no** dispatch permissions by default. OPs always have all of them. OPs can grant
the following per player (stored in the world save, survives restarts):

- `dispatch` — set destinations / dispatch routes
- `override` — allow passing red signals
- `schedule` — disable / resume / clear schedules

The legacy `forceReleasePlayers` config entry still counts as `override`.

### ComputerCraft bridge

With CC:Tweaked installed, every Create station block exposes a read-only peripheral of type
`cts_station` (no new blocks or items — it is attached to Create's own station block entity).

This peripheral only adds **dispatch-specific state that Create does not expose**. Station names, train
presence, schedules and the `train_imminent` / `train_arrival` / `train_departure` events are already
provided by Create itself — use Create's `Create_Station` peripheral for those.

```lua
local station = peripheral.find("cts_station")

station.getTrains()                 -- trains on this station's track network (dispatch state)
station.getTrains(true)             -- all track networks
station.getTrain("车名或UUID")       -- single train: paused / completed / autoSchedule / overrides / speed
station.getCircuit("车名或UUID")     -- CRN circuit: line / terminus / category
station.getVersion()
```

The peripheral is **read-only** on purpose: dispatching still goes through the map UI / commands with
permission checks.

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/create_trains_scheduling-0.0.5-alpha-1.21.1Neo-C6.jar`.

Development self-checks (`./gradlew runData`) verify the JSON ↔ Schedule round-trip and that all
mixins apply.

## Known limitations (alpha)

- When a station name is duplicated on a train's track network, a single destination falls back to
  exact navigation (Create/CRN schedule displays will not show that trip), while multi-stop routes
  refuse to dispatch until the affected station is renamed.
- Pass-through waypoints require Steam 'n' Rails; without it intermediate stations are normal stops.
- Alpha software: back up your worlds.

## License

This project is licensed under the **Mozilla Public License 2.0** — see [LICENSE](LICENSE).

Third-party components and their licenses are listed in [NOTICE](NOTICE).
