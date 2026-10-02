import { isSeed, isStructure } from "./bank-format.js";

// Batching immutable slot metadata keeps migrations well below daily D1 write quotas.
export const STRUCTURE_PAGE_SIZE = 256;
export const STRUCTURE_SCHEMA = `CREATE TABLE IF NOT EXISTS bank_structures (
  revision TEXT NOT NULL, type TEXT NOT NULL, page INTEGER NOT NULL CHECK(page>=0),
  entries TEXT NOT NULL, PRIMARY KEY(revision,type,page))`;

export function parseStructurePage(text) {
  const entries = JSON.parse(text);
  if (!Array.isArray(entries) || !entries.length || entries.length > STRUCTURE_PAGE_SIZE
      || entries.some(row => !Array.isArray(row) || row.length !== 3 || !isSeed(row[0]) || !isStructure(row.slice(1)))) {
    throw new Error("Invalid structure metadata page.");
  }
  return entries;
}

export function structureForSlot(text, slot, seed) {
  const entry = parseStructurePage(text)[slot % STRUCTURE_PAGE_SIZE];
  if (!entry || entry[0] !== seed) throw new Error("Structure metadata does not match the selected seed.");
  return entry.slice(1);
}
