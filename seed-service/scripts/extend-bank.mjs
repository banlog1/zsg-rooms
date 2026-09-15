import { createReadStream } from "node:fs";
import { mkdir, writeFile, rename, rm, lstat } from "node:fs/promises";
import { createInterface } from "node:readline";
import { randomUUID } from "node:crypto";
import { resolve, dirname, basename } from "node:path";
import { pathToFileURL } from "node:url";
import { TYPES, validateRecord } from "../src/bank-format.js";
import { digest, readPublication, validateRows } from "./bank-publication.mjs";

export async function extendBank(base, output, inputs) {
  if (!inputs.length) throw new Error("Supply completed bank snapshots to append.");
  const previous = await readPublication(base);
  const known = new Map(previous.rows.map(row => [row.seed, row]));
  const additions = [];
  for (const input of inputs) {
    const stream = createReadStream(input, { encoding: "utf8" });
    const lines = createInterface({ input: stream, crlfDelay: Infinity });
    try {
      for await (const line of lines) {
        if (!line.trim()) continue;
        const row = validateRecord(JSON.parse(line));
        const existing = known.get(row.seed);
        if (existing) {
          if (existing.type !== row.type || existing.family !== row.family) {
            throw new Error("Snapshot conflicts with an existing seed assignment.");
          }
          continue;
        }
        known.set(row.seed, row);
        additions.push(row);
      }
    } finally { lines.close(); stream.destroy(); }
  }
  const compare = (a, b) => a < b ? -1 : a > b ? 1 : 0;
  additions.sort((a, b) => compare(a.type, b.type) || compare(a.seed, b.seed));
  // Existing order assigns permanent per-type slots, including uploads still pending.
  const rows = [...previous.rows, ...additions];
  validateRows(rows);
  const content = rows.map(row => JSON.stringify(row)).join("\n") + "\n";
  const manifest = { schemaVersion: previous.manifest.schemaVersion, profile: previous.manifest.profile,
    revision: digest(content), bankRevision: previous.bankRevision,
    ancestors: [...new Set([...(previous.manifest.ancestors || []), previous.manifest.revision])],
    counts: Object.fromEntries(TYPES.map(type => [type, rows.filter(row => row.type === type).length])),
    total: rows.length, added: additions.length };
  const destination = resolve(output);
  try { await lstat(destination); throw new Error("Publication directory already exists."); }
  catch (error) { if (error.code !== "ENOENT") throw error; }
  await mkdir(dirname(destination), { recursive: true });
  const staging = `${destination}.${randomUUID()}.tmp`;
  await mkdir(staging);
  try {
    await writeFile(resolve(staging, "bank.jsonl"), content, { flag: "wx" });
    await writeFile(resolve(staging, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n", { flag: "wx" });
    await rename(staging, destination);
  } catch (error) {
    if (dirname(staging) !== dirname(destination) || !basename(staging).startsWith(basename(destination) + ".")
        || !staging.endsWith(".tmp")) throw new Error("Unsafe publication cleanup path.");
    await rm(staging, { recursive: true, force: true });
    throw error;
  }
  return manifest;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [base, output, ...inputs] = process.argv.slice(2);
    if (!base || !output) throw new Error("Supply BASE_PUBLICATION OUTPUT_DIRECTORY BANK_JSONL [...].");
    console.log(JSON.stringify(await extendBank(base, output, inputs)));
  } catch {
    console.error("Bank extension failed; check snapshots, base publication and output path. No seed data printed.");
    process.exitCode = 1;
  }
}
