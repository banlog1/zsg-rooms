import { createReadStream } from "node:fs";
import { mkdir, writeFile, rename, rm, readFile, lstat } from "node:fs/promises";
import { createInterface } from "node:readline";
import { createHash, randomUUID } from "node:crypto";
import { resolve, dirname, basename } from "node:path";
import { pathToFileURL } from "node:url";
import { PROFILE, SCHEMA_VERSION, TYPES, validateRecord } from "../src/bank-format.js";

// Only consolidated, committed bank snapshots belong here, never in-progress attempts.
export async function publishBank(inputs, output, rowsPerPart = 25000) {
  if (!Number.isSafeInteger(rowsPerPart) || rowsPerPart < 50 || rowsPerPart % 50 !== 0) {
    throw new Error("Import part size must be a positive multiple of 50.");
  }
  if (!inputs.length) throw new Error("Supply at least one completed bank.jsonl snapshot.");
  const rows = new Map();
  const families = new Map();
  for (const input of inputs) {
    const stream = createReadStream(input, { encoding: "utf8" });
    const lines = createInterface({ input: stream, crlfDelay: Infinity });
    try {
      for await (const line of lines) {
        if (!line.trim()) continue;
        let value;
        try { value = JSON.parse(line); } catch { throw new Error("Invalid bank JSON record."); }
        const record = validateRecord(value);
        if (rows.has(record.seed)) throw new Error("Duplicate seed in publication inputs.");
        const key = `${record.type}:${record.family}`;
        const count = (families.get(key) || 0) + 1;
        if (count > (record.type === "shipwreck" ? 4 : 2)) throw new Error("Publication exceeds the profile family cap.");
        families.set(key, count);
        rows.set(record.seed, record);
      }
    } finally { lines.close(); stream.destroy(); }
  }
  if (!rows.size) throw new Error("Cannot publish an empty bank.");
  const compare = (a, b) => a < b ? -1 : a > b ? 1 : 0;
  const records = [...rows.values()].sort((a, b) => compare(a.type, b.type) || compare(a.seed, b.seed));
  const content = records.map(row => JSON.stringify(row)).join("\n") + "\n";
  const counts = Object.fromEntries(TYPES.map(type => [type, records.filter(row => row.type === type).length]));
  const manifest = {
    schemaVersion: SCHEMA_VERSION, profile: PROFILE,
    revision: createHash("sha256").update(content).digest("hex"), counts,
    total: records.length
  };
  const destination = resolve(output);
  try { await lstat(destination); throw new Error("Publication directory already exists."); }
  catch (error) { if (error.code !== "ENOENT") throw error; }
  await mkdir(dirname(destination), { recursive: true });
  const staging = `${destination}.${randomUUID()}.tmp`;
  await mkdir(staging);
  try {
    await writeFile(resolve(staging, "bank.jsonl"), content, { flag: "wx" });
    await writeFile(resolve(staging, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n", { flag: "wx" });
    const sql = [await readFile(new URL("../schema.sql", import.meta.url), "utf8")];
    const schema = sql[0];
    let part = [schema];
    const parts = [];
    const revision = manifest.revision;
    const slots = Object.fromEntries(TYPES.map(type => [type, 0]));
    for (let i = 0; i < records.length; i += 50) {
      const values = records.slice(i, i + 50).map(row =>
        `('${revision}','${row.type}',${slots[row.type]++},'${row.seed}','${row.family}')`);
      const statement = "INSERT OR IGNORE INTO bank_seeds(revision,type,slot,seed,family) VALUES " + values.join(",") + ";";
      sql.push(statement);
      part.push(statement);
      if ((i + values.length) % rowsPerPart === 0 || i + values.length === records.length) {
        const name = `import-part-${String(parts.length + 1).padStart(3, "0")}.sql`;
        await writeFile(resolve(staging, name), part.join("\n") + "\n", { flag: "wx" });
        parts.push({ file: name, rows: (i % rowsPerPart) + values.length });
        part = [schema];
      }
    }
    const activation = [];
    for (const type of TYPES) {
      activation.push(`INSERT OR IGNORE INTO bank_types(revision,type,count) VALUES ('${revision}','${type}',${counts[type]});`);
    }
    // Publish only when the complete new revision is present. Previous revisions remain intact.
    activation.push(`INSERT INTO bank_active(profile,revision) SELECT '${PROFILE}','${revision}'
      WHERE (SELECT COUNT(*) FROM bank_seeds WHERE revision='${revision}')=${records.length}
      ON CONFLICT(profile) DO UPDATE SET revision=excluded.revision;`);
    sql.push(...activation);
    await writeFile(resolve(staging, "activate.sql"), activation.join("\n") + "\n", { flag: "wx" });
    await writeFile(resolve(staging, "import-plan.json"), JSON.stringify({ revision, parts,
      activation: "activate.sql", estimatedRowsWritten: records.length * 3 + 8 }, null, 2) + "\n", { flag: "wx" });
    await writeFile(resolve(staging, "import.sql"), sql.join("\n") + "\n", { flag: "wx" });
    // Never overwrite a published revision, including one another publisher just created.
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
  const [output, ...inputs] = process.argv.slice(2);
  try {
    if (!output) throw new Error("Usage: publish-bank.mjs OUTPUT_DIRECTORY BANK_JSONL [BANK_JSONL ...]");
    const manifest = await publishBank(inputs, output);
    console.log(JSON.stringify(manifest));
  } catch (error) {
    // Filesystem errors can contain private paths. Never print input lines or seed values.
    console.error(error.code ? "Bank publication failed; check input/output paths and permissions." : error.message);
    process.exitCode = 1;
  }
}
