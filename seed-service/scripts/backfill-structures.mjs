import { createReadStream } from "node:fs";
import { mkdir, writeFile, rename } from "node:fs/promises";
import { createInterface } from "node:readline";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { randomUUID } from "node:crypto";
import { readPublication, digest } from "./bank-publication.mjs";
import { PROFILE, TYPES, isStructure } from "../src/bank-format.js";

// Enrich existing slots only. Historical acceptance rules must not be rerun here.
export async function recoverStructures(publication, inputs) {
  const expected = new Map(publication.rows.map(row => [`${row.type}:${row.seed}`, row]));
  const found = new Map();
  for (const input of inputs) {
    const stream = createReadStream(input, { encoding: "utf8" });
    const lines = createInterface({ input: stream, crlfDelay: Infinity });
    try {
      for await (const line of lines) {
        if (!line.trim()) continue;
        let source;
        try { source = JSON.parse(line); } catch { throw new Error("Invalid source record JSON."); }
        const key = `${source.type}:${source.seed}`;
        const row = expected.get(key);
        if (!row) continue;
        if (source.profile !== PROFILE || source.status !== "MODEL_ACCEPTED"
            || source.family !== row.family || !isStructure(source.structure)) {
          throw new Error("Invalid matching source metadata.");
        }
        const previous = found.get(key) || row.structure;
        if (previous && (previous[0] !== source.structure[0] || previous[1] !== source.structure[1])) {
          throw new Error("Conflicting structure coordinates; no publication was written.");
        }
        found.set(key, source.structure);
      }
    } finally { lines.close(); stream.destroy(); }
  }
  const rows = publication.rows.map(row => ({ ...row, structure: found.get(`${row.type}:${row.seed}`) || row.structure }));
  const missing = Object.fromEntries(TYPES.map(type => [type, rows.filter(row => row.type === type && !row.structure).length]));
  return { rows, missing };
}

export async function backfillStructures(base, output, inputs) {
  const publication = await readPublication(base);
  const { rows, missing } = await recoverStructures(publication, inputs);
  if (Object.values(missing).some(n => n)) throw new Error(`Source coordinates missing: ${JSON.stringify(missing)}`);
  const content = rows.map(row => JSON.stringify(row)).join("\n") + "\n";
  const manifest = { ...publication.manifest, revision: digest(content), bankRevision: publication.bankRevision,
    ancestors: [...new Set([...(publication.manifest.ancestors || []), publication.manifest.revision])],
    structureMetadataVersion: 1 };
  // An exclusive directory prevents accidental replacement of a published snapshot.
  await mkdir(output);
  const temporary = resolve(output, `bank.${randomUUID()}.tmp`);
  await writeFile(temporary, content, { flag: "wx" });
  await rename(temporary, resolve(output, "bank.jsonl"));
  await writeFile(resolve(output, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n", { flag: "wx" });
  return manifest;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [base, output, ...inputs] = process.argv.slice(2);
    if (!base || !output || !inputs.length) throw new Error("Usage: backfill-structures.mjs BASE OUTPUT SOURCE_BANK_JSONL ...");
    console.log(JSON.stringify(await backfillStructures(base, output, inputs)));
  } catch (error) {
    console.error(error.code || error instanceof SyntaxError
      ? "Backfill failed; check local paths and record format. No source files were changed." : error.message);
    process.exitCode = 1;
  }
}
