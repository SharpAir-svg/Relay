# Relay Spectator (Fabric, client-side, MC 26.2)

A PvP-tournament-style spectator mod.

| Command | What it does |
|---|---|
| `/spectate <playerA> <playerB>` | Auto-directed camera on the duel (you must be in spectator mode). Cuts between side, over-the-shoulder and high shots; follows any ender pearl in flight. |
| `/spectate pick <player>` | Show "<player> Pick" under the kit name in the top-left kit box. |
| `/spectate kit <name>` | Label shown on the breakdown (e.g. `Spear & Mace`). |
| `/spectate stop` | Stop. |
| `/spectate win <player>` | Manual fallback: declare the winner and run the replay and breakdown. |
| `/spectate replay <on|off>` | Turn totem/death replays on or off (default off). |
| `/replay exit` | Leave a replay early. |
| `/breakdown` | Re-open the last round breakdown. |

Replays are **off by default** (`/spectate replay on` enables them): on a totem pop or death you then get a replay of the previous 20 s. A **death** goes to the Round Breakdown screen (after the replay, if enabled)
(damage, regen, hits, accuracy, crits, winner, match score).

## Far-apart fighters
When the two fighters are more than 100 blocks apart (usually one is off splashing XP or restocking), the usual shot of the fight would show nothing, so the camera cuts to one fighter for about 8 seconds. It prefers whoever is throwing XP bottles, eating/drinking or digging in an open shulker box, and otherwise alternates between them. It returns to the normal shot once they are back within about 85 blocks.

## Build
Needs JDK 25. Run `./gradlew build`; the jar lands in `build/libs/relay-spectator-0.1.0.jar`.
Versions (Loom 1.19.1, Loader 0.19.5, Fabric API 0.161.0+26.2) are set in `gradle.properties`.

## Known limits
- **Compiles against 26.2 but has not been run in a game yet.** Behaviour (camera feel, replay ghosts, screen layout) is untested.
- Item counts and kit detection need this mod on the **server** too (clients never receive other players' inventories). Armour durability and held items work client-only. Only spectators can request inventories.
- Stats are inferred from health changes on the client, so they approximate the server's numbers (crits especially).
- Replay ghosts use the default skin with a name tag (the level rejects a duplicate UUID).
- The camera works by moving your spectator player, so the server sees you moving.

## Servers without the mod
The client mod works on any server. Only the item counts and full kit detection need the mod on the server too:

| | Client only | Client + server |
|---|---|---|
| Camera, pearl follow, top bar, breakdown, replays | yes | yes |
| Armour durability per piece and the difference | yes | yes |
| Item counts on the inventory bar | no (bar says so) | yes |
| Kit detection | Mace and Spear & Mace, from a mace held in hand; otherwise `/spectate kit` | every kit |

The inventory bar shows "item counts need the mod on the server" whenever the server doesn't answer.

## Kit detection (needs the server install)
| Kit | Detected when | Bar tracks |
|---|---|---|
| Cart | a TNT minecart | XP bottles, totems, pearls |
| Spear & Mace | any mace + any spear | totems, wind charges, pearls |
| Mace | any mace (no spear) | totems, wind charges, pearls |
| Diamond / Netherite SMP | full diamond / netherite armour + cobwebs | XP bottles, cobwebs, golden apples |
| Netherite OP | full netherite + 12+ potions (checked after SMP) | healing potions, XP bottles, totems, other potions |

Every kit also shows per-piece armour durability and the total durability difference (the player minus the opponent: "+" means above on durability, "-" below). `/spectate kit <name>` overrides the label.
