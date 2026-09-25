# ZSG Rooms Seed Service

Hosted seed-bank delivery with separate local and production configurations.
The publisher only creates private local files; remote imports and deployment
are explicit Wrangler operations. `wrangler.jsonc` has a placeholder D1 ID for
local testing. `wrangler.production.jsonc` pins the separate seed-bank account.

## Flow

1. The model-only finder writes committed `bank.jsonl` snapshots.
2. The operator extends the latest private publication with new seeds only.
3. A resumable uploader appends missing rows and increases each type's available
   count after successful batches. Existing seeds remain usable during growth;
   types become available independently, without waiting for the entire bank.
4. The host requests one seed over HTTPS through its existing private prefetcher.
5. Start or an approved seed change consumes that seed. The existing launch path
   shares it with the room only when launching, then prepares the following seed.

Five separate choices are added alongside FSG: **ZSG Rooms Desert Temple**,
**ZSG Rooms Village**, **ZSG Rooms Shipwreck**, **ZSG Rooms Buried Treasure**,
and **ZSG Rooms Ruined Portal**.
Their stable specifications are `rooms-temple-v5`, `rooms-village-v5`,
`rooms-shipwreck-v5`, `rooms-buried-treasure-v5`, and `rooms-ruined-portal-v5`. History and
room snapshots retain these identities. Structure-proximity rules map to the
corresponding vanilla structure. Individual FSG, manual and vanilla-random
choices are unchanged. ZSG Rooms Mode uses our temple/village/shipwreck/BT
banks at 20% each, our RP at 15%, and external RP Seedbank at 5%.

RP imports require `frame-completable-v3`, at least one golden axe or pickaxe,
and flint and steel, flint plus nine nuggets, or at least five fire charges.
The family cap is four for RP and shipwreck, two for the other types. Old
publications without an RP count remain valid only when they contain no RP rows.

The service returns a seed as a **decimal string**, never a JavaScript number.
The Java client validates the model profile, type, request identity, revision,
schema and signed 64-bit value before using it. Errors never fall back to FSG,
random seeds, another bank or the room name.

## Local Setup

Requires Node 24 for the SQLite-based tests, and the repository's installed
Wrangler under `relay/node_modules`. No new dependency is installed by this setup.
From the repository root:

```powershell
node --test seed-service/test/*.test.mjs
node seed-service/scripts/publish-bank.mjs run/seed-service/publications/my-v5 run/model-bank/overnight/main/bank.jsonl
node relay/node_modules/wrangler/bin/wrangler.js d1 execute zsg-rooms-seeds-local --local --config seed-service/wrangler.jsonc --file run/seed-service/publications/my-v5/import.sql
node relay/node_modules/wrangler/bin/wrangler.js dev --local --config seed-service/wrangler.jsonc --ip 127.0.0.1 --port 8791
```

Always use a **new output directory** for publication. Supply additional completed
snapshot paths after the first input to combine banks. Duplicate seeds, wrong
profiles, invalid coordinates and excess sisters are rejected, not silently
removed. Do not supply per-attempt outputs or mix pre-v5 banks into this profile.
The finder may keep searching: its consolidated snapshot is replaced on export,
not appended while this publisher reads it. New results need another publication.

In Minecraft, open **Room Settings -> Seed Bank...**, enter
`http://127.0.0.1:8791`, and Save. No JVM argument is needed. Create a room and
select one of the bank choices. The room host needs the service connection;
guests receive the launched exact seed through the existing room transport.
For same-machine testing, both the relay-backed and direct room transports use
the same host prefetcher. Setting the seed service does not change the relay URL.

The URL override is stored in `.minecraft/config/zsg-rooms-seedbank.txt`. Missing
or blank configuration uses `https://zsg-rooms-seeds.banlogzzz.workers.dev`.
Existing explicit overrides, including localhost, are preserved. Clear the field
and Save to return to the default. Only HTTPS is accepted
outside the explicit localhost/loopback HTTP exception. Credentials, query strings
and fragments are not allowed. Redirects are not followed.

The commands above are for initial/local full-snapshot testing, not daily live
updates. For production growth use `extend-bank.mjs` and `upload-bank.mjs` as
described in [production operations](PRODUCTION.md). Daily cumulative snapshots
are deduplicated against the previous plan; older live slots are never reassigned.

The first local publication, `run/seed-service/publications/first-v5`, combines the
completed main snapshot and the three benchmark banks: 51,126 seeds (9,158 temple,
41,447 shipwreck, 521 village). It was taken before the extension's final export
and does not include those extra results. Private outputs belong under ignored
`run/`, not source control.

## Verification

The initial local setup passed all 358 Gradle tests and all 9 service/publisher
tests. A real local Wrangler/D1 smoke test served each profile, excluded the
previous family, rejected a mismatched profile and returned 404 for a bulk-bank
path. Seed values were not printed. The settings screen compiles, but still needs
an in-game visual check. The smoke-test server was stopped afterward; use the
local command above to start it for Minecraft testing.

## API And Selection

`POST /v1/seed`, maximum 2 KiB JSON body:

```json
{
  "profile": "zsg-model-only-v5",
  "type": "temple",
  "requestId": "12345678-1234-1234-1234-123456789abc",
  "excludeFamilies": []
}
```

Success returns schema version 1, the matching profile/type/request ID, the active
bank revision ID and one string-valued seed. The bank ID remains stable as rows
are appended; it is not a content hash of the currently available prefix.
There is no seed-list or bulk-download
endpoint. All responses use `Cache-Control: no-store`. SQL import is an operator
operation through Wrangler, not a public administrative API.

Selection uses cryptographically random, uniformly sampled dense slot IDs and
indexed lookups, not `ORDER BY RANDOM()` over the bank. The host keeps up to 16
recently prepared lower-48 families per profile in memory, including prefetched
seeds later discarded after a selection change. The service samples 32 candidates
and chooses the first outside that list. If none qualifies, it returns 409 rather
than a duplicate. A fresh request can sample again. Extremely small banks may not
have another family; choose another bank or add more seeds. This is **not** a
global consumption queue or a never-repeat guarantee across sessions: different
rooms can receive the same seed, and client history resets when Minecraft exits.

Other failures: 400 invalid request, 404 unknown route, 405 wrong method,
429 rate-limited, 503 missing/empty/invalid/unavailable bank. The existing host
prefetcher deduplicates pending requests and launch consumption. There is no
server-side request-ID reservation: retrying a request after a network failure
may receive another seed, which is harmless before launch. The ID correlates a
response; it is not an authentication credential.

## Deployment Boundary

See [production operations](PRODUCTION.md) for the pinned account, staged import
status, remaining activation steps and quota precautions. The existing room
relay is not modified or redeployed by this feature. Always pass the explicit
seed-service production configuration to remote Wrangler commands.

The initial endpoint is anonymous, with a coarse 60-requests/minute limiter keyed
by connecting IP. Shared networks share that allowance. Cloudflare's limiter is
local to a Cloudflare location, not a strict global quota. It is abuse mitigation,
not authenticated access or a guarantee that a determined client cannot collect
seeds through repeated requests. Review authentication, quotas and billing before
public rollout. No numeric seeds, response bodies or raw request errors are logged
by the service. Do not enable external body logging or publish `import.sql`.

References: [D1 local development](https://developers.cloudflare.com/d1/best-practices/local-development/),
[D1 database API](https://developers.cloudflare.com/d1/worker-api/d1-database/),
[Workers rate limits](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/).

## Gameplay Boundary

Delivery sends the exact seed and profile, not model coordinates. Existing spawn
and Nether-entry rules still determine runtime positions. The original snapshots
retain coordinates for later work; the compact service publication does not.

A modeled lava opportunity can fail to generate. The Temple and Village bank
profiles now perform a separate [runtime terrain repair](../docs/SEED_BANK_TERRAIN.md)
to provide two safe, separated lava routes with nearby water where viable. This
does not reject seeds or alter production search. These repaired bank worlds are
not vanilla verifiable runs. Village acceptance still does not prove a natural
golem exists, and wooded terrain is a proxy; no golem repair is implemented.
See [the model profile](../docs/MODEL_SEED_FINDER.md) for acceptance criteria.
