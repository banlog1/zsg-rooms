# Game Rules and World Modifications

Room game rules are selected by the host before room creation and copied to all
players in every room snapshot. Each rule is applied when the local integrated
server starts for the race world.

## Allow Cheats

Calls Minecraft's player-manager cheat setting for the local race server. This
does not grant remote control to the room host; every player's world remains a
separate local world.

Default: Off.

## Standardize Race RNG

This rule makes several random channels deterministic from the shared world
seed:

- Mob drops are seeded by dimension, entity type, and that channel's event
  index.
- Piglin barters use one deterministic global barter sequence.
- The player is positioned at the world's exact configured spawn point instead
  of receiving Minecraft's normal randomized spawn offset.

The vanilla mob drop and barter loot tables are still used unless the separate
boosted-barters rule is enabled.

This is event-sequence standardization, not a lockstep simulation. If one
runner kills different mobs or barters in a different order, later random
results can diverge because the event indexes no longer match.

Default: Off.

## Spawn Near Filter Structure

When the selected filter's route structure is more than 140 horizontal blocks
from Minecraft's original world spawn, the mod moves the local world spawn to a
safe surface 70-128 blocks from that structure. The distance and direction are
derived from the shared world seed and filter, so equivalent clients choose the
same position. If the structure is already close, cannot be located, or no dry
two-block-high surface is found, the original spawn is kept.

Filter targets are:

| Filters | Target structure |
| --- | --- |
| ZSG Mapless, Mapless (OP) | Buried treasure |
| ZSG Village, Village (OP) | Village |
| ZSG Shipwreck, Shipwreck (OP) | Shipwreck |
| ZSG Desert Temple, Desert Temple (OP) | Desert pyramid |
| ZSG Jungle Temple, Jungle Temple (OP) | Jungle pyramid |
| Ruined Portal Seedbank | Ruined portal |

Random, room-code, and manual seeds do not have a route structure and are left
unchanged. This rule relocates spawn before a player joins; it does not move or
regenerate the structure itself.

Default: Off.

## Increase Piglin Barter Rates

Uses `data/zsg-rooms/loot_tables/gameplay/standardized_piglin_bartering.json`
instead of Minecraft's normal piglin barter table. It can be enabled with or
without RNG standardization:

- With standardization: the custom table uses the deterministic barter stream.
- Without standardization: the custom table uses the normal world RNG.

The table has one weighted roll with total weight 418. Important entries are:

| Result | Weight | Chance per barter | Count |
| --- | ---: | ---: | ---: |
| Ender pearls | 25 | about 5.98% | 4-8 |
| String | 25 | about 5.98% | 8-24 |
| Obsidian | 50 | about 11.96% | 1 |
| Fire resistance potion | 10 | about 2.39% | 1 |
| Splash fire resistance potion | 10 | about 2.39% | 1 |
| Iron nuggets | 10 | about 2.39% | 9-36 |

The JSON file is the source of truth for every entry and count range.

Default: Off.

## Guarantee 3 Iron in Bastion

The first opened chest using one of Minecraft's four bastion loot tables is
inspected immediately after its vanilla loot is generated.

Iron is counted in units:

- One ingot = 9 units.
- One nugget = 1 unit.
- Target = exactly 27 units, equivalent to three ingots.

If the chest already has at least 27 units, nothing is added. Otherwise the mod
adds exactly the missing amount as ingots when divisible by nine, or nuggets
when it is not. Nugget additions may be distributed across up to three shuffled
empty slots to resemble generated loot. If no exact placement fits in the full
chest, the mod logs a warning and leaves it unchanged rather than deleting or
replacing another item.

Only the first qualifying chest is completed. This rule does not scan every
bastion chest at Nether entry time.

Default: Off.

## Remove Zombified Piglins From Bastions

Rejects a `ZombifiedPiglinEntity` when it is added or loaded inside the child
bounding boxes of a Bastion Remnant structure. It does not define an arbitrary
radius around the bastion and does not cancel spawning of piglins, hoglins, or
other entity types.

Minecraft 1.16.1 does not have piglin brutes, so no brute-specific handling is
needed for the supported version.

Default: Off.

## Ruined Portal Corruption Repair

This is a local Room Settings preference rather than a host game rule. It only
activates while the current room filter is `Ruined Portal Seedbank`.

During ruined-portal structure generation, the mod records:

- The generated chest state and its `minecraft:chests/ruined_portal` loot seed.
- Generated obsidian block positions and states.
- Generated crying obsidian block positions and states.

For up to 2,400 server ticks, a watched chest is checked after its chunk loads.
If terrain placement replaced or corrupted it, the mod restores the original
chest block and reapplies the original loot table and loot seed. Recorded
obsidian or crying obsidian replaced by another block is restored once its
chunk is available.

The repair does not search for arbitrary missing chests. It can only restore
blocks captured from the ruined-portal structure template during generation.

Default local preference: On.

## Advancement and Progress Reporting

Completed displayed advancements are sent once per player per race. Recipe
advancement IDs under `recipes/` are excluded. Remote advancements appear in
Minecraft chat and room chat.

The match HUD maps selected IDs to these stages:

| Stage | Advancement IDs | Label |
| ---: | --- | --- |
| 1 | `minecraft:story/root` | Advancement title |
| 2 | `minecraft:story/mine_stone` | Advancement title |
| 3 | `minecraft:story/smelt_iron`, `minecraft:story/iron_tools` | Advancement title |
| 4 | `minecraft:nether/root` | Entered Nether |
| 5 | `minecraft:nether/find_fortress` | Found Fortress |
| 5 | `minecraft:nether/find_bastion` | Entered Bastion |
| 6 | `minecraft:story/follow_ender_eye` | Found Stronghold |
| 7 | `minecraft:end/root` | Entered End |
| 8 | `minecraft:end/kill_dragon` | Dragon Defeated |

Progress never moves backward unless that player selects `Reset Run`, which
sets their stage to zero with the label `Restarting`.

## Victory Rules

`minecraft:end/kill_dragon` is only a progress milestone. A completed run is
reported when Minecraft sends the `GAME_WON` state after the player enters the
End exit portal.

The host validates and broadcasts the first completion as the match result. A
forfeit or a player leaving a two-player active match can also decide the
winner. The result returns all clients to their existing room state after the
on-screen title delay.

## Clear Speedrun Worlds

The Select World screen adds a confirmed destructive action. It enumerates
Minecraft's level list and deletes only worlds whose internal or display name
starts with:

```text
Set Speedrun #
```

It does not use a broad folder wildcard. Minecraft's `LevelStorage.Session`
performs each deletion, and the screen reports deleted and failed counts.
