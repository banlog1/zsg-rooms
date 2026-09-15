import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { PROFILE, TYPES } from "../src/bank-format.js";
import { readPublication } from "./bank-publication.mjs";

const exec = promisify(execFile);
const root = fileURLToPath(new URL("../../", import.meta.url));

// Prefix checks and plain INSERTs make retries fail closed rather than overwrite slots.
export async function uploadBank(publication, execute, { writeBudget, maxNewSeeds = Infinity, type: selectedType, batchSize = 50, report = () => {} }) {
  if (!Number.isSafeInteger(writeBudget) || writeBudget < 100
      || !(maxNewSeeds === Infinity || (Number.isSafeInteger(maxNewSeeds) && maxNewSeeds >= 0))) {
    throw new Error("Specify a write budget of at least 100 and a nonnegative seed limit.");
  }
  if (selectedType !== undefined && !TYPES.includes(selectedType)) throw new Error("Invalid upload type.");
  // Bound SQL/command-line size well below D1 and Windows limits.
  if (!Number.isSafeInteger(batchSize) || batchSize < 1 || batchSize > 200) throw new Error("Batch size must be between 1 and 200.");
  const uploadTypes = selectedType === undefined ? TYPES : [selectedType];
  const { manifest, byType, bankRevision: revision } = publication;
  let writes = 0;
  let reads = 0;
  let added = 0;
  const query = async sql => {
    const result = await execute(sql);
    writes += result.rowsWritten;
    reads += result.rowsRead;
    return result.rows;
  };
  // Read and validate all already-uploaded rows, without displaying them or relying
  // on a local completion marker that could be lost after a successful remote write.
  const counts = Object.fromEntries(TYPES.map(type => [type, 0]));
  const active = await query(`SELECT revision FROM bank_active WHERE profile='${PROFILE}'`);
  if (active.length && active[0].revision !== revision) throw new Error("Different bank is active; use its publication as the base.");
  for (const type of TYPES) {
    while (true) {
      const rows = await query(`SELECT slot,seed,family FROM bank_seeds WHERE revision='${revision}' AND type='${type}' AND slot>=${counts[type]} ORDER BY slot LIMIT 1000`);
      for (const row of rows) {
        const expected = byType[type][counts[type]];
        if (!expected || row.slot !== counts[type] || row.seed !== expected.seed || row.family !== expected.family) {
          throw new Error("Uploaded rows differ from the publication prefix; no slots were overwritten.");
        }
        counts[type]++;
      }
      if (rows.length < 1000) break;
    }
  }
  const visible = await query(`SELECT type,count FROM bank_types WHERE revision='${revision}'`);
  if (visible.some(row => !TYPES.includes(row.type) || row.count > counts[row.type])) {
    throw new Error("Advertised count exceeds the verified uploaded prefix.");
  }
  await query("CREATE TABLE IF NOT EXISTS bank_upload_state (revision TEXT PRIMARY KEY, plan TEXT NOT NULL)");
  const state = await query(`SELECT plan FROM bank_upload_state WHERE revision='${revision}'`);
  if (state.length && state[0].plan !== manifest.revision && !(manifest.ancestors || []).includes(state[0].plan)) {
    throw new Error("A newer or different upload plan is registered; extend that plan instead.");
  }
  if (!state.length) {
    await query(`INSERT OR IGNORE INTO bank_upload_state(revision,plan) VALUES ('${revision}','${manifest.revision}')`);
  } else if (state[0].plan !== manifest.revision) {
    await query(`UPDATE bank_upload_state SET plan='${manifest.revision}' WHERE revision='${revision}' AND plan='${state[0].plan}'`);
  }
  const claimed = await query(`SELECT plan FROM bank_upload_state WHERE revision='${revision}'`);
  if (claimed[0]?.plan !== manifest.revision) throw new Error("Another uploader registered a different plan.");
  const guard = `EXISTS (SELECT 1 FROM bank_upload_state WHERE revision='${revision}' AND plan='${manifest.revision}')`;
  const publishCount = async type => {
    if (!counts[type]) return;
    await query(`INSERT INTO bank_types(revision,type,count) SELECT '${revision}','${type}',${counts[type]} WHERE ${guard}
      ON CONFLICT(revision,type) DO UPDATE SET count=excluded.count WHERE bank_types.count<excluded.count`);
    await query(`INSERT INTO bank_active(profile,revision) SELECT '${PROFILE}','${revision}' WHERE ${guard}
      AND EXISTS (SELECT 1 FROM bank_types WHERE revision='${revision}' AND count>0)
      ON CONFLICT(profile) DO NOTHING`);
  };
  for (const type of TYPES) await publishCount(type);
  // Round-robin batches avoid making smaller profiles wait behind a large one.
  let progress = true;
  while (progress && added < maxNewSeeds) {
    progress = false;
    for (const type of uploadTypes) {
      const size = Math.min(batchSize, byType[type].length - counts[type], maxNewSeeds - added);
      if (!size || writes + size * 3 + 20 > writeBudget) continue;
      const start = counts[type];
      const values = byType[type].slice(start, start + size).map((row, i) => `(${start + i},'${row.seed}','${row.family}')`);
      const rows = await query(`WITH incoming(slot,seed,family) AS (VALUES ${values.join(",")})
        INSERT INTO bank_seeds(revision,type,slot,seed,family)
        SELECT '${revision}','${type}',slot,seed,family FROM incoming WHERE ${guard} RETURNING slot`);
      if (rows.length !== size) throw new Error("Upload plan changed; resume with the latest publication.");
      counts[type] += size;
      added += size;
      await publishCount(type);
      progress = true;
      report({ added, counts: { ...counts }, rowsWritten: writes, rowsRead: reads });
    }
  }
  return { added, counts, remaining: manifest.total - Object.values(counts).reduce((a, b) => a + b, 0),
    rowsWritten: writes, rowsRead: reads };
}

export function wranglerExecutor(config, run = exec) {
  return async sql => {
    try {
      const { stdout } = await run(process.execPath, [resolve(root, "relay/node_modules/wrangler/bin/wrangler.js"),
        "d1", "execute", "BANK", "--remote", "--config", config, "--command", sql, "--json"], {
        cwd: root, windowsHide: true, timeout: 60000, maxBuffer: 4 * 1024 * 1024,
        env: { ...process.env, WRANGLER_SEND_METRICS: "false", WRANGLER_LOG: "log",
          WRANGLER_WRITE_LOGS: "false", WRANGLER_LOG_SANITIZE: "true" }
      });
      const results = JSON.parse(stdout);
      if (!Array.isArray(results) || !results.length || results.some(result => !result.success
          || !Number.isSafeInteger(result.meta?.rows_written) || !Number.isSafeInteger(result.meta?.rows_read))) throw new Error();
      return { rows: results.flatMap(result => result.results),
        rowsWritten: results.reduce((n, result) => n + result.meta.rows_written, 0),
        rowsRead: results.reduce((n, result) => n + result.meta.rows_read, 0) };
    } catch {
      // Never forward child-process errors: their command line contains private SQL.
      throw new Error("D1 query failed or timed out. Resume the same publication after checking account usage.");
    }
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [directory, ...args] = process.argv.slice(2);
    const options = {};
    for (let i = 0; i < args.length; i += 2) {
      if (args[i] === "--type") {
        if (!TYPES.includes(args[i + 1])) throw new Error();
        options["--type"] = args[i + 1];
        continue;
      }
      if (!["--write-budget", "--max-new-seeds", "--batch-size"].includes(args[i]) || !/^\d+$/.test(args[i + 1] || "")) throw new Error();
      options[args[i]] = Number(args[i + 1]);
    }
    const config = resolve(root, "seed-service/wrangler.production.jsonc");
    const target = JSON.parse(await readFile(config, "utf8"));
    if (target.account_id !== "3cee401ab7a7bc6f8d9be942204fe527"
        || target.d1_databases?.find(db => db.binding === "BANK")?.database_id !== "ade095f8-532f-4b0a-9220-b3b2410d3d40") throw new Error();
    const result = await uploadBank(await readPublication(directory), wranglerExecutor(config), {
      writeBudget: options["--write-budget"], maxNewSeeds: options["--max-new-seeds"], type: options["--type"], batchSize: options["--batch-size"],
      report: progress => console.log(JSON.stringify(progress))
    });
    console.log(JSON.stringify(result));
  } catch {
    console.error("Upload stopped. Check the publication, account usage and pinned configuration; resume with the latest plan. No seed data printed.");
    process.exitCode = 1;
  }
}
