# Relay Spectator (Fabric, client-side, MC 26.2)

A PvP-tournament-style spectator mod.

| Command | What it does |
|---|---|
| `/spectate <playerA> <playerB>` | Auto-directed camera on the duel (you must be in spectator mode). Cuts between side, over-the-shoulder and high shots; follows any ender pearl in flight. |
| `/spectate kit <name>` | Label shown on the breakdown (e.g. `Spear & Mace`). |
| `/spectate stop` | Stop. |
| `/spectate win <player>` | Manual fallback: declare the winner and run the replay and breakdown. |
| `/replay exit` | Leave a replay early. |
| `/breakdown` | Re-open the last round breakdown. |

On a **totem pop** you get a replay of the previous 20 s. On a **death** you get the same replay, then the Round Breakdown screen
(damage, regen, hits, accuracy, crits, winner, match score).

## Build
Needs JDK 25. Run `./gradlew build`; the jar lands in `build/libs/relay-spectator-0.1.0.jar`.
Versions (Loom 1.19.1, Loader 0.19.5, Fabric API 0.161.0+26.2) are set in `gradle.properties`.

## Known limits
- **Compiles against 26.2 but has not been run in a game yet.** Behaviour (camera feel, replay ghosts, screen layout) is untested.
- Item counts and kit detection need this mod on the **server** too (clients never receive other players' inventories). Armour durability and held items work client-only. Only spectators can request inventories.
- Stats are inferred from health changes on the client, so they approximate the server's numbers (crits especially).
- Replay ghosts use the default skin with a name tag (the level rejects a duplicate UUID).
- The camera works by moving your spectator player, so the server sees you moving.

## Kit detection (needs the server install)
| Kit | Detected when | Bar tracks |
|---|---|---|
| Cart | a TNT minecart | XP bottles, totems, pearls |
| Spear & Mace | any mace + any spear | totems, wind charges, pearls |
| Mace | any mace (no spear) | totems, wind charges, pearls |
| Diamond / Netherite SMP | full diamond / netherite armour + cobwebs, water bucket, oak logs, XP bottles | XP bottles, cobwebs, golden apples |
| Netherite OP | full netherite + 12+ potions | healing potions, XP bottles, totems, other potions |

Every kit also shows per-piece armour durability and the total durability difference (opponent minus the player: "+" means the player is behind). `/spectate kit <name>` overrides the label.
