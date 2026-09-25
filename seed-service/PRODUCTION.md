# Seed Service Production

Worker: `https://zsg-rooms-seeds.banlogzzz.workers.dev`

The production config is `seed-service/wrangler.production.jsonc`. It pins account
`3cee401ab7a7bc6f8d9be942204fe527` and D1 database
`ade095f8-532f-4b0a-9220-b3b2410d3d40` (`zsg-rooms-seeds`). These IDs are not
credentials. Wrangler OAuth credentials stay outside the repository. This is
the separate seed-bank account on Workers Free, not the room-relay account.

## Current Publication

Use `run/seed-service/publications/production-v5-20260925-bt-rp` for uploads
and as the base for the next extension. Snapshot revision:
`84a316d22e6bc3acd32c3642131d10a02498669de0d32293fb9829b8bc4a878c`.
The stable bank revision remains
`fd4ac774dfda3d4e0c43999a96a73ecfd33df7844ce35c7194ba57f287991dc8`.

The completed `bt-rp` overnight snapshot contains **38,519 buried-treasure**
and **16,528 ruined-portal** seeds from 9,101 committed batches. All 55,047
were appended to this private plan, preserving every existing slot and pending
temple/shipwreck row. Desired total: **252,452**. Only committed snapshot rows
were imported; the three unfinished reservations remain the supervisor's work.

- All **16,528 RP seeds** are live, using **49,669 writes and 167,243 reads**.
- **15,600 new BT seeds** are live, using **46,878 writes and 181,895 reads**.
  Live counts: **74,523 temple, 24,406 village, 34,784 shipwreck, 15,632 buried
  treasure, 16,528 ruined portal**. Total **165,873**; pending **86,579**:
  22,919 BT from this run plus the unchanged 8,734 temple and 54,926 shipwreck
  backlog. No uploader remains running.
- September 25 UTC preflight analytics reported zero account reads and writes.
  Upload receipts total **96,547 writes and 349,138 reads**, excluding final
  read-only checks, leaving 3,453 nominal writes as headroom. The 19:27 UTC
  analytics query still showed only 89,335 writes due to reporting lag. Do not
  use that lagging number to budget more writes today; recheck after UTC reset.
- The seed-service Worker now supports `ruined_portal`; deployed version
  `e5f153fa-2de7-43fb-ad1f-0bc3451753c2`. The room relay was not deployed.
- The mod exposes **ZSG Rooms Ruined Portal**, with existing portal art, HUD
  identity and automatic chest/obsidian repair. Its generated-chest lookup does
  not require Looting; the external RP Seedbank retains that requirement.
  Existing external choices remain selectable. ZSG Rooms Mode now draws our
  temple/village/shipwreck/BT at 20% each, our RP at 15%, and external RP
  Seedbank at 5%. This draw-policy change needs no service or relay deployment.
- All 32,128 newly uploaded rows match the overnight snapshot and publication.
  Live delivery and recent-family exclusion passed for all five types. Private
  receipts: `upload-ruined_portal.json`, `upload-buried_treasure.json`, and
  `verification.json` in this publication directory.
- The mod build passed (479 tests, one skipped, no failures), as did all 26
  seed-service tests. The in-game picker was not manually opened in this check.
- Seed values and upload receipts remain private under ignored `run/`.

## Previous Update, September 24 UTC Buried Treasure

`run/seed-service/publications/production-v5-20260924-buried-treasure` was used
for this update and is now superseded. Snapshot revision:
`e6498403f12a19e1e68e35d5e0dc3b32a9146e402f4d171c2ed0df3dbc0ef1ab`.
The stable bank revision remains
`fd4ac774dfda3d4e0c43999a96a73ecfd33df7844ce35c7194ba57f287991dc8`.

- Added **32 buried-treasure seeds from 20 families**, including 7 stables
  seeds across 4 layouts, from the user-approved two-minute sample.
- Live counts: **74,523 temple, 24,406 village, 34,784 shipwreck, 32 buried
  treasure**. Total **133,745**; desired total **197,405**; pending **63,660**.
  Existing slots and the pending temple/shipwreck batches were left unchanged.
- This upload used **99 writes and 133,803 reads**, excluding final checks.
  Preflight analytics reported 97,480 writes, so recorded September 24 UTC
  writes now total **97,579**. Leave the remaining nominal 2,421 writes as
  headroom; recheck usage after the next UTC reset before bulk uploading.
- The seed-service Worker was deployed to accept `buried_treasure`; version
  `b8e029ec-001f-4249-87a4-fdb994eef2d4`. No relay deployment was needed.
- All 32 uploaded rows match the source sample. Live delivery and recent-family
  exclusion passed for all four types. Private receipt: `verification.json`
  alongside this publication. All 24 seed-service tests and the mod build pass.
- The new mod build exposes **ZSG Rooms Buried Treasure** on the Rooms filter
  tab. Existing Mapless choices and random-mode weights remain unchanged.
  The overnight scheduler still supports only the original three types.
- No uploader remains running. Seed values remain private under ignored `run/`.

## Previous Update, September 24 UTC Evening

`run/seed-service/publications/production-v5-20260924-latest-priority` was used for
uploads and as the base for the next extension. Snapshot revision:
`86eb9247b778f905790f8ce3858120223f88f307bde73ffae807838c1ae7978e`.
The stable bank revision remains
`fd4ac774dfda3d4e0c43999a96a73ecfd33df7844ce35c7194ba57f287991dc8`.

The latest `all-types-wood-first` run ended normally on September 24 at 15:24 UTC,
with zero active workers and 10,327 completed batches. Its snapshot contains
8,734 temple, 4,277 village and 47,193 shipwreck seeds. All 60,204 passed
publication validation and were added to the private plan; only villages and
shipwrecks were requested for this upload.

- All 4,277 new villages are live: **24,406 active**, using 12,854 writes.
- **9,734 latest-run shipwrecks** were added: **34,784 active**, using 29,251 writes.
- Temple remains **74,523 active**; all 8,734 new temples remain pending.
- Active total: **133,713**. Desired plan total: **197,373**. Pending: **63,660**,
  comprising 8,734 temples, 37,459 latest-run shipwrecks and 17,467 older shipwrecks.
- At the user's explicit request, the latest shipwrecks were prioritized over
  the older unpublished backlog. Only the unuploaded tail was reordered; all
  25,050 previously live shipwreck slots and every temple/village slot were
  preserved. The uploader verified the complete remote prefix before writing.
  The intermediate `production-v5-20260924-all-types` plan is superseded and must
  not be used for uploads. Resume or extend the priority plan named above.
- Account-wide analytics reported 55,375 writes before this evening upload.
  These uploads used **42,105 writes** and 272,099 reads, excluding preflight and
  final checks. Recorded writes for September 24 UTC now total **97,480**.
  Uploads stopped with 2,520 writes of nominal headroom; wait until the next UTC
  day and recheck account usage before uploading more. Analytics can lag writes.
- Final checks matched every new row to both the private publication and latest
  overnight snapshot, including preceding boundary slots. Counts, plan identity,
  live delivery and recent-family exclusion passed for all three types. The
  private `verification.json` receipt is stored alongside the priority plan.
- No uploader remains running. No Worker or relay deployment or mod update was
  needed. Seed values and publication files remain private under ignored `run/`.

## Previous Update, September 24 UTC Morning

Use `run/seed-service/publications/production-v5-20260924-wood-first` for uploads
and as the base for that update (now superseded). Snapshot revision:
`a1f425abcb7d1c129c2d209fac52da9e6fa52656992d3ef15c033c7dda7d0770`.
The stable bank revision remains
`fd4ac774dfda3d4e0c43999a96a73ecfd33df7844ce35c7194ba57f287991dc8`.

The latest `temple-village-wood-first` run ended normally on September 23 at
15:42 UTC, with zero active workers and 28,803 completed batches. Its cumulative
snapshot contains 36,347 temple and 17,899 village seeds. Extending the previous
publication deduplicated prior results and added **18,427 new seeds**: 12,387
temple and 6,040 village. All passed publication validation; existing slots and
the older shipwreck backlog are preserved.

- All 6,040 new village seeds are live: **20,129 active**, using 18,152 writes.
- All 12,387 new temple seeds are live: **74,523 active**, using 37,223 writes.
- Shipwreck remains **25,050 active**; 17,467 older seeds remain pending.
- Active total: **119,702**. Desired plan total: **137,169**. Every new seed
  from this overnight snapshot is live.
- Before uploading on September 24 UTC, account-wide D1 analytics reported
  zero reads and writes since midnight UTC. The two uploads used **55,375
  writes** and 245,950 reads, excluding read-only preflight and final checks.
  Allow for analytics lag and other activity when budgeting further uploads.
- Final verification matched the registered plan, active counts and every new
  row, including the preceding slot at each upload boundary. Live delivery and
  recent-family exclusion passed for all three types. A private verification
  receipt is stored alongside the publication. All 22 seed-service tests passed.
- No uploader remains running. No Worker or relay deployment or mod update was
  needed. All seed data remains private under ignored `run/`.

## Previous Update, September 23 UTC

Use `run/seed-service/publications/production-v5-20260923-wood-first` for uploads
and as the base for that update (now superseded). Snapshot revision:
`ddb6f61828bcfce137c99321db66c4ecfb10117a488b44c759f9ffda728a6b56`.
The stable bank revision remains
`fd4ac774dfda3d4e0c43999a96a73ecfd33df7844ce35c7194ba57f287991dc8`.

The latest `temple-village-wood-first` snapshot ended normally on September 21 at
17:00 UTC, with zero active workers and 19,173 completed batches. It contains
23,960 temple and 11,859 village seeds cumulatively. Extending the previous plan
deduplicated the prior snapshot and added **17,950 new seeds**: 12,043 temple and
5,907 village. All passed publication validation; previous slots and the older
shipwreck backlog are preserved.

- All 5,907 new village seeds are live: **14,089 active**, using 17,752 writes.
- All 12,043 new temple seeds are live: **62,136 active**, using 36,190 writes.
- Shipwreck remains **25,050 active**; 17,467 older seeds remain pending.
- Active total: **101,275**. Desired plan total: **118,742**. Only the older
  shipwreck backlog remains pending; every new seed from this snapshot is live.
- Before uploading on September 23 UTC, the dedicated account's only D1 database
  reported zero writes in the previous 24 hours. The completed uploads used
  **53,942 writes** and 208,953 reads, excluding read-only preflight and final checks.
  Allow for analytics lag and other activity before spending the remaining quota.
- Final verification matched the registered plan, active counts and every newly
  uploaded row against the private publication, including the preceding slot at
  each boundary. Live delivery and recent-family exclusion passed for all three
  types. No uploader remains running.
- No Worker or relay deployment is part of this update. Seed data remains private
  under ignored `run/`; no seed values are printed.

## Previous Update, September 20 UTC

Use `run/seed-service/publications/production-v5-20260921-wood-first` for uploads
and as the base for that update (now superseded). Snapshot revision:
`c789e12658087cc7fb555b9d0d21cb1cfa5f3ed953ed98e7f66a80c98d13b218`.
The stable bank revision remains
`fd4ac774dfda3d4e0c43999a96a73ecfd33df7844ce35c7194ba57f287991dc8`.

The latest `temple-village-wood-first` run ended normally on September 20 at
15:23 UTC, with zero active workers and 9,562 completed batches. Its snapshot
contains **17,869 new seeds**: 11,917 temple and 5,952 village. All passed
publication validation and were appended without changing existing slots.
Only this latest run was added, not the older unpublished overnight snapshots.

- All 5,952 new village seeds are live: **8,182 active**, using 17,887 writes.
- All 11,917 new temple seeds are live: **50,093 active**, using 35,811 writes.
- Shipwreck is unchanged at **25,050 active**; 17,467 older seeds remain pending.
- Active total: **83,325**. Desired plan total: **100,792**.
- Uploads completed September 20 UTC (September 21 locally). Account-wide D1
  analytics reported zero writes before uploading. These uploads used **53,698
  writes** and 173,093 reads, excluding read-only preflight and final checks.
  Allow for analytics lag and other account activity when budgeting more writes.
- Final active counts, registered plan and last uploaded slots matched the
  private publication. Live delivery and recent-family exclusion passed for all
  three types. No seed values were printed.
- No uploader remains running. No Worker deployment or mod update was needed.

## Previous Update, September 16 UTC

That update used `run/seed-service/publications/production-v5-20260916-evening`.
Snapshot revision:
`a53343986ed1e3cdbf3b5d328ce8d4d781554b5104a27ad9c76b06e0f14159a2`.
The stable bank revision remains
`fd4ac774dfda3d4e0c43999a96a73ecfd33df7844ce35c7194ba57f287991dc8`.

The latest temple/village session ended normally on September 16 at 16:32 UTC,
with zero active workers. Its cumulative snapshot contains 28,806 temple and
1,696 village seeds from 19,044 completed batches. Extending the previous plan
deduplicated that snapshot and added **16,769 new seeds**: 15,840 temple and 929
village. Earlier slots and the pending shipwreck backlog were preserved.

- All 929 new village seeds are live: **2,230 active**, using 2,793 writes.
- All 15,840 new temple seeds are live: **38,176 active**, using 47,600 writes.
- Shipwreck is unchanged at **25,050 active**; 17,467 older seeds remain pending.
- Active total: **65,456**. Desired plan total: **82,923**.
- Before uploading, account-wide D1 analytics reported zero writes since
  September 16 00:00 UTC. These two uploads used **50,393 writes** and 132,307
  reads, excluding the small read-only preflight and final checks. Allow for
  analytics lag and other account activity before spending the remaining quota.
- Final active counts, the registered plan and each type's last uploaded slot
  matched the private publication. Live delivery passed for all three types;
  recent-family exclusion also passed. No seed values were printed.
- No uploader remains running. No Worker deployment or mod update was needed.

## Previous Update, September 15 UTC

Use `run/seed-service/publications/production-v5-20260916` for uploads and as the
base for that update (now superseded by the evening publication above). Its snapshot revision is
`6ad83ddc52151c72420bf2af23f6a855731627e39294e195085c2063a05300c0`;
the stable bank revision remains
`fd4ac774dfda3d4e0c43999a96a73ecfd33df7844ce35c7194ba57f287991dc8`.

The temple/village overnight run completed 8,553 batches and produced 12,966
temple and 767 village seeds. All 13,733 were validated and added to the plan,
bringing its desired totals to 22,336 temple, 1,301 village and 42,517 shipwreck.
Existing slots and pending rows from the previous plan are preserved.

- Village is fully uploaded: 1,301 active seeds; this update added 1,251 rows
  using 3,761 writes, including previously pending village seeds.
- Temple is fully uploaded: 22,336 active seeds; this update added 17,286 rows
  using 51,945 writes. An interrupted attempt added no rows; the retry completed.
- Shipwreck remains at 25,050 active seeds; its older backlog is not part of
  this temple/village update.
- Active total: 48,687 seeds. All 13,733 newly generated seeds, plus the older
  temple/village backlog, are live. Only 17,467 older shipwreck seeds are pending.
- Successful uploads used 55,706 writes on September 15 UTC. Final live checks
  passed for all three types, including exact publication membership and recent
  family exclusion. No seed values were printed. No upload remains running.

## Initial Import History

Publication: `run/seed-service/publications/production-v5-20260915-staged`.
Revision: `fd4ac774dfda3d4e0c43999a96a73ecfd33df7844ce35c7194ba57f287991dc8`.

The latest completed main bank and the three completed worker benchmark banks
produce 52,421 seeds: 9,370 temple, 534 village and 42,517 shipwreck.

- Worker deployed. Its database binding and rate limiter are configured.
- Part 001 uploaded on **2026-09-14 UTC**: 25,000 seeds, **75,010 rows written**.
- The verified 25,000-row shipwreck prefix was activated using 9 metadata writes.
- A live append test added 150 seeds using 455 writes. All three types passed
  live delivery and recent-family exclusion checks against their uploaded subsets.
- A temple-only top-up added 5,000 seeds using 15,100 writes. **Then-active
  counts: 25,050 shipwreck, 5,050 temple and 50 village.** Live temple requests
  returned newly added seeds during the upload.
- The remaining 22,271 seeds have **not** been uploaded.
- Recorded write operations total 90,574 for September 14 UTC. Cloudflare's
  aggregate metrics may lag recent writes; use operation totals when budgeting.
- No background upload or scheduled task is running.

The Free plan allows 100,000 row writes per UTC day, including index writes.
The current schema writes about three rows per seed plus small metadata updates.
Do not import the full `import.sql` on this plan: it exceeds a day's allowance.
Use the resumable uploader below, not the old bulk-import/activation files.
See [D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/).

## Resume Uploading

Check D1 account usage in the dashboard first. Limits reset at 00:00 UTC
(03:00 Sofia during summer). Other D1 activity shares the allowance. The
`--write-budget` is an operator-supplied allowance for this invocation, not an
automatic account quota lookup. Leave headroom and do not reuse the full daily
budget on several runs in the same UTC day.

From the repository root, with at least 90,000 daily writes still available:

```powershell
node seed-service/scripts/upload-bank.mjs run/seed-service/publications/production-v5-20260924-buried-treasure --write-budget 90000
```

The uploader verifies existing rows privately against the desired publication,
then inserts only missing rows in 50-seed batches, rotating through the four
types. Each successful batch increases that type's available count. It stops
before the next batch would exceed the supplied write allowance. Re-run the
same command with an appropriate budget to resume, including after a crash.
Counts and quota metrics are printed; seed values and SQL are not.

`--batch-size 200` reduces command-launch overhead for larger uploads. The default
remains 50; the allowed range is 1-200 to keep SQL and Windows command lines
bounded. Changing this size between resumes does not change slot assignments.
Write-budget accounting still applies to each batch.

To focus on one type, add `--type temple`, `--type village`, `--type shipwreck`,
or `--type buried_treasure`.
For example, with at least 16,000 writes still available, add at most 5,000 temple
seeds without adding other types:

```powershell
node seed-service/scripts/upload-bank.mjs run/seed-service/publications/production-v5-20260924-buried-treasure --write-budget 16000 --max-new-seeds 5000 --type temple
```

To verify and activate already-uploaded rows without adding seeds:

```powershell
node seed-service/scripts/upload-bank.mjs run/seed-service/publications/production-v5-20260924-buried-treasure --write-budget 100 --max-new-seeds 0
```

No Worker deployment or full-publication activation is needed when adding seeds.
The normal query API is used, not `d1 execute --file`, so routine growth does not
put D1 into bulk-import maintenance. Queries can still experience normal database
contention. Existing rows are never deleted or overwritten. If interrupted after
inserting a batch but before advertising it, the previous count remains safe;
resuming verifies and advertises the new prefix.

Initial verification reads all existing rows for this bank on each invocation.
These reads count toward D1's daily read allowance. Avoid tight retry loops.
No background task needs to remain running while waiting for quota reset.

In Minecraft, **Room Settings -> Seed Bank...**, clear an old localhost override
and Save to use the new default. Only the host requests seeds from the service.
The mod must include the production-default change, or set the URL explicitly
on an older build. A type with zero uploaded seeds still reports unavailable.

## Later Publications

Extend the **latest planned publication**, even if some of it is still waiting
to upload. Existing seeds keep their original slots; repeated seeds in cumulative
finder snapshots are skipped. Changed type assignments, invalid records and
excess sister seeds are rejected. Nothing is uploaded by the extension command.

```powershell
node seed-service/scripts/extend-bank.mjs run/seed-service/publications/production-v5-20260924-buried-treasure run/seed-service/publications/next-v5 run/model-bank/overnight/all-types-wood-first/bank.jsonl
node seed-service/scripts/upload-bank.mjs run/seed-service/publications/next-v5 --write-budget 90000
```

Use a new output directory each day and replace the base path with yesterday's
latest plan. Additional completed snapshot paths can follow the first input.
The private manifest records ancestry and the stable `bankRevision` that identifies
the growing D1 collection. Its `revision` hashes that particular desired snapshot.
The API's existing `revision` field identifies the stable bank collection; it is
not a promise of a fixed seed count. No client protocol changes are required.

The database registers the latest upload plan. A stale or competing branch fails
closed instead of replacing current assignments. Use one operator/uploader at a
time; this is not a distributed job queue. Preserve the private publication
chain. `publish-bank.mjs` remains for initial/local full snapshots; do not use a
fresh independent snapshot as a daily replacement for the growing live bank.

Storage grows with new seeds, not daily copies of all existing seeds. Existing
SQL import files are retained for offline use but are not the live update path.

Keep all publication files under ignored `run/`. Never upload them as public
assets, include them in a mod JAR, or commit them. No public bulk/admin route is
provided. The anonymous 60/minute/IP limiter is not authentication or a strict
account-wide cost cap. Worker observability and the uploader's Wrangler disk
logging are disabled; never add seed/body logging when troubleshooting.

The growth path passes 22 service tests, including fixed slot assignments,
deduplication, partial availability, interrupted uploads, daily budget stops,
extending unfinished plans, type-specific uploads, bounded batch sizes, family
caps and stale-plan rejection. Live shipwreck
requests also succeeded while the append test was running. These tests do not
guarantee zero latency impact from database contention at higher traffic volumes.

To redeploy only the seed service:

```powershell
node relay/node_modules/wrangler/bin/wrangler.js deploy --config seed-service/wrangler.production.jsonc
```

Do not use the relay configuration for this account. Logging into a different
Wrangler account does not change an already deployed relay.
