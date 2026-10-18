# Relay Spectator (Fabric, client-side, MC 26.2)

A PvP-tournament-style spectator mod.

| Command | What it does |
|---|---|
| `/spectate <playerA> <playerB>` | Auto-directed camera on the duel (you must be in spectator mode). Cuts between side, over-the-shoulder and high shots; follows any ender pearl in flight. |
| `/spectate kit <name>` | Label shown on the breakdown (e.g. `Spear & Mace`). |
| `/spectate stop` | Stop. |
| `/replay exit` | Leave a replay early. |
| `/breakdown` | Re-open the last round breakdown. |

On a **totem pop** you get a replay of the previous 20 s. On a **death** you get the same replay, then the Round Breakdown screen
(damage, regen, hits, accuracy, crits, winner, match score).

## Build
Needs JDK 25. Run `gradle wrapper` once, then `./gradlew build`; the jar lands in `build/libs/`.
Check `gradle.properties` versions against https://fabricmc.net/develop first.

## Known limits
- **Never compiled.** Written without network access to Fabric/Mojang; expect to fix a few 26.x API names. Rendering calls are
  isolated at the top of `BreakdownScreen`; the packet hook is `mixin/ClientPacketListenerMixin`.
- Stats are inferred from health changes on the client, so they approximate the server's numbers (crits especially).
- Replay ghosts use the default skin with a name tag (the level rejects a duplicate UUID).
- The camera works by moving your spectator player, so the server sees you moving.
