# Seed Service Production Operations

Worker: `https://zsg-rooms-seeds.banlogzzz.workers.dev`

Run commands from the repository root. Use
[`wrangler.production.jsonc`](wrangler.production.jsonc) for every remote
seed-service operation. It pins the separate seed-bank account and D1 database,
not the room relay. Keep Wrangler credentials outside the repository.

## Private Publication State

Publication directories, source snapshots, SQL, upload receipts, live counts and
the operator's current-publication pointer belong in ignored `run/` or
`local-notes/`, never in source control, release assets or mod JARs. This page
describes the procedure, not the current queue or daily quota.

Before extending or uploading, identify the latest authoritative publication
from the private operator journal and receipts. Verify its ancestry and uploaded
prefix against production. Do not choose an old plan or infer the active plan
solely from a directory's name or modification time.

A publication's `revision` identifies the desired snapshot. Its stable
`bankRevision` identifies the collection as it grows; it is not a hash of the
currently available prefix. Preserve existing slots, structure metadata and the
unpublished queue when extending. Routine growth must not replace the bank.

## Preflight

1. Confirm the seed-service account and database in the explicit production
   configuration. Allow only one operator/uploader to update the bank at a time.
2. Check current account-wide reads and writes for the UTC quota day. Include
   earlier uploads, other activity and pending operations; leave safety headroom.
3. Check the private publication's completed source snapshots, checksums and
   pending counts. Do not import unfinished attempts or overlapping raw outputs.
4. Run the local service tests before using changed publishing tools:

```powershell
node --test seed-service/test/*.test.mjs
```

`--write-budget` requires at least 100 and is an allowance for one invocation,
not a live quota lookup.
Analytics can lag recent writes: reconcile usage with receipts conservatively.
Do not give successive invocations the same full-day allowance. Metadata,
indexes and advertisement updates also consume writes; a seed count is not a
write count. Readback verification consumes reads, so avoid repeated full-bank
preflights or retry loops.

## Extend The Plan

Use a new output directory and completed, consolidated `bank.jsonl` snapshots.
The arguments below are placeholders for private paths:

```powershell
node seed-service/scripts/extend-bank.mjs BASE_PUBLICATION NEW_PUBLICATION COMPLETED_BANK_JSONL
```

Additional snapshots can follow the first input. Cumulative snapshots are
deduplicated against the existing plan. Invalid profiles, conflicting records,
invalid structure coordinates and excess sisters fail validation. The command
only prepares local files; it does not upload or deploy anything.

Check the new manifest before making it the current private plan. Keep its
source provenance and the previous publication for recovery. Do not reorder
already published slots to prioritize new seeds. Changing an unpublished tail
requires a separately validated plan that preserves every live slot.

## Upload And Resume

After preflight, supply the remaining safe allowance for this invocation:

```powershell
node seed-service/scripts/upload-bank.mjs NEW_PUBLICATION --write-budget WRITE_ALLOWANCE
```

The uploader checks existing rows, inserts only missing seeds, publishes structure
metadata before advertising them, and increases each type's available prefix
after successful batches. Existing seeds remain usable during an upload.

Optional controls:

- `--type`: restrict to `temple`, `village`, `shipwreck`,
  `buried_treasure`, `ruined_portal` or `aa_temple`.
- `--max-new-seeds N`: limit added seeds. Zero permits metadata/publication
  updates for existing rows; **it is not a read-only dry run**.
- `--batch-size N`: use 1-200 seeds per batch; the default is 50.

After interruption, reconcile usage and resume the same latest plan with a
freshly calculated allowance. Do not delete live rows, rebuild from a partial
snapshot or run competing uploads. Save receipts privately, check remote
readback and delivery for changed types, and update the private journal with
the remaining queue and account usage. Never print numeric seeds in public logs.

## Structure Metadata

Modern clients require each seed's modeled starting-structure coordinates.
Private `bank_structures` pages bind up to 256 coordinate records to their seed
strings and slots. Uploads verify existing prefixes and read back metadata before
new counts become available.

For a legacy publication missing coordinates, recover them from saved accepted
model records, without rerunning world generation:

```powershell
node seed-service/scripts/backfill-structures.mjs BASE_PUBLICATION NEW_PUBLICATION SOURCE_BANK_JSONL
node seed-service/scripts/upload-bank.mjs NEW_PUBLICATION --write-budget WRITE_ALLOWANCE --max-new-seeds 0
```

Additional source files may follow the first. Missing or conflicting coordinates
fail closed. The enriched publication preserves the bank ID and slots and must
be the base for future extensions. Budget this operation like any other write.

## Deployment Boundary

Routine seed uploads need no Worker deployment, room-relay deployment or mod
release. Only deploy when seed-service code or configuration changes:

```powershell
node relay/node_modules/wrangler/bin/wrangler.js deploy --config seed-service/wrangler.production.jsonc
```

`publish-bank.mjs` and full `import.sql` loading are for initial/local snapshot
setup, not routine replacement of production. See the [service guide](README.md)
for local setup, API validation, selection rules and gameplay boundaries.

The anonymous request limiter is abuse mitigation, not a global collection or
billing cap. Do not enable response-body logging or publish seed SQL. Review
access controls and account limits separately from upload budgeting.
