import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, writeFile, readFile, access, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, dirname, resolve, basename } from "node:path";
import { publishBank } from "../scripts/publish-bank.mjs";
import { PROFILE, isSeed, validateRecord } from "../src/bank-format.js";

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
