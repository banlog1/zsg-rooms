import { readFileSync, writeFileSync } from 'node:fs';

// Development-only destination equivalence check. A timing win cannot override a mismatch.
function read(path) {
    const events = readFileSync(path, 'utf8').trim().split(/\r?\n/).map(JSON.parse);
    if (!events.some(event => event.event === 'complete')) throw new Error(`Incomplete profile: ${path}`);
    const snapshots = events.filter(event => event.event === 'snapshot');
    if (snapshots.length !== 18 || new Set(snapshots.map(event => event.phase)).size !== 18) {
        throw new Error(`Expected 18 distinct destination snapshots: ${path}`);
    }
    return { events, snapshots };
}

function differences(a, b) {
    return [...new Set([...Object.keys(a), ...Object.keys(b)])].filter(key => a[key] !== b[key]);
}

const [beforePath, afterPath, outputPath] = process.argv.slice(2);
if (!beforePath || !afterPath) throw new Error('Usage: node scripts/Compare-ViewerSeekProfiles.mjs before.jsonl after.jsonl [output.json]');
const before = read(beforePath), after = read(afterPath);
const rows = before.snapshots.map(a => {
    const b = after.snapshots.find(event => event.phase === a.phase);
    if (!b || a.file !== b.file || a.target !== b.target) throw new Error(`Workload mismatch: ${a.phase}`);
    const timing = profile => {
        const end = profile.events.find(event => event.phase === a.phase && event.event === 'phase-end');
        const settle = profile.events.find(event => event.phase === a.phase && event.event === 'light-settled');
        if (!end || end.actual !== a.target || !settle) throw new Error(`Missing exact target/settled capture: ${a.phase}`);
        return { seekMs: end.wallMs, settleMs: settle.wallMs, totalMs: end.wallMs + settle.wallMs,
            lightMs: end.lightMs, drains: end.drains, deferred: end.deferred };
    };
    const diff = {};
    for (const field of ['blocks', 'lights', 'entities']) diff[field] = differences(a[field], b[field]);
    const matches = Object.values(diff).every(keys => keys.length === 0)
        && a.viewerData === b.viewerData && a.dimension === b.dimension && !a.pendingLight && !b.pendingLight;
    return { phase: a.phase, file: a.file, target: a.target, dimension: a.dimension, matches,
        pendingLight: [a.pendingLight,b.pendingLight], viewerDataMatches: a.viewerData === b.viewerData,
        differences: diff, before: timing(before), after: timing(after) };
});
const report = { equivalent: rows.every(row => row.matches), destinations: rows.length, rows };
if (outputPath) writeFileSync(outputPath, JSON.stringify(report,null,2) + '\n');
console.table(rows.map(row => ({ phase: row.phase, beforeMs: Math.round(row.before.totalMs),
    afterMs: Math.round(row.after.totalMs), blocks: row.differences.blocks.length,
    lights: row.differences.lights.length, entities: row.differences.entities.length,
    viewerData: row.viewerDataMatches, matches: row.matches })));
console.log(report.equivalent ? 'PASS: captured destinations match' : 'FAIL: destination differences; candidate is not equivalent');
process.exitCode = report.equivalent ? 0 : 1;
