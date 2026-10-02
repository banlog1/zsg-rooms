export const PROFILE = "zsg-model-only-v5";
export const SCHEMA_VERSION = 1;
export const TYPES = Object.freeze(["temple", "village", "shipwreck", "buried_treasure", "ruined_portal", "aa_temple"]);
const MIN_SEED = -(1n << 63n);
const MAX_SEED = (1n << 63n) - 1n;

export function isSeed(value) {
  if (typeof value !== "string" || !/^-?(0|[1-9][0-9]{0,18})$/.test(value)) return false;
  const number = BigInt(value);
  return number !== 0n && number >= MIN_SEED && number <= MAX_SEED && String(number) === value;
}

export const isStructure = value => Array.isArray(value) && value.length === 2
  && value.every(n => Number.isSafeInteger(n) && Math.abs(n) <= 30000000);

export function validateRecord(record) {
  if (!record || record.profile !== PROFILE || record.status !== "MODEL_ACCEPTED"
      || !TYPES.includes(record.type) || !isSeed(record.seed)) {
    throw new Error("Invalid seed-bank record or incompatible filter profile.");
  }
  const family = String(BigInt(record.seed) & ((1n << 48n) - 1n));
  if (record.family !== family) throw new Error("Seed-bank family does not match its seed.");
  if (record.type === "buried_treasure" && record.buriedTreasureRule !== "mapless-regular-v1") {
    throw new Error("Incompatible buried-treasure acceptance rule.");
  }
  if (record.type === "aa_temple") {
    const point = value => Array.isArray(value) && value.length === 2
      && value.every(n => Number.isInteger(n) && Math.abs(n) <= 30000000);
    const near = (a, b, radius) => point(a) && point(b)
      && (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 <= radius ** 2;
    const points = (value, count) => Array.isArray(value) && value.length === count
      && value.every(point) && new Set(value.map(p => p.join(","))).size === count;
    if (record.aaTempleRule !== "aa-temple-v4" || !Number.isSafeInteger(record.gunpowder) || record.gunpowder < 22
        || !near(record.structure, record.village, 70) || !points(record.extraTemples, 2)
        || !record.extraTemples.every(p => near(record.structure, p, 1024) && p.join(",") !== record.structure.join(","))
        || !point(record.innerEndGateway) || !point(record.outerEndGateway)
        || record.endFirstShipRadius !== 512 || record.endConnectionRadius !== 1024 || record.endShipLayout !== "connected"
        || !points(record.endCities, 3) || !points(record.endShips, 3)
        || !near(record.outerEndGateway, record.endShips[0], 512)
        || !near(record.endShips[0], record.endShips[1], 1024)
        || !(near(record.endShips[0], record.endShips[2], 1024) || near(record.endShips[1], record.endShips[2], 1024))) {
      throw new Error("Incompatible AA Thunderless acceptance rule or geometry.");
    }
  }
  if (record.type === "ruined_portal") {
    const counts = [record.goldenAxes, record.goldenPickaxes, record.flintAndSteel, record.fireCharges, record.flint, record.ironNuggets];
    if (record.ruinedPortalRule !== "frame-completable-v3"
        || !counts.every(value => Number.isSafeInteger(value) && value >= 0)
        || record.goldenAxes + record.goldenPickaxes < 1 || record.goldenAxes + record.goldenPickaxes > 8
        || !(record.flintAndSteel > 0 || record.fireCharges >= 5 || (record.flint > 0 && record.ironNuggets >= 9))) {
      throw new Error("Incompatible ruined-portal acceptance rule or resources.");
    }
  }
  for (const key of ["structure", "entry", "spawn", "bastion", "fortress"]) {
    if (!Array.isArray(record[key]) || record[key].length !== 2
        || !record[key].every(value => Number.isInteger(value) && Math.abs(value) <= 30000000)) {
      throw new Error("Invalid seed-bank coordinates.");
    }
  }
  return { profile: PROFILE, type: record.type, seed: record.seed, family, structure: [...record.structure] };
}
