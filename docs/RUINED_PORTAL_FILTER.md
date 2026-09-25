# Model-Only Ruined Portal Filter

Status: available as **ZSG Rooms Ruined Portal** on the Rooms filter tab,
with hosted profile `rooms-ruined-portal-v5` and finder type `ruined_portal`.
This is separate from RP Seedbank. ZSG Rooms Mode draws this filter at 15%
and the external RP Seedbank at 5%.
The standalone finder never creates a Minecraft world, including on failure.
Local overnight collection is available through `-Preset bt-rp`; see
[Overnight Seed Bank](OVERNIGHT_SEED_BANK.md). Searching collects local results;
publication and uploading remain explicit operator steps.

## Acceptance

- Cubiomes portal candidate in region 0,0, within 224 blocks per axis of origin.
- ZSG surface/air-pocket and wooded-biome predicates, with a complete 3x3
  wood-biome sample within 30 blocks; modeled spawn within 32 blocks per axis
  of the portal. At least one sample must be a wood biome, not all nine.
- Native lower-48 ZSG chest-loot calculation. No Looting requirement, no nearby
  village/temple requirement, and no stronghold-position restriction.
- Require at least one golden axe or golden pickaxe in the chest. Reject
  tool-less families before Nether models and sister-seed checks. Counting tools
  uses the existing loot rolls and does not change RNG advancement.
- The existing full ZSG Nether checks, including our stables good gap and
  triple-chest rampart requirements, without modifications.
- After biome and spawn checks, SeedFinding predicts portal height, rotation,
  mirror, frame blocks and crying-obsidian replacements. Require a non-buried,
  upright surface portal, an air pocket and a modeled chest position.
- Find a usable rectangular opening without mining normal or crying obsidian.
  Corners are optional. A required edge cannot be crying obsidian, and the
  interior cannot contain either kind of obsidian. Ordinary stone/dirt may
  still need clearing. The search can shrink a giant frame or extend broken
  edges by up to two blocks and requires at least three existing frame blocks.
- Count missing edge blocks. Chest obsidian can fill them directly. Otherwise
  count surviving template lava sources after the positional 20% magma rule;
  flowing lava in giant templates is excluded because it cannot be bucketed;
  lava plus chest obsidian must cover the deficit. Never count decorative
  obsidian as obtainable inventory, or crying obsidian as normal obsidian.
- Require flint and steel, flint plus nine iron nuggets, or at least five fire
  charges. A stack of one to four charges alone is rejected, even if it can light
  the portal, to leave ignition available for food. When crafting flint and steel,
  its nine nuggets cannot also count toward a required bucket.
  Lava-assisted completion additionally reserves 27 nuggets for a bucket and
  uses the existing natural-water proxy within 48 blocks of the portal anchor.
  Obsidian-only completion does not require bucket iron or nearby water.

## Boundaries

This is a gameplay model, not a proof of the final world. SeedFinding's terrain
height, temperature handling and later decoration are not block-perfect. Lava
above portal origin Y90 is conservatively ignored; sources next to modeled
natural water are also ignored. No new water, lava or obsidian is placed by the
filter. In Rooms, this profile activates the same automatic generated-portal
chest/obsidian repair and chest clearance as RP Seedbank, independently of the
testing-only repair toggle. Generated-portal lookup also covers this profile
without requiring Looting; the external RP Seedbank keeps its Looting lookup.
The finder does not rely on Minecraft generation to accept candidates.

The bounded frame policy deliberately rejects some otherwise repairable
portals rather than assuming a player will build a separate portal or mine
obsidian. Spawn/biome and surface criteria are inherited profile choices, not
requirements imposed by the frame algorithm itself.

## Checks And Reports

`checks.portal_completion` records how many sisters reach the late model and
its wall time. The additional `portalModel` report separates layout, frame and
resource rejection, lava routes needing water, and obsidian-only completion.
The native nearby-water stage records subsequent water rejections.

Accepted private JSONL rows use `ruinedPortalRule: frame-completable-v3` and
record chest resources, missing blocks, counted lava, cast blocks, template ID
(1-10 small; 11-13 giant), portal origin Y and any selected water anchor.
Version 2 adds the five-charge minimum when neither ready nor craftable flint
and steel is available. Version 3 additionally requires an axe or pickaxe and
records `goldenAxes` and `goldenPickaxes`. Earlier samples are not retroactively
certified by the new rule. This applies to the new model-only RP finder, not the
existing external RP Seedbank provider.

Tests cover native protocol rejection, 4,096 loot vectors, bucket/ignition
allocation, crying obsidian, optional corners, obstructed interiors, frame
selection and lava-table bounds. The loot oracle uses the existing isolated
SeedFinding **1.171.9** dependency: 1.171.5 on the village classpath lacks the
axe/thorns enchantment overrides and diverges on these loot vectors. No village
dependency was upgraded. An offline `PortalTemplateAudit <1.16.1.jar>` verifies
the lava coordinates against the game assets without loading game classes.

Example after building the native worker and installing the model finder:

```powershell
.\scripts\Search-FilterCandidates.ps1 -Type ruined_portal -Target 10 -Seconds 120 -Stream <reserved-offset>
```

Reserve a fresh range through the existing shared cursor for bank work.
Provisional defaults are 4,096 sisters and at most four accepted seeds per family;
these are not yet a tuned overnight-production policy.

## Tool Requirement And Search Policy Trial

With the v3 tool requirement, a sequential one-worker comparison used the same
reserved range and 180 seconds per policy, without Minecraft generation:

| Sister attempts | Accepted cap | Accepted seeds | Productive families | Seeds/min |
| ---: | ---: | ---: | ---: | ---: |
| 4096 | 2 | 14 | 7 | 4.67 |
| 4096 | 4 | 26 | 7 | 8.67 |

Four is now the RP operator-script default; explicit `-FamilyCap` still wins.
The 86% output increase is a short-sample result, not an overnight forecast.
The previous 30-minute v2 run used different criteria and a different range,
so it is not a controlled baseline for the early tool rejection.
Spawn remains the main measured cost. Tool-less families now skip this work
entirely rather than reaching a late rejection after sister validation.

Private reports and samples are under
`run/portal-analysis/tools-cap-7e76c7f2918e45e4a58af807c88d860a/`.
Reproduce policy comparisons with `scripts/Benchmark-ModelSearchPolicies.ps1`
using `-Type ruined_portal -SecondsPerCase 180 -Stream <reserved-offset>` and a
fresh `-OutputDirectory`. Both policies intentionally revisit that range for
comparison; do not merge their outputs into a bank without deduplication.

### Ten-Minute Throughput Check

On September 25, a fresh reserved range with v3 loot rules, 4,096 sister
attempts and cap four accepted **108 seeds from 30 families in 600 seconds**:
10.8 seeds/minute, equivalent to 648/hour for this window, not an overnight
guarantee. Chrome remained open; a separate BT policy diagnostic overlapped
for three minutes. This is a light concurrent-load result, not an isolated
machine maximum.

All 108 records matched the independent SeedFinding loot calculation,
including axe/pickaxe counts. There were 98 obsidian-only and 10 lava-assisted
routes; bastions were 47 housing, 33 bridge, 16 stables and 12 treasure.
No samples were uploaded. Search visited 27,844,732 families and 1,381,729
sisters. Spawn checks used 390 seconds (65%); biomes 122 seconds (20%);
portal completion 50 seconds (8%); full Nether models 32 seconds (5%).

Private frozen finder, fingerprint, bank and timing report:
`run/portal-analysis/throughput-v3-ea81ebf2cf044bdb8839927114a30a8f/`.

## First Trial

A bounded 120-second, one-worker trial found seven seeds from four families.
All seven used chest-obsidian completion. Of 257,164 sister checks, 84,229 reached
spawn and 2,979 reached the portal model. Spawn consumed 75.7 seconds; the late
portal model consumed 10.6 seconds. This was a small development trial with
regression tests also running, not a stable throughput estimate or gameplay
validation. Samples remain private under `run/portal-samples/` and were not
uploaded.

A second fresh window found ten more obsidian-only seeds from five families
in 66.45 seconds, with a mod build also running. The small samples have not yet
produced a lava-assisted acceptance; a known-layout test exercises lava/bucket
completion separately, without changing actual sample loot. All 17 samples
still need in-game inspection before the profile is published.

## Gameplay Repair Testing

For these manually entered samples, turn on `Room Settings > RP Test All Seeds`
before generating a fresh world. The prototype profile is not yet a room-picker
choice. Existing RP room seeds (including random-mode RP selections) repair
automatically without that testing override. The override defaults off, uses a
new preference file, and also applies outside rooms.

Repair captures the original chest state/loot seed and portal obsidian during
structure generation, then runs once when the chunk is fully generated. It clears
a five-block access pocket above/in front only when safe. Portal blocks, other
containers, fluids and unsupported falling overhangs are protected; an unsafe
pocket is skipped and logged. No retroactive chunk-load repair or repeated
player-terrain removal occurs. The standalone portal repair calibration covers
the previously reported corruption seed plus clearance and interaction fixtures.
