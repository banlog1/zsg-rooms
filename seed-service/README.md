# ZSG Rooms Seed Service

Local-first hosted seed-bank delivery. No deployment, Cloudflare resource creation
or remote seed import is performed by the scripts in this directory. The checked-in
Wrangler configuration has a placeholder D1 ID and is for local testing only.

## Flow

1. The model-only finder writes committed `bank.jsonl` snapshots.
2. The operator publishes one or more snapshots into a new private revision.
3. An SQL import stages that revision in D1 and switches the active pointer only
   after all its seed rows exist. Older revisions remain available for rollback.
4. The host requests one seed over HTTPS through its existing private prefetcher.
5. Start or an approved seed change consumes that seed. The existing launch path
   shares it with the room only when launching, then prepares the following seed.

Three separate choices are added alongside FSG: **ZSG Rooms Desert Temple**,
**ZSG Rooms Village**, and **ZSG Rooms Shipwreck**. Their stable specifications
are `rooms-temple-v5`, `rooms-village-v5`, and `rooms-shipwreck-v5`. History and
room snapshots retain these identities. Structure-proximity rules map to the
corresponding vanilla structure. FSG, manual and random choices are unchanged.

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
select one of the three new choices. The room host needs the service connection;
guests receive the launched exact seed through the existing room transport.
For same-machine testing, both the relay-backed and direct room transports use
the same host prefetcher. Setting the seed service does not change the relay URL.

The URL is stored in `.minecraft/config/zsg-rooms-seedbank.txt`. It is deliberately
empty by default until a production endpoint is deployed. Only HTTPS is accepted
outside the explicit localhost/loopback HTTP exception. Credentials, query strings
and fragments are not allowed. Redirects are not followed.

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
revision hash and one string-valued seed. There is no seed-list or bulk-download
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

Before public deployment, create a separate production D1 database and Worker
configuration, choose the real endpoint, and explicitly authorize the upload.
The existing room relay is not modified or redeployed by this feature.

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
