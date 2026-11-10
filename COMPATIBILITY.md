# Version Compatibility (Client × Server)

[简体中文](COMPATIBILITY_ZH.md)

This table describes **inter-version compatibility of this mod only** (Minecraft 1.21.1 / NeoForge build).
It answers one question: if a client and a server run different versions of this mod, can the client join,
and which features stop working?

- Rows: **client** version.
- Columns: **server** version.
- ✅ join OK, all features supported by both sides work
- ⚠️ join OK, but the listed features are unavailable in this pairing
- ❌ cannot join (the connection is refused during the configuration/handshake or registry sync phase)

## Client mod requirement

Create registers its built-in registries as **synced** registries. Starting with 0.0.8 this mod adds an
entry to `create:display_source` (`create_trains_scheduling:departure_countdown`), so a 0.0.8+ server sends a
registry entry that clients without this mod — or with an older version of it — do not know.

Since 0.0.10 the server can skip that registration with the startup config
`registerDepartureCountdown = false` (see below). 0.0.8 – 0.0.9b always register the entry.

| Server version | Client without this mod | Client with an older mod version |
| --- | --- | --- |
| 0.0.1 – 0.0.7 | can join; map UI, clipboard editor and display features are unavailable | see matrix below |
| 0.0.8 – 0.0.9b | cannot join (registry sync fails with an "unknown key" error); no workaround | cannot join either — the client must be 0.0.8 or newer |
| 0.0.10, default (`registerDepartureCountdown = true`) | cannot join (same registry error) | client must be 0.0.8 or newer |
| 0.0.10, `registerDepartureCountdown = false` | can join; the station departure countdown (DCD) and its display links are unavailable | can join; everything the client version and the server version both support works, except DCD |

The switch lives in `config/create_trains_scheduling-startup.toml`, is read at startup, and requires a
server restart. It affects **every** client, modded or not: while it is off, nobody sees a departure
countdown.

## Protocol groups

Several releases share the same network protocol. They are grouped in the matrix below.

| Group | Releases | Notes |
| --- | --- | --- |
| P1 | 0.0.1 | Initial protocol. Payload channels are **non-optional**; old `TrainStatusPayload` layout. |
| P2 | 0.0.2 | Payload channels are **non-optional**; status payload uses the flags layout; permissions and manual-schedule interrupt added. |
| P3 | 0.0.3, 0.0.3a, 0.0.3b, 0.0.4, 0.0.5 | Payload channels are **optional**; identical network code. 0.0.3a/0.0.3b changed client rendering only; 0.0.4 added the ComputerCraft bridge; 0.0.5 deduplicated it. None of these changed the wire protocol. |
| P4 | 0.0.6 | Superset of P3: adds optional clipboard/editor channels. |
| P5 | 0.0.7 | Superset of P4: adds the optional GUI train-schedule action channel. |
| P6 | 0.0.8, 0.0.8a, 0.0.9, 0.0.9a, 0.0.9b, 0.0.10 | Same wire protocol as P5, but adds a **synced** `create:display_source` registry entry. 0.0.9 adds optional station-door-radius channels and server-side separation queue behaviour. 0.0.10 adds no channels; it adds the startup config that can skip the synced entry. |

## Matrix

| Client ↓ / Server → | 0.0.1 | 0.0.2 | 0.0.3 – 0.0.5 | 0.0.6 | 0.0.7 | 0.0.8 – 0.0.8a | 0.0.9 – 0.0.10 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **0.0.1** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **0.0.2** | ❌ | ✅ | ⚠️ MS / CRN / QD / ED* | ⚠️ MS / CRN / ED* | ⚠️ MS / CRN / ED / TSE* | ❌ | ❌ |
| **0.0.3** | ❌ | ⚠️ MS / CRN | ✅ | ⚠️ ED | ⚠️ ED / TSE | ❌ | ❌ |
| **0.0.3a** | ❌ | ⚠️ MS / CRN | ✅ | ⚠️ ED | ⚠️ ED / TSE | ❌ | ❌ |
| **0.0.3b** | ❌ | ⚠️ MS / CRN (QD works) | ✅ | ⚠️ ED | ⚠️ ED / TSE | ❌ | ❌ |
| **0.0.4** | ❌ | ⚠️ MS / CRN (QD works) | ✅ | ⚠️ ED | ⚠️ ED / TSE | ❌ | ❌ |
| **0.0.5** | ❌ | ⚠️ MS / CRN (QD works) | ✅ | ⚠️ ED | ⚠️ ED / TSE | ❌ | ❌ |
| **0.0.6** | ❌ | ⚠️ MS / CRN / ED (QD works) | ⚠️ ED | ✅ | ⚠️ TSE | ❌ | ❌ |
| **0.0.7** | ❌ | ⚠️ MS / CRN / ED / TSE (QD works) | ⚠️ ED / TSE | ⚠️ TSE | ✅ | ❌ | ❌ |
| **0.0.8 – 0.0.8a** | ❌ | ⚠️ MS / CRN / ED / TSE / DCD (QD works) | ⚠️ ED / TSE / DCD | ⚠️ TSE / DCD | ⚠️ DCD | ✅ | ⚠️ DR |
| **0.0.9 – 0.0.10** | ❌ | ⚠️ MS / CRN / ED / TSE / DCD / DQ / DR (QD works) | ⚠️ ED / TSE / DCD / DQ / DR | ⚠️ TSE / DCD / DQ / DR | ⚠️ DCD / DQ / DR | ⚠️ DQ / DR | ✅ † |

\* A 0.0.2 client does not have the UI for these features at all. The cell only says the server-side
features are not reachable from this client, or that the client has no UI for them.

† With the server's default `registerDepartureCountdown = true`. A 0.0.10 server with
`registerDepartureCountdown = false` can still be joined by every client, but DCD is unavailable for all of
them (see [Client mod requirement](#client-mod-requirement)).

0.0.9 – 0.0.10 are join-compatible with 0.0.8 – 0.0.8a in both directions: the synced registry entry is
identical and 0.0.9's new channels are optional. The separation queue (DQ) needs a 0.0.9+ server; the door
radius setting (DR) needs a 0.0.9+ server to apply and 0.0.9+ on both sides to configure.

Feature codes:

| Code | Feature | Requires |
| --- | --- | --- |
| MS | Multi-stop custom routes (pass-through via Steam 'n' Rails) | client ≥ 0.0.3 and server ≥ 0.0.3 |
| CRN | CRN circuit info (line / terminus / category) in the train status panel | client ≥ 0.0.3 and server ≥ 0.0.3 |
| QD | Quick destination (click a station while the train menu is open) | client ≥ 0.0.3b and server ≥ 0.0.2 |
| ED | Schedule editor commands (`/cts getcrn`, `/cts getsch`, `/cts writesch`) + clipboard channels | client ≥ 0.0.6 and server ≥ 0.0.6 |
| TSE | Train schedule export/import from the map dispatch menu (live schedule + run step) | client ≥ 0.0.7 and server ≥ 0.0.7 |
| DCD | Departure countdown display source on station blocks (CRN Advanced Display, simple/rich text) | client ≥ 0.0.8 and server ≥ 0.0.8 **with the display source enabled** (a 0.0.10+ server can disable it) |
| DQ | Separation departure queue (first-come-first-served) | server ≥ 0.0.9 (no client UI needed) |
| DR | Station door radius setting | client ≥ 0.0.9 and server ≥ 0.0.9 for configuring; server ≥ 0.0.9 applies it |
| PERM | Per-feature permissions (`/cts perm`) | client ≥ 0.0.2 and server ≥ 0.0.2 |
| INT | Manual schedule interrupt / resume | client ≥ 0.0.2 and server ≥ 0.0.2 |
| CC | `cts_station` ComputerCraft peripheral | server ≥ 0.0.4 (not part of the login handshake) |

## Why some pairs cannot join

- **0.0.1 ↔ anything newer**: 0.0.1 registers a non-optional `ClearSchedulePayload` channel that later
  versions no longer have, and its `TrainStatusPayload` layout differs. Both directions are refused.
- **0.0.2 ↔ 0.0.1**: 0.0.2 registers non-optional channels that do not exist in 0.0.1 and vice versa.
- **0.0.2 ↔ 0.0.3 – 0.0.7**: both directions can join. The 0.0.2 payload set is a subset of the later set,
  and from 0.0.3 onward every channel is optional, so extra channels on either side are negotiated away.
- **0.0.8 – 0.0.9b server ↔ any older client**: cannot join. These servers sync the
  `create:display_source` entry `create_trains_scheduling:departure_countdown`, which older clients do not
  know; registry sync fails and there is no server-side switch in these versions.
- **0.0.10 server with `registerDepartureCountdown = false` ↔ older or unmodded client**: can join. The
  server syncs no custom registry entry, and the wire protocol is unchanged since 0.0.3 (extra channels are
  optional). Features newer than the client version, and DCD, stay unavailable.
- **0.0.8+ client ↔ any older server**: can join. The server's registry simply lacks the newer entry, so the
  client keeps it locally and everything else works (DCD unavailable; DQ and DR additionally unavailable
  below 0.0.9).

## Recommendations

- Run the **same version on client and server**. Mixed pairs work up to 0.0.7 but with feature gaps;
  **0.0.8+ servers require 0.0.8+ clients** unless the server is 0.0.10 and disables the display source.
- Servers on 0.0.8 – 0.0.9b cannot accept unmodded clients. If a server must stay vanilla-client friendly,
  upgrade to 0.0.10 and set `registerDepartureCountdown = false` in
  `config/create_trains_scheduling-startup.toml`, then restart.
- When the display source is disabled, the station departure countdown and any existing countdown display
  links stop working for every client. Remove or repurpose those links first; re-enabling the option after a
  restart restores them (the display source id stays the same).
- For servers on 0.0.8 or newer, **upgrade the clients first** (or both sides together): a 0.0.8+ server
  rejects any client older than 0.0.8, even if that client has the mod installed.
- Never mix 0.0.1 or 0.0.2 with a different protocol group.
- `0.0.3a` through `0.0.7` are wire-compatible with `0.0.3`; only client rendering (0.0.3a/0.0.3b), the
  server-side ComputerCraft bridge (0.0.4/0.0.5), the clipboard editor channels (0.0.6) or the GUI
  train-schedule channel (0.0.7) changed. 0.0.8+ keeps the same wire protocol but adds the synced registry
  entry (and 0.0.9 adds optional door-radius channels).
