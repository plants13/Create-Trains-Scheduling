# Version Compatibility (Client × Server)

This table describes **inter-version compatibility of this mod only** (Minecraft 1.21.1 / NeoForge build).
It answers one question: if a client and a server run different versions of this mod, can the client join,
and which features stop working?

- Rows: **client** version.
- Columns: **server** version.
- ✅ join OK, all features supported by both sides work
- ⚠️ join OK, but the listed features are unavailable in this pairing
- ❌ cannot join (the connection is refused during the configuration/handshake phase)

## Protocol groups

Several releases share the same network protocol. They are grouped in the matrix below.

| Group | Releases | Notes |
| --- | --- | --- |
| P1 | 0.0.1 | Initial protocol. Payload channels are **non-optional**; old `TrainStatusPayload` layout. |
| P2 | 0.0.2 | Payload channels are **non-optional**; status payload uses the flags layout; permissions and manual-schedule interrupt added. |
| P3 | 0.0.3, 0.0.3a, 0.0.3b, 0.0.4, 0.0.5 | Payload channels are **optional**; identical network code. 0.0.3a/0.0.3b changed client rendering only; 0.0.4 added the ComputerCraft bridge; 0.0.5 deduplicated it. None of these changed the wire protocol. |
| P4 | 0.0.6 | Superset of P3: adds optional clipboard/editor channels. |

## Matrix

| Client ↓ / Server → | 0.0.1 | 0.0.2 | 0.0.3 – 0.0.5 | 0.0.6 |
| --- | --- | --- | --- | --- |
| **0.0.1** | ✅ | ❌ | ❌ | ❌ |
| **0.0.2** | ❌ | ✅ | ⚠️ MS / CRN / QD / ED* | ⚠️ MS / CRN / ED* |
| **0.0.3** | ❌ | ⚠️ MS / CRN | ✅ | ⚠️ ED |
| **0.0.3a** | ❌ | ⚠️ MS / CRN | ✅ | ⚠️ ED |
| **0.0.3b** | ❌ | ⚠️ MS / CRN (QD works) | ✅ | ⚠️ ED |
| **0.0.4** | ❌ | ⚠️ MS / CRN (QD works) | ✅ | ⚠️ ED |
| **0.0.5** | ❌ | ⚠️ MS / CRN (QD works) | ✅ | ⚠️ ED |
| **0.0.6** | ❌ | ⚠️ MS / CRN / ED (QD works) | ⚠️ ED | ✅ |

\* A 0.0.2 client does not have the UI for these features at all. The cell only says the server-side
features are not reachable from this client, or that the client has no UI for them.

Feature codes:

| Code | Feature | Requires |
| --- | --- | --- |
| MS | Multi-stop custom routes (pass-through via Steam 'n' Rails) | client ≥ 0.0.3 and server ≥ 0.0.3 |
| CRN | CRN circuit info (line / terminus / category) in the train status panel | client ≥ 0.0.3 and server ≥ 0.0.3 |
| QD | Quick destination (click a station while the train menu is open) | client ≥ 0.0.3b and server ≥ 0.0.2 |
| ED | Schedule editor commands (`/cts getcrn`, `/cts getsch`, `/cts writesch`) + clipboard channels | client ≥ 0.0.6 and server ≥ 0.0.6 |
| PERM | Per-feature permissions (`/cts perm`) | client ≥ 0.0.2 and server ≥ 0.0.2 |
| INT | Manual schedule interrupt / resume | client ≥ 0.0.2 and server ≥ 0.0.2 |
| CC | `cts_station` ComputerCraft peripheral | server ≥ 0.0.4 (not part of the login handshake) |

## Why some pairs cannot join

- **0.0.1 ↔ anything newer**: 0.0.1 registers a non-optional `ClearSchedulePayload` channel that later
  versions no longer have, and its `TrainStatusPayload` layout differs. Both directions are refused.
- **0.0.2 ↔ 0.0.1**: 0.0.2 registers non-optional channels that do not exist in 0.0.1 and vice versa.
- **0.0.2 ↔ 0.0.3+**: both directions can join. The 0.0.2 payload set is a subset of the 0.0.3+ set,
  and from 0.0.3 onward every channel is optional, so extra channels on either side are negotiated away.
- **0.0.3+ ↔ 0.0.3+**: all optional; missing channels are ignored. Only features that need a missing
  channel are unavailable.

## Recommendations

- Run the **same version on client and server**. Mixed pairs work from 0.0.3 onward but with feature gaps.
- If a mixed pair is unavoidable, upgrade the **server first**: 0.0.3+ servers accept older clients, and
  older servers accept newer clients (0.0.3+ era only).
- Never mix 0.0.1 or 0.0.2 with a different protocol group.
- `0.0.3a`, `0.0.3b`, `0.0.4` and `0.0.5` are wire-compatible with `0.0.3`; only client rendering
  (0.0.3a/0.0.3b) or the server-side ComputerCraft bridge (0.0.4/0.0.5) changed.
