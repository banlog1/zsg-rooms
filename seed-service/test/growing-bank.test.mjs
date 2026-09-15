import { test } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { readFile, writeFile, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, dirname, resolve, basename } from "node:path";
import { publishBank } from "../scripts/publish-bank.mjs";
import { extendBank } from "../scripts/extend-bank.mjs";
import { readPublication } from "../scripts/bank-publication.mjs";
import { uploadBank, wranglerExecutor } from "../scripts/upload-bank.mjs";
import { serveSeed } from "../src/index.js";
import { PROFILE } from "../src/bank-format.js";

const family = seed => String(BigInt(seed) & ((1n << 48n) - 1n));
const record = (seed, type = "shipwreck") => ({ seed, type, family: family(seed), profile: PROFILE,
  status: "MODEL_ACCEPTED", structure: [0, 0], entry: [0, 0], spawn: [0, 0], bastion: [0, 0], fortress: [0, 0] });

async function fixture(t, rows) {
  const directory = await mkdtemp(join(tmpdir(), "zsg-bank-growth-"));
  const db = new DatabaseSync(":memory:");
  t.after(async () => {
    db.close();
    assert.equal(dirname(resolve(directory)), resolve(tmpdir()));
    assert.ok(basename(directory).startsWith("zsg-bank-growth-"));
    await rm(directory, { recursive: true, force: true });
  });
  db.exec(await readFile(new URL("../schema.sql", import.meta.url), "utf8"));
  const input = join(directory, "input.jsonl");
  await writeFile(input, rows.map(row => JSON.stringify(row)).join("\n"));
  const output = join(directory, "initial");
  await publishBank([input], output);
  const publication = await readPublication(output);
  const execute = async sql => {
    const before = db.prepare("SELECT total_changes() AS n").get().n;
    const result = db.prepare(sql).all();
    const changes = db.prepare("SELECT total_changes() AS n").get().n - before;
    return { rows: result, rowsRead: result.length,
      rowsWritten: changes * (sql.includes("INSERT INTO bank_seeds") ? 3 : 1) };
  };
  const env = { BANK: { prepare(sql) { return { bind(...args) { return {
    async first() { return db.prepare(sql).get(...args) || null; },
    async all() { return { results: db.prepare(sql).all(...args) }; }
  }; } }; } }, SEED_REQUESTS: { async limit() { return { success: true }; } } };
  const fetch = (type, slot = 0, excludeFamilies = []) => serveSeed(new Request("https://test/v1/seed", {
    method: "POST", body: JSON.stringify({ profile: PROFILE, type,
      requestId: "12345678-1234-1234-1234-123456789abc", excludeFamilies })
  }), env, () => slot);
  return { directory, db, input, output, publication, execute, fetch };
}

test("a partial bank serves existing seeds; other types become available independently", async t => {
  const f = await fixture(t, [record("1"), record("2"), record("3", "temple"), record("4", "village")]);
  const revision = f.publication.bankRevision;
  f.db.prepare("INSERT INTO bank_seeds VALUES (?,?,?,?,?)").run(revision, "shipwreck", 0, "1", "1");
  const activated = await uploadBank(f.publication, f.execute, { writeBudget: 100, maxNewSeeds: 0 });
  assert.equal(activated.added, 0);
  assert.equal(activated.remaining, 3);
  assert.equal((await f.fetch("shipwreck")).status, 200);
  assert.equal((await f.fetch("temple")).status, 503);
  assert.equal((await f.fetch("shipwreck", 0, ["1"])).status, 409);
  let samples = 0;
  const execute = async sql => {
    if (sql.includes("INSERT INTO bank_seeds")) {
      assert.equal((await f.fetch("shipwreck")).status, 200);
      samples++;
    }
    return f.execute(sql);
  };
  const done = await uploadBank(f.publication, execute, { writeBudget: 1000 });
  assert.equal(done.added, 3);
  assert.equal(done.remaining, 0);
  assert.equal(samples, 3);
  assert.equal((await f.fetch("shipwreck", 1)).status, 200);
  assert.equal((await f.fetch("temple")).status, 200);
  assert.equal((await f.fetch("village")).status, 200);
  const repeated = await uploadBank(f.publication, f.execute, { writeBudget: 100 });
  assert.equal(repeated.added, 0);
  assert.equal(repeated.rowsWritten, 0);
});

test("daily extension retains every slot and inserts only genuinely new seeds", async t => {
  const f = await fixture(t, [record("9"), record("8", "temple")]);
  await uploadBank(f.publication, f.execute, { writeBudget: 100 });
  const snapshot = join(f.directory, "tomorrow.jsonl");
  const rows = [record("1"), record("9"), record("8", "temple"), record("2", "temple")];
  await writeFile(snapshot, rows.map(row => JSON.stringify(row)).join("\n"));
  const next = join(f.directory, "next");
  const manifest = await extendBank(f.output, next, [snapshot, snapshot]);
  assert.equal(manifest.added, 2);
  const publication = await readPublication(next);
  assert.equal(publication.bankRevision, f.publication.bankRevision);
  assert.deepEqual(publication.byType.shipwreck.map(row => row.seed), ["9", "1"]);
  assert.deepEqual(publication.byType.temple.map(row => row.seed), ["8", "2"]);
  const result = await uploadBank(publication, f.execute, { writeBudget: 100 });
  assert.equal(result.added, 2);
  assert.equal(f.db.prepare("SELECT count(*) AS n FROM bank_seeds").get().n, 4);
  assert.equal((await (await f.fetch("shipwreck", 0)).json()).seed, "9");
  assert.equal((await (await f.fetch("shipwreck", 1)).json()).seed, "1");
  await assert.rejects(uploadBank(f.publication, f.execute, { writeBudget: 100 }));
});

test("a stopped upload resumes from remote rows without duplicating seeds", async t => {
  const f = await fixture(t, Array.from({ length: 120 }, (_, i) => record(String(i + 1))));
  let fail = true;
  const execute = async sql => {
    if (fail && sql.includes("INSERT INTO bank_types") && sql.includes(",50 WHERE")) {
      fail = false;
      throw new Error("simulated disconnect after seed insert");
    }
    return f.execute(sql);
  };
  await assert.rejects(uploadBank(f.publication, execute, { writeBudget: 1000 }));
  assert.equal(f.db.prepare("SELECT count(*) AS n FROM bank_seeds").get().n, 50);
  const result = await uploadBank(f.publication, f.execute, { writeBudget: 1000 });
  assert.equal(result.added, 70);
  assert.equal(result.counts.shipwreck, 120);
  assert.equal((await f.fetch("shipwreck", 119)).status, 200);
});

test("tomorrow's plan can extend today's unfinished upload without skipping its pending seeds", async t => {
  const f = await fixture(t, Array.from({ length: 120 }, (_, i) => record(String(i + 1))));
  await uploadBank(f.publication, f.execute, { writeBudget: 190 });
  const snapshot = join(f.directory, "new.jsonl");
  await writeFile(snapshot, JSON.stringify(record("1000")));
  const next = join(f.directory, "next");
  await extendBank(f.output, next, [snapshot]);
  const extended = await readPublication(next);
  assert.deepEqual(extended.byType.shipwreck.slice(0, 120), f.publication.byType.shipwreck);
  const result = await uploadBank(extended, f.execute, { writeBudget: 1000 });
  assert.equal(result.added, 71);
  assert.equal(result.remaining, 0);
  assert.equal((await (await f.fetch("shipwreck", 120)).json()).seed, "1000");
});

test("extension enforces family caps across the existing bank and new snapshots", async t => {
  const f = await fixture(t, [record("1", "temple"), record("281474976710657", "temple")]);
  const snapshot = join(f.directory, "extra-sister.jsonl");
  await writeFile(snapshot, JSON.stringify(record("562949953421313", "temple")));
  await assert.rejects(extendBank(f.output, join(f.directory, "next"), [snapshot]), /family cap/);
});

test("budget-limited uploads keep the active prefix usable", async t => {
  const f = await fixture(t, Array.from({ length: 120 }, (_, i) => record(String(i + 1))));
  const first = await uploadBank(f.publication, f.execute, { writeBudget: 190 });
  assert.equal(first.added, 50);
  assert.ok(first.rowsWritten <= 190);
  assert.equal(first.remaining, 70);
  assert.equal((await f.fetch("shipwreck", 49)).status, 200);
  const next = await uploadBank(f.publication, f.execute, { writeBudget: 1000 });
  assert.equal(next.added, 70);
  assert.equal(next.remaining, 0);
});

test("a type-selected upload adds only that type and respects its seed limit", async t => {
  const f = await fixture(t, [record("1", "temple"), record("2", "temple"), record("3", "village"), record("4")]);
  await assert.rejects(uploadBank(f.publication, f.execute, { writeBudget: 100, type: "unknown" }), /upload type/);
  const result = await uploadBank(f.publication, f.execute, { writeBudget: 100, maxNewSeeds: 1, type: "temple" });
  assert.equal(result.added, 1);
  assert.deepEqual(result.counts, { temple: 1, village: 0, shipwreck: 0 });
  assert.equal((await f.fetch("temple")).status, 200);
  assert.equal((await f.fetch("village")).status, 503);
  assert.equal((await f.fetch("shipwreck")).status, 503);
  const resumed = await uploadBank(f.publication, f.execute, { writeBudget: 100, type: "temple" });
  assert.equal(resumed.added, 1);
  assert.deepEqual(resumed.counts, { temple: 2, village: 0, shipwreck: 0 });
});

test("larger bounded batches retain the write limit and resume with a different batch size", async t => {
  const f = await fixture(t, Array.from({ length: 450 }, (_, i) => record(String(9223372036854775807n - BigInt(i)), "temple")));
  for (const batchSize of [0, 201, 1.5]) {
    await assert.rejects(uploadBank(f.publication, f.execute, { writeBudget: 1000, batchSize }), /Batch size/);
  }
  let largest = 0;
  const execute = async sql => {
    const result = await f.execute(sql);
    if (sql.includes("INSERT INTO bank_seeds")) {
      assert.ok(sql.length < 15000);
      largest = Math.max(largest, result.rows.length);
    }
    return result;
  };
  const first = await uploadBank(f.publication, execute, { writeBudget: 700, batchSize: 200, type: "temple" });
  assert.equal(first.added, 200);
  assert.equal(largest, 200);
  assert.ok(first.rowsWritten <= 700);
  assert.equal((await f.fetch("temple", 199)).status, 200);
  const next = await uploadBank(f.publication, execute, { writeBudget: 1000, batchSize: 100, type: "temple" });
  assert.equal(next.added, 250);
  assert.equal(next.remaining, 0);
  assert.equal((await f.fetch("temple", 449)).status, 200);
});

test("holes, conflicting rows and out-of-order plans fail closed", async t => {
  const f = await fixture(t, [record("1"), record("2")]);
  f.db.prepare("INSERT INTO bank_seeds VALUES (?,?,?,?,?)").run(f.publication.bankRevision, "shipwreck", 1, "2", "2");
  await assert.rejects(uploadBank(f.publication, f.execute, { writeBudget: 100 }));
  assert.equal(f.db.prepare("SELECT count(*) AS n FROM bank_active").get().n, 0);
  f.db.exec("DELETE FROM bank_seeds");
  f.db.prepare("INSERT INTO bank_seeds VALUES (?,?,?,?,?)").run(f.publication.bankRevision, "shipwreck", 0, "3", "3");
  await assert.rejects(uploadBank(f.publication, f.execute, { writeBudget: 100 }));
});

test("newer upload plan ownership prevents an older uploader from appending", async t => {
  const f = await fixture(t, [record("1"), record("2")]);
  let changed = false;
  const execute = async sql => {
    if (!changed && sql.includes("INSERT INTO bank_seeds")) {
      changed = true;
      f.db.prepare("UPDATE bank_upload_state SET plan=?").run("b".repeat(64));
    }
    return f.execute(sql);
  };
  await assert.rejects(uploadBank(f.publication, execute, { writeBudget: 100 }));
  assert.equal(f.db.prepare("SELECT count(*) AS n FROM bank_seeds").get().n, 0);
});

test("extension rejects conflicting filters and corrupt base manifests", async t => {
  const f = await fixture(t, [record("1")]);
  const snapshot = join(f.directory, "bad.jsonl");
  await writeFile(snapshot, JSON.stringify(record("1", "temple")));
  await assert.rejects(extendBank(f.output, join(f.directory, "next"), [snapshot]));
  await writeFile(join(f.output, "bank.jsonl"), "{}\n");
  await assert.rejects(readPublication(f.output));
});

test("Wrangler wrapper captures JSON, disables disk logs and never exposes child errors", async () => {
  const execute = wranglerExecutor("test-config", async (command, args, options) => {
    assert.ok(args.includes("--command"));
    assert.ok(!args.includes("--file"));
    assert.equal(options.env.WRANGLER_WRITE_LOGS, "false");
    assert.equal(options.env.WRANGLER_LOG_SANITIZE, "true");
    assert.equal(options.env.WRANGLER_LOG, "log");
    return { stdout: JSON.stringify([{ success: true, results: [{ count: 10 }], meta: { rows_written: 0, rows_read: 10 } }]) };
  });
  assert.deepEqual(await execute("SELECT count(*) AS count FROM bank_seeds"), {
    rows: [{ count: 10 }], rowsWritten: 0, rowsRead: 10
  });
  const fail = wranglerExecutor("test-config", async () => { throw new Error("private-sql-and-seed"); });
  await assert.rejects(fail("private-sql-and-seed"), error => !error.message.includes("private-sql-and-seed"));
});
