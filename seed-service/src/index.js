import { PROFILE, SCHEMA_VERSION, TYPES, isSeed } from "./bank-format.js";

function response(status, data) {
  return new Response(JSON.stringify(data), { status, headers: {
    "Content-Type": "application/json", "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff", ...(status === 429 ? { "Retry-After": "60" } : {})
  } });
}

async function readBody(request) {
  if (!request.body) throw new Error("body");
  const reader = request.body.getReader();
  let size = 0;
  const parts = [];
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > 2048) { await reader.cancel(); throw new Error("size"); }
      parts.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const part of parts) { bytes.set(part, offset); offset += part.length; }
  return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
}

export function randomSlot(count) {
  const ceiling = Math.floor(4294967296 / count) * count;
  const value = new Uint32Array(1);
  do { crypto.getRandomValues(value); } while (value[0] >= ceiling);
  return value[0] % count;
}

export async function serveSeed(request, env, chooseSlot = randomSlot) {
  const url = new URL(request.url);
  if (url.pathname !== "/v1/seed" || url.search) return response(404, { error: "not_found" });
  if (request.method !== "POST") return response(405, { error: "method_not_allowed" });
  if (!env.BANK || !env.SEED_REQUESTS) return response(503, { error: "bank_not_configured" });
  try {
    // Anonymous-service abuse guard, not authentication or a strict global quota.
    const limit = await env.SEED_REQUESTS.limit({ key: request.headers.get("CF-Connecting-IP") || "local" });
    if (!limit.success) return response(429, { error: "rate_limited" });
    let body;
    try { body = await readBody(request); } catch { return response(400, { error: "invalid_request" }); }
    if (body?.profile !== PROFILE || !TYPES.includes(body.type)
        || typeof body.requestId !== "string" || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(body.requestId)
        || !Array.isArray(body.excludeFamilies) || body.excludeFamilies.length > 16
        || !body.excludeFamilies.every(value => typeof value === "string" && /^(0|[1-9][0-9]{0,14})$/.test(value)
          && BigInt(value) < (1n << 48n))) return response(400, { error: "invalid_request" });
    const bank = await env.BANK.prepare(`SELECT a.revision, t.count FROM bank_active a
      JOIN bank_types t ON t.revision=a.revision WHERE a.profile=? AND t.type=?`)
      .bind(PROFILE, body.type).first();
    if (!bank || !Number.isSafeInteger(bank.count) || bank.count <= 0 || bank.count > 100000000) {
      return response(503, { error: "bank_empty" });
    }
    const slots = Array.from({ length: 32 }, () => chooseSlot(bank.count));
    const { results } = await env.BANK.prepare(`SELECT slot,seed,family FROM bank_seeds
      WHERE revision=? AND type=? AND slot IN (${slots.map(() => "?").join(",")})`)
      .bind(bank.revision, body.type, ...slots).all();
    const rows = new Map(results.map(row => [row.slot, row]));
    const candidates = slots.map(slot => rows.get(slot));
    if (candidates.some(row => !row || !isSeed(row.seed)
        || row.family !== String(BigInt(row.seed) & ((1n << 48n) - 1n)))) {
      return response(503, { error: "bank_invalid" });
    }
    const excluded = new Set(body.excludeFamilies);
    const chosen = candidates.find(row => !excluded.has(row.family));
    if (!chosen) return response(409, { error: "no_fresh_candidate" });
    return response(200, { schemaVersion: SCHEMA_VERSION, profile: PROFILE, type: body.type,
      requestId: body.requestId, revision: bank.revision, seed: chosen.seed });
  } catch {
    // Database failures and request data must never reach response text or logs.
    return response(503, { error: "bank_unavailable" });
  }
}

export default { fetch(request, env) { return serveSeed(request, env); } };
