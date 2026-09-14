import { test } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { readFile, mkdtemp, writeFile, rm } from "node:fs/promises";
import { join, resolve, dirname, basename } from "node:path";
import { tmpdir } from "node:os";
import worker, { serveSeed, randomSlot } from "../src/index.js";
import { PROFILE } from "../src/bank-format.js";
import { publishBank } from "../scripts/publish-bank.mjs";

const id = "12345678-1234-1234-1234-123456789abc";
const revision = "a".repeat(64);
const first = "9223372036854775807";
const second = "9007199254740993";
const family = seed => String(BigInt(seed) & ((1n << 48n) - 1n));

function request(changes = {}, url = "https://seeds.test/v1/seed") {
  return new Request(url, { method: "POST", body: JSON.stringify({ profile: PROFILE, type: "temple",
    requestId: id, excludeFamilies: [], ...changes }) });
}

function binding(database) {
  return { prepare(sql) { return { bind(...args) { return {
    async first() { return database.prepare(sql).get(...args) || null; },
    async all() { return { results: database.prepare(sql).all(...args) }; }
  }; } }; } };
}

async function fixture(t) {
  const db = new DatabaseSync(":memory:");
  t.after(() => db.close());
  db.exec(await readFile(new URL("../schema.sql", import.meta.url), "utf8"));
  db.prepare("INSERT INTO bank_active VALUES (?,?)").run(PROFILE, revision);
  db.prepare("INSERT INTO bank_types VALUES (?,?,?)").run(revision, "temple", 2);
  db.prepare("INSERT INTO bank_seeds VALUES (?,?,?,?,?)").run(revision, "temple", 0, first, family(first));
  db.prepare("INSERT INTO bank_seeds VALUES (?,?,?,?,?)").run(revision, "temple", 1, second, family(second));
  return { db, env: { BANK: binding(db), SEED_REQUESTS: { async limit() { return { success: true }; } } } };
}

test("one exact seed, requested identity, no-store and no bulk endpoint", async t => {
  const { env } = await fixture(t);
  const response = await serveSeed(request(), env, () => 0);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  const body = await response.json();
  assert.equal(body.seed, first);
  assert.equal(body.requestId, id);
  assert.equal(body.type, "temple");
  assert.equal(body.profile, PROFILE);
  assert.equal((await serveSeed(request({}, "https://seeds.test/bank.jsonl"), env)).status, 404);
  assert.equal((await worker.fetch(request(), env, { waitUntil() {} })).status, 200);
});

test("recent families are excluded, without changing filters or returning a duplicate", async t => {
  const { env } = await fixture(t);
  let n = 0;
  const result = await serveSeed(request({ excludeFamilies: [family(first)] }), env, () => n++ % 2);
  assert.equal((await result.json()).seed, second);
  const exhausted = await serveSeed(request({ excludeFamilies: [family(first), family(second)] }), env);
  assert.equal(exhausted.status, 409);
  assert.ok(!(await exhausted.text()).includes(first));
});

test("invalid, empty, unavailable and rate-limited requests fail without seed data", async t => {
  const { env } = await fixture(t);
  for (const changes of [{ profile: "old" }, { type: "random" }, { requestId: "x" }, { excludeFamilies: [123] }, { excludeFamilies: ["281474976710656"] }]) {
    assert.equal((await serveSeed(request(changes), env)).status, 400);
  }
  assert.equal((await serveSeed(request({ type: "village" }), env)).status, 503);
  assert.equal((await serveSeed(request(), {})).status, 503);
  assert.equal((await serveSeed(new Request("https://seeds.test/v1/seed"), env)).status, 405);
  assert.equal((await serveSeed(new Request("https://seeds.test/v1/seed", { method: "POST", body: "x".repeat(2049) }), env)).status, 400);
  const limited = await serveSeed(request(), { ...env, SEED_REQUESTS: { async limit() { return { success: false }; } } });
  assert.equal(limited.status, 429);
  assert.equal(limited.headers.get("retry-after"), "60");
  const failed = await serveSeed(request(), { ...env, BANK: { prepare() { throw new Error(first); } } });
  assert.equal(failed.status, 503);
  assert.ok(!(await failed.text()).includes(first));
});

test("missing or malformed indexed data never becomes a valid response", async t => {
  const { db, env } = await fixture(t);
  db.exec("DELETE FROM bank_seeds WHERE slot=0");
  assert.equal((await serveSeed(request(), env, () => 0)).status, 503);
  db.exec("UPDATE bank_seeds SET family='0' WHERE slot=1");
  assert.equal((await serveSeed(request(), env, () => 1)).status, 503);
});

test("slot RNG remains within dense index bounds", () => {
  for (const count of [1, 7, 50000, 100000000]) {
    for (let i = 0; i < 100; i++) { const slot = randomSlot(count); assert.ok(slot >= 0 && slot < count); }
  }
});

test("SQL import is repeatable and a partial revision never replaces the active bank", async t => {
  const { db, env } = await fixture(t);
  const directory = await mkdtemp(join(tmpdir(), "zsg-bank-sql-"));
  try {
    const input = join(directory, "bank.jsonl");
    const rows = [first, second].map(seed => ({ seed, family: family(seed), profile: PROFILE, type: "temple",
      status: "MODEL_ACCEPTED", structure: [16, 32], entry: [24, 40], spawn: [8, 8], bastion: [16, 16], fortress: [80, 80] }));
    await writeFile(input, rows.map(row => JSON.stringify(row)).join("\n"));
    const out = join(directory, "published");
    const manifest = await publishBank([input], out);
    const sql = await readFile(join(out, "import.sql"), "utf8");
    const activation = sql.substring(sql.lastIndexOf("INSERT INTO bank_active"));
    db.exec(activation);
    assert.equal(db.prepare("SELECT revision FROM bank_active").get().revision, revision);
    db.exec(sql);
    db.exec(sql);
    assert.equal(db.prepare("SELECT revision FROM bank_active").get().revision, manifest.revision);
    assert.equal(db.prepare("SELECT COUNT(*) AS n FROM bank_seeds WHERE revision=?").get(manifest.revision).n, 2);
    assert.equal((await serveSeed(request(), env)).status, 200);
  } finally {
    assert.equal(dirname(resolve(directory)), resolve(tmpdir()));
    assert.ok(basename(directory).startsWith("zsg-bank-sql-"));
    await rm(directory, { recursive: true, force: true });
  }
});
