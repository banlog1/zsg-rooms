# Game Rule Quality Audit

Reviewed 2026-10-04 against `94a2231` (v1.0.31). The initial audit changed no
production behavior. Approved follow-ups change the adult-animal guarantee,
force Survival for Rooms creation, and enforce the room's command permissions.
Bastion and strider behavior is retained at the user's request.

## Findings Requiring Decisions

### Resolved: Allow Cheats Off Did Not Override the World's Cheat Flag

`ZsgRooms.onServerStarted` sets the player-manager cheat flag, but Minecraft also
grants the integrated-server owner permission when the saved world's commands
flag is enabled. A test world created with commands enabled retained permission
level 4 inside a cheats-off room, despite the manager flag being false.

This is conditional on world-creation settings; the test does not establish that
the normal Atum launch always enables that flag. Nevertheless, the room setting
is not authoritative over it, and history eligibility uses the room setting.

Recommendation: enforce the room's command permission policy in its race worlds,
including when the creation template enables cheats. Do not change unrelated
singleplayer worlds. Confirm whether this is the intended meaning of Off.

Source: `src/main/java/zsgrooms/modid/ZsgRooms.java`, `onServerStarted`.

#### Enforcement Follow-Up

Minecraft 1.16.1's `PlayerManager.isOperator` accepts any of the operator list,
the local host plus saved-world commands flag, or the player-manager cheat flag.
`MinecraftServer.getPermissionLevel` derives player command permission from that
decision. The current `OpenToLanScreenMixin` disables the vanilla LAN cheats
button, but that UI restriction does not override the other permission sources.

Implemented design:

- Capture the host's cheat rule for an explicit Rooms world launch, and bind it
  to that integrated-server instance before player login. Merely having a lobby
  open, or matching its seed, should not identify an arbitrary world as a race.
- When that server's rule is Off, override player operator eligibility there.
  This closes the saved-world and operator-list paths as well as later changes
  to the manager flag. Normal permission-level-zero commands remain available;
  internal server commands are not blanket-blocked.
- Retain normal enabled-cheats behavior for On and vanilla behavior for unrelated
  worlds. Avoid rewriting the user's general Atum/world-creation settings.
- Keep the policy for the lifetime of the bound race world, including result
  screens, and clear it on shutdown or failed launch. Do not unlock permissions
  merely because another runner finishes or the room returns to its lobby state.
- Keep the client command tree/permission indication consistent with the server.
  Test saved cheats on/off, an operator-list entry, LAN opening, enabled-cheats
  rooms, failed launches, resets, multi-seed transitions and ordinary singleplayer.

`RoomWorldCreationMixin` scopes creation by the exact Atum screen and captures
the rule while Minecraft starts the new server. `RoomCommandPermissions` binds
the policy by the launch directory and server identity before login. A finally
block clears pending launch context, and server shutdown clears the binding.
Creation copies the host's command flag without changing the Atum template;
player-manager hooks enforce it even against later operator/LAN flag changes.
`RoomSeedCommandMixin` separately restricts `/seed` for players, since vanilla
integrated servers otherwise expose it at permission level zero.

This enforces the setting inside the mod, not tamper-proof anti-cheat against a
modified client running its own integrated server. The original diagnosis above
is historical; an unrelated direct world launch is intentionally not restricted.

Verification: `RoomWorldCreationSmoke` passed with real Atum launches and resets
(Off/On/Off), opposing Atum cheat defaults, explicit operator entries, the LAN
permission setter, rejected `/gamemode` execution, and client command-tree and
permission checks for `/gamemode`, `/give`, `/tp`, `/seed` and `/help`. It also
checks an ordinary Creative world with the lobby still open, mismatched launch
directories, failed-launch cleanup, stopped-server cleanup and a later room-rule
change not unlocking an existing server. This does not include a remote LAN
connection or a multi-machine sequence race. `test remapJar` passed with 557
tests passing and five skipped.

### Medium: Bastion Removal Extends Beyond Individual Structure Pieces

The tooltip promises generated bastion *piece* bounds. The implementation tests
the enclosing bounding box of the complete structure. In an actual generated
bastion, a point outside every child piece was still rejected; an explicitly
spawned zombified piglin there was also rejected. Terrain and gaps between
pieces therefore fall under this rule.

Recommendation: check individual piece bounds to match the advertised behavior.
Alternatively, retain the larger area and describe it accurately if clearing the
whole bastion vicinity is intentional. The existing hooks also cover entity
loading and explicit creation, not just natural spawning.

Source: `src/main/java/zsgrooms/modid/BastionZombifiedPiglinControl.java`,
`shouldReject`; `mixin/BastionZombifiedPiglinMixin.java`.

### Resolved: Baby Animals Counted Toward the Three-Animal Guarantee

The original eligibility check tested species, not age. A baby cow counted, so
the guarantee did not necessarily provide three animals that immediately yield
food. The user approved counting adults only.

The implementation now counts adult pigs, cows, sheep, chickens and rabbits.
Existing babies are left untouched, and newly added animals are explicitly made
adult after initialization. The tooltip and rule documentation describe this.
Placement remains deliberately bounded and can return fewer than three when no
suitable natural positions exist. No spawn radius or terrain criteria changed.

Source: `src/main/java/zsgrooms/modid/StructureAnimalGuarantee.java`,
`ensure` and `isEligible`.

### Low: Natural Strider Jockey Removal Also Covers Spawn Eggs

The mixin intercepts initialization without checking `SpawnReason`. With a fixed
random seed, `SPAWN_EGG` initialization created one rider with the rule off and
none with it on. Manually attached passengers are not the issue here.

Recommendation: restrict suppression to natural/chunk-generation initialization
if the word Natural is intentional. Otherwise keep this behavior and update the
name and tooltip to include automatically generated spawn-egg riders.

Source: `src/main/java/zsgrooms/modid/mixin/StriderEntityMixin.java`.

## Rule-by-Rule Coverage

All 13 gameplay flags were checked through preset selection, UI application,
serialization, snapshot propagation and server application. The new combination
test covers data round trips, not every combination's live gameplay effects.

| Rule | Review result and important limits |
| --- | --- |
| Allow Cheats | Fixed in the approved follow-up: launch-scoped server policy overrides saved template/operator/LAN permissions; player `/seed` follows the same rule. |
| Standardize Race RNG | Reviewed separate drop/barter/eye/gravel/Unbreaking channels, spawn positioning, wood lighting, Blaze spawners, Nether spawning and dragon controls. It standardizes event sequences, not complete world simulation. The first fortress's eight protected pack-selection opportunities can temporarily exceed the global mob cap; this is documented existing behavior, not a new defect. |
| Increase Piglin Barter Rates | Boosted table and RNG standardization remain independent. No additional defect identified in table selection or reset flow. |
| Spawn Near Filter Structure | Uses the selected bank structure rather than substituting a locate result; bank metadata is validated before launch. Ordinary routes use the 48-block limit; shipwrecks retain safe spawn within 60 blocks or seek nearby island/coast. Prepared spawn is pinned even with RNG standardization off. No safe location retains original spawn. |
| Guarantee 3 Animals Near Structure | Approved follow-up excludes babies and explicitly adds adults; existing babies are preserved. Bounded natural-terrain placement is still not an unconditional promise of three animals. |
| Reduce Zero-Cycle Fly-Aways | Independent from RNG standardization. Scales opening target-height offsets for the first 1,300 dragon ticks; does not force a perch or zero cycle. No additional defect identified. |
| Guarantee 3 Iron in Bastion | First qualifying chest only, counting ingots and nuggets as 27 nugget units. New tests cover preserving a full chest, trying a later chest, once-per-configuration handling and reset rearming. It is not a guarantee for every chest or every bastion. |
| Remove Zombified Piglins from Bastions | Confirmed whole-structure versus piece-bound mismatch above. Only the target mob type is removed, but spawn/load origin is not restricted. |
| Remove Natural Strider Jockeys | Confirmed spawn-egg scope mismatch above. Existing passenger attachment is not intercepted. |
| Preload Nether Entry Chunk | Room flag and local preference both gate it. Reviewed projected destination, staged chunk requests, upgrade budget and ticket cleanup. Work is conditional, not guaranteed to complete before entry. No new latency benchmark in this audit. |
| Disable Pause World Saves During Races | Active-race gating and targeted pause-save interception reviewed. Periodic autosaves, shutdown saves, player data and chunk unloading remain enabled. No new defect identified. |
| Shared First Nether Entry | Original world-spawn reference is captured before relocating spawn. Deterministic destination/orientation, per-player first-entry state and later vanilla linking reviewed. Fresh same-seed resets repeat the reference; ordinary return trips do not consume a new shared entry. |
| No Temple Hostile Spawns | Live test passed for actual natural hostile-spawn rejection, temple/chamber bounds, outside/passive/Nether bypass, disabling and lifecycle cleanup. Existing mobs and mobs walking in are intentionally unaffected. |

Host-only, lobby-only rule editing and seed-preparation invalidation were also
reviewed. Existing tests cover room snapshots, presets, format/tournament
validation and seed/reset flows. Standard enables all gameplay options except
cheats; Vanilla Barters differs in the barter rule; Raw clears gameplay flags.
The two documented performance overrides do not force a different preset label.

## Remaining Risks and Documentation

- Some global controls (RNG, bastion iron, bastion zombified piglins and strider
  jockeys) are reconfigured at `SERVER_STARTED` but not cleared at
  `SERVER_STOPPED`. Loading a subsequent world before that callback deserves a
  dedicated transition test, especially entity loading in saved Nether chunks.
  This is a lifecycle risk from code inspection, not a reproduced cross-world
  failure. Prefer server-scoped state or early setup/explicit cleanup if confirmed.
- The local `RP Test All Seeds` switch can affect non-RP room seeds independently
  of the host. That may be intentional for testing, but allowing it in ordinary
  competitive rooms deserves an explicit decision.
- Mandatory bank lava preparation and RP repairs are separate from host rules.
  Raw does not undo them. Preserve that distinction rather than silently changing
  previously agreed bank behavior.
- `GAME_RULES.md` still describes RP repair as an optional, default-on preference
  for the old RP Seedbank only. The code now mandates repair for RP room seed
  types and provides a default-off all-seed testing override. That section needs
  updating. The RNG tooltip should also mention its fortress spawn-cap exception.

## Verification

- Full unit suite: **555 passed, 5 opt-in tests skipped**, zero failures/errors
  (560 tests across 86 suites).
- New test exercises **8,192** combinations of the 13 gameplay flags through
  JSON serialization and application/capture, plus two iron-inventory cases.
- Real Minecraft 1.16.1/Fabric integrated-server smoke passed, including the
  existing temple checks and the four diagnostic observations above.
- Smoke environment used Java 17 and isolated worlds in
  `run/filter-picker-test/`. It did not use the player's normal MultiMC saves.
  Atum, World Preview, SpeedRunIGT and the complete player modpack were not loaded.
- This was not a new multi-machine relay test, every-seed biome survey, or full
  in-game integration run for every RNG/preload/portal combination. Passing
  helper tests does not prove those broader cases.

Commands:

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat runFilterPickerTest -PfilterPickerTest -PgameRuleAuditSmoke `
    '-PtestJava=C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot/bin/java.exe' `
    --console=plain
```

The test Java path above is this audit machine's installation. The audit smoke
records undecided behavior for policy decisions rather than asserting that it
must remain forever. Adult eligibility now has regression assertions for all
five species, breeding cooldown, baby-age preservation, exclusions and a mixed
herd where one adult and two babies require two additional adults. Its completion marker is
`[GameRuleAudit] COMPLETE`; the combined temple harness also must pass.
