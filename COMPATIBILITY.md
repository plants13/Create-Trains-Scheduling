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
entry to `create:display_source`, so a 0.0.8+ server sends a registry entry that older clients do not know.

| Server version | Client without this mod | Client with an older mod version |
| --- | --- | --- |
| 0.0.1 – 0.0.7 | can join; map UI, clipboard editor and display features are unavailable | see matrix below |
| 0.0.8+ | cannot join (registry sync fails with an "unknown key" error) | cannot join either — the client must be 0.0.8 or newer |

## Protocol groups

Several releases share the same network protocol. They are grouped in the matrix below.

| Group | Releases | Notes |
| --- | --- | --- |
| P1 | 0.0.1 | Initial protocol. Payload channels are **non-optional**; old `TrainStatusPayload` layout. |
| P2 | 0.0.2 | Payload channels are **non-optional**; status payload uses the flags layout; permissions and manual-schedule interrupt added. |
| P3 | 0.0.3, 0.0.3a, 0.0.3b, 0.0.4, 0.0.5 | Payload channels are **optional**; identical network code. 0.0.3a/0.0.3b changed client rendering only; 0.0.4 added the ComputerCraft bridge; 0.0.5 deduplicated it. None of these changed the wire protocol. |
| P4 | 0.0.6 | Superset of P3: adds optional clipboard/editor channels. |
| P5 | 0.0.7 | Superset of P4: adds the optional GUI train-schedule action channel. |
| P6 | 0.0.8, 0.0.8a, 0.0.9, 0.0.9a, 0.0.9b | Same wire protocol as P5, but adds a **synced** `create:display_source` registry entry. Older clients cannot join a P6 server; 0.0.8a/0.0.9a/0.0.9b are bugfix releases with no wire or registry change. |

## Matrix

| Client ↓ / Server → | 0.0.1 | 0.0.2 | 0.0.3 – 0.0.5 | 0.0.6 | 0.0.7 | 0.0.8 – 0.0.8a |
| --- | --- | --- | --- | --- | --- | --- |
| **0.0.1** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **0.0.2** | ❌ | ✅ | ⚠️ MS / CRN / QD / ED* | ⚠️ MS / CRN / ED* | ⚠️ MS / CRN / ED / TSE* | ❌ |
| **0.0.3** | ❌ | ⚠️ MS / CRN | ✅ | ⚠️ ED | ⚠️ ED / TSE | ❌ |
| **0.0.3a** | ❌ | ⚠️ MS / CRN | ✅ | ⚠️ ED | ⚠️ ED / TSE | ❌ |
| **0.0.3b** | ❌ | ⚠️ MS / CRN (QD works) | ✅ | ⚠️ ED | ⚠️ ED / TSE | ❌ |
| **0.0.4** | ❌ | ⚠️ MS / CRN (QD works) | ✅ | ⚠️ ED | ⚠️ ED / TSE | ❌ |
| **0.0.5** | ❌ | ⚠️ MS / CRN (QD works) | ✅ | ⚠️ ED | ⚠️ ED / TSE | ❌ |
| **0.0.6** | ❌ | ⚠️ MS / CRN / ED (QD works) | ⚠️ ED | ✅ | ⚠️ TSE | ❌ |
| **0.0.7** | ❌ | ⚠️ MS / CRN / ED / TSE (QD works) | ⚠️ ED / TSE | ⚠️ TSE | ✅ | ❌ |
| **0.0.8 – 0.0.8a** | ❌ | ⚠️ MS / CRN / ED / TSE / DCD (QD works) | ⚠️ ED / TSE / DCD | ⚠️ TSE / DCD | ⚠️ DCD | ✅ |

\* A 0.0.2 client does not have the UI for these features at all. The cell only says the server-side
features are not reachable from this client, or that the client has no UI for them.

0.0.9 is join-compatible with 0.0.8/0.0.8a in both directions (same synced registry; only optional channels added).
The separation queue needs a 0.0.9 server; the door radius setting needs 0.0.9 on both sides to configure.

Feature codes:

| Code | Feature | Requires |
| --- | --- | --- |
| MS | Multi-stop custom routes (pass-through via Steam 'n' Rails) | client ≥ 0.0.3 and server ≥ 0.0.3 |
| CRN | CRN circuit info (line / terminus / category) in the train status panel | client ≥ 0.0.3 and server ≥ 0.0.3 |
| QD | Quick destination (click a station while the train menu is open) | client ≥ 0.0.3b and server ≥ 0.0.2 |
| ED | Schedule editor commands (`/cts getcrn`, `/cts getsch`, `/cts writesch`) + clipboard channels | client ≥ 0.0.6 and server ≥ 0.0.6 |
| TSE | Train schedule export/import from the map dispatch menu (live schedule + run step) | client ≥ 0.0.7 and server ≥ 0.0.7 |
| DCD | Departure countdown display source on station blocks (CRN Advanced Display, simple/rich text) | client ≥ 0.0.8 and server ≥ 0.0.8 |
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
- **0.0.8+ server ↔ any older client**: cannot join. The 0.0.8+ server syncs `create:display_source` with the
  new `create_trains_scheduling:departure_countdown` entry, which older clients do not know; registry sync
  fails.
- **0.0.8+ client ↔ any older server**: can join. The server's registry simply lacks the newer entry, so the
  client keeps it locally and everything else works (DCD unavailable).

## Recommendations

- Run the **same version on client and server**. Mixed pairs work up to 0.0.7 but with feature gaps, and
  **0.0.8+ servers require 0.0.8+ clients**.
- For servers on 0.0.7 or older, upgrading the server first is fine (older clients can still join).
- For servers on 0.0.8 or newer, **upgrade the clients first** (or both sides together): a 0.0.8+ server
  rejects any client older than 0.0.8, even if that client has the mod installed.
- Never mix 0.0.1 or 0.0.2 with a different protocol group.
- `0.0.3a` through `0.0.7` are wire-compatible with `0.0.3`; only client rendering (0.0.3a/0.0.3b), the
  server-side ComputerCraft bridge (0.0.4/0.0.5), the clipboard editor channels (0.0.6) or the GUI
  train-schedule channel (0.0.7) changed. 0.0.8/0.0.8a keep the same wire protocol but add a synced registry
  entry.
