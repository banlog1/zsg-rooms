# AA Temple Filter

`-Type aa_temple` is a separate, model-only All Advancements starting profile.
It does not change the regular temple filter, Rooms random mix, or game rules.
The mod exposes it as **AA Thunderless** (`rooms-aa-thunderless-v4`) in the ZSG
Rooms filter picker. Its custom-goal label distinguishes it from
Any% filters; the tooltip, room options, and lobby explain the win condition:
**all advancements except Very Very Frightening**. Exiting the End does not win.
The ordinary Rooms random mix does not select AA.

SpeedrunIGT switches to All Advancements during an AA race and stops when the
custom goal is reached. Ordinary room races use Any%; leaving a race restores
the timer's previous category. This is a room-specific completion rule, not a
standard AA leaderboard completion. The mod remains usable without SpeedrunIGT.

The production seed service accepts `type: aa_temple` in its own bank, requiring
current `aa-temple-v4` metadata. After the September 27, 2026 overnight upload,
the bank has 909 seeds from 669 lower48 families, including the original two
test seeds. AA alone retries once without
recent-family exclusions when the service returns HTTP 409 (no fresh candidate),
so an exhausted recent-family selection can still be played. Ordinary filters retain their
anti-repeat behavior. An empty AA bank or a service error still fails explicitly
without substituting an ordinary temple seed. See [production notes](../seed-service/PRODUCTION.md)
for the current publication and upload state.

## Overnight Run

For a resumable AA-only 12-hour run with up to five workers:

```powershell
Set-Location "E:\recordings\code\minecraft rooms"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Start-OvernightFilterBank.ps1 -Directory run/model-bank/overnight/aa-temple-v4 -Types aa_temple -Minutes 720 -Workers 5 -AllowFullCpu -CollectSamples
```

Rerun the same command to extend that bank. The supervisor reserves disjoint
family ranges, snapshots the current production runtime, commits completed
batches, and resumes unfinished assignments. Defaults are 100 million families
per batch, 4,096 sister checks and at most two accepted seeds per family. Ordinary
overnight defaults are unchanged. Combined results are exported to `bank.jsonl`
in that directory at checkpoints and shutdown; committed batches are saved as
they finish. Uploading remains a separate step. Keep the laptop plugged in;
screen-off is fine, but do not manually sleep or hibernate it during the run.

## Requirements

All regular [DT checks](MODEL_SEED_FINDER.md#what-it-checks) still apply: starting
temple loot (7 iron, or 4 iron plus 3 diamonds), nearby tree-biome sampling
anchored at the temple, roof exposure, modeled lava-pool opportunity, natural
water within 48 blocks of that pool, modeled spawn proximity, and the full ZSG
Nether checks including the existing stables layout requirements.

AA adds:

- At least **22 gunpowder** across the starting temple's four chests. TNT is not
  counted as gunpowder, and extra temples cannot make up a starting shortfall.
- A biome-valid, non-abandoned **village within 70 blocks** of the starting
  temple. There is no extra blacksmith or village-loot requirement.
- At least **two additional biome-valid temples within 1,024 blocks** of the
  starting temple. The starting temple does not count toward these two.
- A **first End ship within 512 blocks** of the modeled outer-End destination of
  the **first dragon-kill gateway**, plus **two more connected ships with links
  of at most 1,024 blocks**. The second connects to the first; the third can
  connect to either of them, allowing outward chains and existing clusters.
  All three must belong to distinct cities, pass biome and terrain-height checks,
  and actually generate ship pieces. Every eligible first/second pair is tried.

Both Overworld distances are horizontal circular distances between Cubiomes structure
start positions, not bounding-box edges, the nearest house, or walking distance.
The nearby temple cluster is the proxy for sufficient desert exploration. It
does not require a single connected desert biome or guarantee a minimum amount
of gunpowder in the extra temples. Their roof exposure is not checked; the
starting temple retains the regular exposure check.

End distances are horizontal circular distances using each ship's template center,
not the city start or the central End island. Cubiomes models gateway ordering, linked
outer destination, End terrain, and city pieces. This is a natural-terrain
arrival model, not an exact teleport-block guarantee: structures, decoration and
player edits can affect vanilla's final exit search. Gameplay calibration is
still needed. No portals, cities or elytra are added or changed in-game.
These limits constrain travel distance, not line of sight or guaranteed visibility.

City starts are searched within a bounded 2,944-block circle around the predicted
arrival. Ships can be up to 2,560 blocks from that arrival (512 + 2 * 1,024), with another
384 blocks allowed for the offset from city start to ship.
An unusually distant city with a very long bridge extending back into the target
area could be missed; this can reject a usable seed but cannot accept a ship
outside the specified distance limits. The check concerns natural first entry after the
first dragon kill, not later gateways from respawned dragons.

## Implementation

Lower48 filtering collects all village and extra-temple placement opportunities
within the requested radii. Families without enough opportunities are rejected
before Nether modeling. Temple resource and gunpowder rolls are also lower48
checks. The gunpowder model includes the second vanilla 1.16.1 temple loot pool;
ordinary DT still skips this unused pool.

For each full seed, village biome viability and abandoned status are checked
before terrain/spawn work. Extra-temple biome checks run only after the regular
DT checks pass, stopping at two viable temples. The End check runs only after all
those gates pass. Its lower48 result is cached per family; a failure stops that
family's sister search, and a pass is reused by subsequent sisters. No Minecraft
worlds, chunk generation, or exact-verification fallback are used.

Private JSONL records use `type: aa_temple` and `aaTempleRule: aa-temple-v4`, with
`gunpowder`, `village: [x,z]`, and `extraTemples: [[x,z],[x,z]]` in addition to the
usual DT metadata. End metadata includes `innerEndGateway`, `outerEndGateway`,
`endFirstShipRadius: 512`, `endConnectionRadius: 1024`, `endShipLayout: connected`, `endCities`, and
`endShips`, all positions as horizontal `[x,z]`. The first element of `endShips`
is the selected reference ship; `endCities` follows the same ordering. The second
ship connects to the first, and the third connects to the first or second.
Seed-free reports have separate reached/rejected/time counters for
`aa_structure_positions`, `aa_gunpowder`, `aa_village`, `aa_extra_temples`, and
`aa_outer_end`. The latter counts actual family evaluations, not cache hits.
Existing v1 samples have **not** passed an End requirement. Earlier records should
not be labeled v4 without rechecking the current geometry and updating metadata.

### End Distance Diagnostics

Set `ZSG_AA_END_SAMPLES` to a new private JSONL path when running `aa_temple` to
retain the first pre-End-qualified seed from each evaluated lower48 family,
including rejected families. This is opt-in, refuses existing files, and does not
alter acceptance or the sister policy. Unset the variable afterward. The normal
timing report alone cannot recover rejected seed identities.

Build the offline probe with `scripts/Build-FilterWorker.ps1 -EndProbeOnly`, then:

```powershell
.\scripts\Measure-AaEndCandidates.ps1 -Candidates run/private-end-candidates.jsonl `
    -OutputDirectory run/private-end-distances
```

The probe records viable ship centers out to 3,072 blocks from the modeled
gateway, with a 384-block city-start allowance. The report compares several
first-ship and companion radii, measures the nearest ship, and calculates the
minimum companion radius needed for each first-ship limit. The historical cluster
comparisons require both companions to share the first ship; `connectedComparisons`
also allows the third ship to connect through the second. All use distinct cities.
Connected comparisons stay within the probe's search envelope. Missing distances mean no
qualifying anchor/group was found in the bounded model search, not proof that
none exists anywhere. Candidate files and diagnostics contain private seeds and
must stay outside public reports and source control.

These are counterfactual results for the sampled families, not a new production
throughput measurement: a looser End gate can change how many sisters are tried
and accepted in each family.

The pinned Cubiomes layout model needs two small End-only corrections: attaching
bridges to the randomly selected tower floor and retaining the original bridge
anchor after adding a ship. `Build-FilterWorker.ps1` applies the tracked patch to
a generated copy; the upstream checkout remains clean. The pinned SeedFinding
layout also has the selected-floor discrepancy, so it is not the layout oracle.
An opt-in offline test compares 128 complete layouts and ship positions with
Minecraft's template generator, without constructing a server/world or generating
chunks. Standalone tests separately compare city placement/terrain with
SeedFinding, first gateway ordering with Java's shuffle, and End sister invariance.

```powershell
.\gradlew.bat -PfilterProbe filterProbeTest -PfilterModelParity=true `
    --tests '*AaEndLayoutParityTest' --tests '*StandaloneModelLootParityTest' --offline
```

The starting search budget is 4,096 sisters and two accepted seeds per family,
borrowed from DT, not yet tuned for AA. The close village requirement may make
this profile substantially rarer. Measure a longer run before forecasting an
overnight seed count.

## Local Search

Build the operator tools as described in [Model Seed Finder](MODEL_SEED_FINDER.md).
The following reserves a fresh family range and runs one worker for at most ten
minutes, stopping sooner if it finds ten seeds. All outputs remain private under
ignored `run/`; it does not upload anything or change an overnight bank.

```powershell
Set-Location "E:\recordings\code\minecraft rooms"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
. .\scripts\FilterBankState.ps1
$families = 100000000L
$offset = Reserve-FilterRange $families "run/model-bank/parallel-offset.json"
$run = Join-Path "run/model-bank" ("aa-temple-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $run | Out-Null
.\scripts\Search-FilterCandidates.ps1 -Type aa_temple `
    -Families $families -Seconds 600 -Target 10 -Stream $offset `
    -OutputFile (Join-Path $run "seeds.jsonl")
```

Keep AA results separate from ordinary DT banks. Gameplay sampling should check
village access, the starting loot, and travel to both extra temples before
publishing candidates to the hosted seed service. Model loot tests compare all four
chests against pinned SeedFinding across 512 seed/coordinate vectors, including
negative coordinates; native tests also check sister invariance and radius limits.

The gameplay integration test uses an isolated creative world and SpeedrunIGT:

```powershell
.\gradlew.bat -PfilterPickerTest -PaaModeSmoke -PreplayTimer=true runFilterPickerTest --offline
```

It checks AA category selection, End-exit exclusion, a still-missing required
advancement, completion without lightning, race-result submission, and Any%
restoration. It does not validate the seed filter's terrain or structures.

## Initial Overworld-Only Check (v1)

The first single-worker smoke search examined 100,000,000 lower48 families and
159,744 sisters in 29.1 seconds of finder time. It accepted one seed: 30 starting
gunpowder, village distance 67.9 blocks, and extra temples at 536.7 and 679.0
blocks. This demonstrates end-to-end model acceptance, not a reliable throughput
estimate or a gameplay validation. No seeds were uploaded. The existing five
profiles also matched the previous executable's decision digests on bounded
100,000-family, 16-sister regression runs per profile.
These timings predate the End gate and are not v2 throughput figures.

## End-Gated Smoke Check (v2)

A bounded single-worker search examined 600,724,169 families and 953,282 sisters
in 180 seconds. Five candidates reached the End check; all five failed, leaving
zero accepted seeds. Actual End-model work took 12.8 ms total, so the new issue is
selectivity rather than End-evaluation cost. This sample is too small to forecast
the full filter's yield. The 384-block requirement was not relaxed.

Separate deterministic End-only tests include a qualifying three-ship fixture,
a rejection fixture, and sister-seed equivalence. All 128 city layouts and ship
coordinates matched the vanilla offline oracle after the two corrections.
These are model tests, not an in-game gateway-arrival calibration or an accepted
full-AA seed pool.

## End Distance Calibration (v3)

The ten-minute single-worker v3 benchmark searched 2,164,635,463 families and
3,472,138 sisters. Twenty-five distinct families reached the End gate, and all
failed the 384-block first-ship / 512-block companion requirement. End checks
took 100.5 ms in total: selectivity, not End-model execution time, limited yield.

An exact-range recovery with private candidate logging reproduced all counters
and the decision digest `61bc134a3910cb7c`. The wider offline probe measured those
same 25 candidates; an independent neighbor-count calculation verified the
radius comparisons:

| First ship from gateway | Each companion from first ship | Passing families / 25 |
| --- | --- | --- |
| 384 | 512 | 0 |
| 384 | 1,024 | 1 |
| 512 | 1,536 | 2 |
| 768 | 1,024 | 1 |
| 768 | 1,536 | 5 |
| 1,024 | 1,024 | 4 |
| 1,024 | 1,536 | 7 |

Only one candidate had any ship within 384 blocks. Its closest ship was about
316.7 blocks from the modeled arrival, and both companions fit within 921 blocks
of it. Across all 25, the median nearest-ship distance was 916.6 blocks, with a
range of 316.7 to 1,980.5. These are bounded-model measurements, not gameplay
validation. The comparisons do not themselves change the configured limits and
are not new throughput figures or guarantees of future yield.

## Connected-Ship Benchmark (v4)

The first fresh ten-minute single-worker v4 search accepted **four seeds from
three distinct lower48 families**, examining 1,804,563,795 families and 2,894,944
sisters. Sixteen families reached the End check; three passed and thirteen failed.
End modeling took 363 ms total. The run used 4,096 sisters, a two-seed family cap,
and private End-candidate logging. No seeds were uploaded.

This run overlapped native compilation and model tests while the Nether preload
code was reviewed, so its family throughput is not an isolated before/after
performance comparison. Four accepted seeds is also too small a sample for a
reliable overnight yield estimate. These are model acceptances, not gameplay
validation or a claim of guaranteed ship visibility.
