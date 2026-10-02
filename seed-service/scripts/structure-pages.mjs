import { TYPES, isStructure } from "../src/bank-format.js";
import { STRUCTURE_PAGE_SIZE, STRUCTURE_SCHEMA, parseStructurePage } from "../src/structure-pages.js";

export function structurePages(publication) {
  const pages = [];
  for (const type of TYPES) {
    const rows = publication.byType[type];
    for (let start = 0; start < rows.length; start += STRUCTURE_PAGE_SIZE) {
      const batch = rows.slice(start, start + STRUCTURE_PAGE_SIZE);
      if (batch.some(row => !isStructure(row.structure))) throw new Error("Backfill this publication before uploading more seeds.");
      pages.push({ type, page: start / STRUCTURE_PAGE_SIZE, entries: JSON.stringify(batch.map(row => [row.seed, ...row.structure])) });
    }
  }
  return pages;
}

// Check every existing page before making any changes; never replace an old slot's target.
export async function syncStructurePages(publication, execute, { writeBudget, guard = "1" }) {
  const expected = structurePages(publication);
  let writes = 0;
  const query = async sql => {
    const result = await execute(sql);
    writes += result.rowsWritten;
    return result.rows;
  };
  await query(STRUCTURE_SCHEMA);
  const revision = publication.bankRevision;
  const saved = await query(`SELECT type,page,entries FROM bank_structures WHERE revision='${revision}'`);
  const pending = new Map(expected.map(row => [`${row.type}:${row.page}`, row]));
  for (const row of saved) {
    const key = `${row.type}:${row.page}`;
    const target = pending.get(key);
    if (!target) throw new Error("Structure pages belong to a newer publication.");
    const before = parseStructurePage(row.entries);
    const after = parseStructurePage(target.entries);
    if (before.length > after.length || before.some((entry, i) => JSON.stringify(entry) !== JSON.stringify(after[i]))) {
      throw new Error("Structure page conflict; existing coordinates were not overwritten.");
    }
    if (before.length === after.length) pending.delete(key);
  }
  if (writes + pending.size * 2 + 10 > writeBudget) throw new Error("Insufficient write budget for structure metadata; retry with a larger budget.");
  const changes = [...pending.values()];
  for (let i = 0; i < changes.length; i += 2) {
    const batch = changes.slice(i, i + 2);
    const values = batch.map(row => `('${row.type}',${row.page},'${row.entries}')`);
    const written = await query(`WITH incoming(type,page,entries) AS (VALUES ${values.join(",")})
      INSERT INTO bank_structures(revision,type,page,entries)
      SELECT '${revision}',type,page,entries FROM incoming WHERE ${guard}
      ON CONFLICT(revision,type,page) DO UPDATE SET entries=excluded.entries RETURNING page`);
    if (written.length !== batch.length) throw new Error("Upload plan changed while writing structure metadata.");
  }
  // Verify stored bytes before callers advertise any newly inserted seeds.
  const verified = await query(`SELECT type,page,entries FROM bank_structures WHERE revision='${revision}'`);
  const wanted = new Map(expected.map(row => [`${row.type}:${row.page}`, row.entries]));
  if (verified.length !== expected.length || verified.some(row => wanted.get(`${row.type}:${row.page}`) !== row.entries)) {
    throw new Error("Structure metadata readback failed.");
  }
  return { pages: expected.length, changedPages: changes.length, rowsWritten: writes };
}
