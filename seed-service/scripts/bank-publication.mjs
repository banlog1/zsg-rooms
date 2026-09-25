import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { resolve } from "node:path";
import { PROFILE, SCHEMA_VERSION, TYPES, isSeed } from "../src/bank-format.js";

export const digest = text => createHash("sha256").update(text).digest("hex");
export const isRevision = value => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);

export function validateRows(rows) {
  const seeds = new Set();
  const families = new Map();
  for (const row of rows) {
    if (row.profile !== PROFILE || !TYPES.includes(row.type) || !isSeed(row.seed)
        || row.family !== String(BigInt(row.seed) & ((1n << 48n) - 1n))) {
      throw new Error("Invalid publication record.");
    }
    if (seeds.has(row.seed)) throw new Error("Duplicate seed in publication.");
    seeds.add(row.seed);
    const key = `${row.type}:${row.family}`;
    const count = (families.get(key) || 0) + 1;
    if (count > (["shipwreck", "ruined_portal"].includes(row.type) ? 4 : 2)) throw new Error("Publication exceeds the profile family cap.");
    families.set(key, count);
  }
}

export async function readPublication(directory) {
  const content = await readFile(resolve(directory, "bank.jsonl"), "utf8");
  const manifest = JSON.parse(await readFile(resolve(directory, "manifest.json"), "utf8"));
  if (manifest.schemaVersion !== SCHEMA_VERSION || manifest.profile !== PROFILE
      || !isRevision(manifest.revision) || digest(content) !== manifest.revision
      || (manifest.bankRevision !== undefined && !isRevision(manifest.bankRevision))
      || (manifest.ancestors !== undefined && (!Array.isArray(manifest.ancestors)
        || !manifest.ancestors.every(isRevision)))) throw new Error("Invalid publication manifest or digest.");
  const rows = content.trim().split("\n").map(line => JSON.parse(line));
  validateRows(rows);
  const byType = Object.fromEntries(TYPES.map(type => [type, rows.filter(row => row.type === type)]));
  // Older snapshots predate these types. Their missing counts mean zero;
  // their original bytes, digests and permanent slots remain unchanged.
  const recordedCount = type => ["buried_treasure", "ruined_portal"].includes(type) && manifest.counts?.[type] === undefined
    ? 0 : manifest.counts?.[type];
  if (manifest.total !== rows.length || TYPES.some(type => recordedCount(type) !== byType[type].length)) {
    throw new Error("Publication counts do not match its records.");
  }
  return { manifest, rows, byType, bankRevision: manifest.bankRevision || manifest.revision };
}
