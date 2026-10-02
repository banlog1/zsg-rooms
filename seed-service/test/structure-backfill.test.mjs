import { test } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { mkdtemp, writeFile, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { PROFILE, TYPES } from "../src/bank-format.js";
import { recoverStructures, backfillStructures } from "../scripts/backfill-structures.mjs";
import { digest, readPublication } from "../scripts/bank-publication.mjs";
import { syncStructurePages } from "../scripts/structure-pages.mjs";
import { structureForSlot } from "../src/structure-pages.js";
import { serveSeed } from "../src/index.js";

const revision = "a".repeat(64);
const row = (seed, structure = [32, 192]) => ({ profile: PROFILE, type: "village", seed, family: seed, structure });
const publication = rows => ({ rows, bankRevision: revision,
  byType: Object.fromEntries(TYPES.map(type => [type, rows.filter(r => r.type === type)])) });

test("backfill preserves exact seed strings, revision lineage and every slot; missing and conflicting sources fail", async t => {
  const directory = await mkdtemp(join(tmpdir(), "zsg-structure-test-"));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const rows = [row("1", undefined), row("2", undefined)].map(({structure, ...r}) => r);
  const content = rows.map(r => JSON.stringify(r)).join("\n") + "\n";
  await writeFile(join(directory, "bank.jsonl"), content);
  await writeFile(join(directory, "manifest.json"), JSON.stringify({schemaVersion: 1, profile: PROFILE,
    revision: digest(content), counts: Object.fromEntries(TYPES.map(type => [type, type === "village" ? 2 : 0])), total: 2}));
  const source = join(directory, "source.jsonl");
  const record = r => ({...r, status: "MODEL_ACCEPTED"});
  await writeFile(source, JSON.stringify(record(row("2"))));
  const base = await readPublication(directory);
  assert.equal((await recoverStructures(base, [source])).missing.village, 1);
  await assert.rejects(backfillStructures(directory, join(directory, "incomplete"), [source]), /missing/);
  await writeFile(source, [row("2"), row("1"), row("1")].map(r => JSON.stringify(record(r))).join("\n"));
  const output = join(directory, "complete");
  await backfillStructures(directory, output, [source]);
  const completed = await readPublication(output);
  assert.deepEqual(completed.rows.map(r => r.seed), ["1", "2"]);
  assert.equal(completed.bankRevision, base.bankRevision);
  assert.ok(completed.manifest.ancestors.includes(base.manifest.revision));
  assert.equal(await readFile(join(directory, "bank.jsonl"), "utf8"), content);
  await writeFile(source, [row("1"), row("1", [33, 192])].map(r => JSON.stringify(record(r))).join("\n"));
  await assert.rejects(recoverStructures(base, [source]), /Conflicting/);
});

async function fixture(t) {
  const db = new DatabaseSync(":memory:");
  t.after(() => db.close());
  db.exec(await readFile(new URL("../schema.sql", import.meta.url), "utf8"));
  const execute = async sql => {
    const before = db.prepare("SELECT total_changes() AS n").get().n;
    const rows = db.prepare(sql).all();
    const changes = db.prepare("SELECT total_changes() AS n").get().n - before;
    return {rows, rowsRead: rows.length, rowsWritten: changes * 2};
  };
  return {db, execute};
}

test("coordinate pages resume safely, extend a partial page and reject changed slots or insufficient budget", async t => {
  const {db, execute} = await fixture(t);
  const rows = Array.from({length: 600}, (_, i) => row(String(i + 1), [i, -i]));
  const first = publication(rows.slice(0, 260));
  await syncStructurePages(first, execute, {writeBudget: 100});
  assert.equal((await syncStructurePages(first, execute, {writeBudget: 100})).changedPages, 0);
  const full = publication(rows);
  await assert.rejects(syncStructurePages(full, execute, {writeBudget: 1}), /budget/);
  let failed = false;
  await assert.rejects(syncStructurePages(full, async sql => {
    const result = await execute(sql);
    if (!failed && sql.includes("INSERT INTO bank_structures")) { failed = true; throw new Error("interrupted"); }
    return result;
  }, {writeBudget: 100}), /interrupted/);
  await syncStructurePages(full, execute, {writeBudget: 100});
  for (const slot of [0, 255, 256, 259, 511, 599]) {
    const page = db.prepare("SELECT entries FROM bank_structures WHERE page=?").get(Math.floor(slot / 256));
    assert.deepEqual(structureForSlot(page.entries, slot, String(slot + 1)), [slot, slot === 0 ? 0 : -slot]);
    assert.throws(() => structureForSlot(page.entries, slot, "9999"), /match/);
  }
  rows[0] = row("1", [7, 8]);
  await assert.rejects(syncStructurePages(publication(rows), execute, {writeBudget: 100}), /conflict/);
});

test("service returns only the chosen seed target, fails closed for new clients and remains compatible with old clients", async t => {
  const {db, execute} = await fixture(t);
  db.prepare("INSERT INTO bank_active VALUES (?,?)").run(PROFILE, revision);
  db.prepare("INSERT INTO bank_types VALUES (?,?,?)").run(revision, "village", 1);
  db.prepare("INSERT INTO bank_seeds VALUES (?,?,?,?,?)").run(revision, "village", 0, "1", "1");
  const env = { BANK: {prepare(sql) {return {bind(...args) {return {
    async first() { return db.prepare(sql).get(...args); }, async all() {return {results: db.prepare(sql).all(...args)};}
  };}};}}, SEED_REQUESTS: { async limit() {return {success: true};} } };
  const request = required => new Request("https://test/v1/seed", {method: "POST", body: JSON.stringify({profile: PROFILE,
    type: "village", requestId: "12345678-1234-1234-1234-123456789abc", excludeFamilies: [], requireStructure: required})});
  assert.equal((await serveSeed(request(true), env, () => 0)).status, 503);
  assert.equal((await serveSeed(request(false), env, () => 0)).status, 200);
  await syncStructurePages(publication([row("1"), row("2")]), execute, {writeBudget: 100});
  const response = await serveSeed(request(true), env, () => 0);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(body.structure, [32, 192]);
  assert.equal(body.seed, "1");
  assert.equal(body.entries, undefined);
  db.exec(`UPDATE bank_structures SET entries='[["2",32,192]]'`);
  assert.equal((await serveSeed(request(true), env, () => 0)).status, 503);
});
