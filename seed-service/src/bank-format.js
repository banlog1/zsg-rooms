export const PROFILE = "zsg-model-only-v5";
export const SCHEMA_VERSION = 1;
export const TYPES = Object.freeze(["temple", "village", "shipwreck"]);
const MIN_SEED = -(1n << 63n);
const MAX_SEED = (1n << 63n) - 1n;

export function isSeed(value) {
  if (typeof value !== "string" || !/^-?(0|[1-9][0-9]{0,18})$/.test(value)) return false;
  const number = BigInt(value);
  return number !== 0n && number >= MIN_SEED && number <= MAX_SEED && String(number) === value;
}

export function validateRecord(record) {
  if (!record || record.profile !== PROFILE || record.status !== "MODEL_ACCEPTED"
      || !TYPES.includes(record.type) || !isSeed(record.seed)) {
    throw new Error("Invalid seed-bank record or incompatible filter profile.");
  }
  const family = String(BigInt(record.seed) & ((1n << 48n) - 1n));
  if (record.family !== family) throw new Error("Seed-bank family does not match its seed.");
  for (const key of ["structure", "entry", "spawn", "bastion", "fortress"]) {
    if (!Array.isArray(record[key]) || record[key].length !== 2
        || !record[key].every(value => Number.isInteger(value) && Math.abs(value) <= 30000000)) {
      throw new Error("Invalid seed-bank coordinates.");
    }
  }
  return { profile: PROFILE, type: record.type, seed: record.seed, family };
}
