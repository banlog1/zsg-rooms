import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, writeFile, readFile, access, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, dirname, resolve, basename } from "node:path";
import { publishBank } from "../scripts/publish-bank.mjs";
import { PROFILE, isSeed, validateRecord } from "../src/bank-format.js";
import { DatabaseSync } from "node:sqlite";

function record(seed, type = "temple") {
  return { seed, type, family: String(BigInt(seed) & ((1n << 48n) - 1n)), profile: PROFILE,
    status: "MODEL_ACCEPTED", structure: [16, 32], entry: [24, 40], spawn: [8, 8], bastion: [16, 16], fortress: [80, 80] };
}

test("signed Java long seeds never pass through floating point", () => {
  for (const value of ["9223372036854775807", "-9223372036854775808", "9007199254740993"]) {
    assert.ok(isSeed(value));
    assert.equal(validateRecord(record(value)).seed, value);
  }
  for (const value of [0, 123, "0", "-0", "01", "+1", "1e3", "9223372036854775808", "-9223372036854775809"]) {
    assert.equal(isSeed(value), false);
  }
});

test("profile, family and coordinates are validated without exposing seeds", () => {
  for (const change of [{ profile: "zsg-model-only-v4" }, { family: "0" }, { type: "unknown" }, { entry: [0, 0, 0] }]) {
    assert.throws(() => validateRecord({ ...record("9007199254740993"), ...change }), error => !error.message.includes("9007199254740993"));
  }
});

test("buried treasure requires the modeled regular-mapless rule", () => {
  const row = record("123", "buried_treasure");
  for (const rule of [undefined, "mapless-op-v1", "unknown"]) {
    assert.throws(() => validateRecord({ ...row, buriedTreasureRule: rule }), /acceptance rule/);
  }
  assert.equal(validateRecord({ ...row, buriedTreasureRule: "mapless-regular-v1" }).type, "buried_treasure");
});

test("ruined portals require v3 tools and usable ignition", () => {
  const row = { ...record("123", "ruined_portal"), ruinedPortalRule: "frame-completable-v3",
    goldenAxes: 1, goldenPickaxes: 0, flintAndSteel: 1, fireCharges: 0, flint: 0, ironNuggets: 0 };
  assert.equal(validateRecord(row).type, "ruined_portal");
  assert.equal(validateRecord({ ...row, goldenAxes: 0, goldenPickaxes: 1 }).type, "ruined_portal");
  assert.equal(validateRecord({ ...row, flintAndSteel: 0, fireCharges: 5 }).type, "ruined_portal");
  assert.equal(validateRecord({ ...row, flintAndSteel: 0, flint: 1, ironNuggets: 9 }).type, "ruined_portal");
  for (const change of [{ ruinedPortalRule: "frame-completable-v2" }, { goldenAxes: 0 },
    { goldenAxes: "1" }, { goldenPickaxes: -1 }, { goldenAxes: 9 },
    { flintAndSteel: 0, fireCharges: 4 }, { flintAndSteel: 0, flint: 1, ironNuggets: 8 }]) {
    assert.throws(() => validateRecord({ ...row, ...change }), /acceptance rule/);
  }
});

test("publication is deterministic, refuses duplicates and leaves existing output alone", async () => {
  const directory = await mkdtemp(join(tmpdir(), "zsg-bank-publish-"));
  try {
    const first = join(directory, "first.jsonl");
    const second = join(directory, "second.jsonl");
    const rows = [record("9007199254740993"), record("-9223372036854775808", "village")];
    await writeFile(first, rows.map(row => JSON.stringify(row)).join("\n"));
    await writeFile(second, [...rows].reverse().map(row => JSON.stringify(row)).join("\n"));
    const out = join(directory, "published");
    const a = await publishBank([first], out);
    const b = await publishBank([second], join(directory, "reversed"));
    assert.deepEqual(a, b);
    assert.equal(a.total, 2);
    assert.equal(a.counts.temple, 1);
    const saved = await readFile(join(out, "bank.jsonl"), "utf8");
    await assert.rejects(publishBank([first], out));
    assert.equal(await readFile(join(out, "bank.jsonl"), "utf8"), saved);
    const duplicate = join(directory, "duplicate");
    await assert.rejects(publishBank([first, second], duplicate), /Duplicate/);
    await assert.rejects(access(duplicate));
    assert.ok(saved.includes("9007199254740993"));
  } finally {
    assert.equal(dirname(resolve(directory)), resolve(tmpdir()));
    assert.ok(basename(directory).startsWith("zsg-bank-publish-"));
    await rm(directory, { recursive: true, force: true });
  }
});

test("split imports preserve dense slots and cannot activate an incomplete revision", async () => {
  const directory = await mkdtemp(join(tmpdir(), "zsg-bank-publish-"));
  const db = new DatabaseSync(":memory:");
  try {
    const input = join(directory, "bank.jsonl");
    const rows = Array.from({ length: 101 }, (_, i) => ({
      ...record(String(i + 1), ["temple", "village", "shipwreck", "buried_treasure", "ruined_portal"][i % 5]),
      buriedTreasureRule: "mapless-regular-v1", ruinedPortalRule: "frame-completable-v3",
      goldenAxes: 1, goldenPickaxes: 0, flintAndSteel: 1, fireCharges: 0, flint: 0, ironNuggets: 0
    }));
    await writeFile(input, rows.map(row => JSON.stringify(row)).join("\n"));
    const output = join(directory, "published");
    const manifest = await publishBank([input], output, 50);
    const plan = JSON.parse(await readFile(join(output, "import-plan.json"), "utf8"));
    assert.deepEqual(plan.parts.map(part => part.rows), [50, 50, 1]);
    const activate = await readFile(join(output, plan.activation), "utf8");
    for (const [index, part] of plan.parts.entries()) {
      const sql = await readFile(join(output, part.file), "utf8");
      db.exec(sql);
      db.exec(sql);
      assert.equal(db.prepare("SELECT count(*) AS n FROM bank_active").get().n, 0);
      if (index < plan.parts.length - 1) {
        db.exec(activate);
        assert.equal(db.prepare("SELECT count(*) AS n FROM bank_active").get().n, 0);
      }
    }
    db.exec(activate);
    assert.equal(db.prepare("SELECT revision FROM bank_active").get().revision, manifest.revision);
    for (const [type, count] of Object.entries(manifest.counts)) {
      const actual = db.prepare("SELECT count(*) AS n, min(slot) AS first, max(slot) AS last FROM bank_seeds WHERE type=?").get(type);
      assert.equal(actual.n, count);
      assert.equal(actual.first, 0);
      assert.equal(actual.last, count - 1);
    }
    assert.equal(db.prepare("SELECT count(*) AS n FROM bank_seeds").get().n, 101);
  } finally {
    db.close();
    assert.equal(dirname(resolve(directory)), resolve(tmpdir()));
    assert.ok(basename(directory).startsWith("zsg-bank-publish-"));
    await rm(directory, { recursive: true, force: true });
  }
});
