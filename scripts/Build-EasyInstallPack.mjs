import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repository = 'https://github.com/banlog1/zsg-rooms';
const modIds = new Set([
  'antigone', 'antiresourcereload', 'atum', 'chunkumulator', 'fabric', 'fast_reset',
  'forceport', 'fsg-mod', 'krypton', 'lazydfu', 'lazystronghold', 'lithium',
  'mcsrfairplay', 'replaymod', 'seedqueue', 'sleepbackground', 'sodium', 'speedrunapi',
  'speedrunigt', 'standardsettings', 'starlight', 'state-output', 'voyager', 'worldpreview',
]);
const hosts = new Set(['cdn.modrinth.com', 'raw.githubusercontent.com', 'www.replaymod.com', 'github.com']);

export function hashes(bytes) {
  return Object.fromEntries(['sha1', 'sha512'].map(algorithm => [algorithm,
    createHash(algorithm).update(bytes).digest('hex')]));
}

function version(value) {
  assert.match(value, /^\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?$/, 'Invalid release version');
  return value;
}

function fileEntry(mod) {
  assert.match(mod.file, /^[A-Za-z0-9][A-Za-z0-9.+_-]*\.jar$/, 'Unsafe mod filename');
  assert(!mod.file.includes('..'), 'Unsafe mod filename');
  assert(Number.isSafeInteger(mod.fileSize) && mod.fileSize > 0, 'Invalid file size');
  assert.match(mod.hashes.sha1, /^[a-f0-9]{40}$/, 'Invalid SHA-1');
  assert.match(mod.hashes.sha512, /^[a-f0-9]{128}$/, 'Invalid SHA-512');
  const url = new URL(mod.url);
  assert(url.protocol === 'https:' && hosts.has(url.hostname), 'Untrusted download URL');
  assert(!url.username && !url.password && !url.hash && !url.port, 'Unexpected URL credentials or fragment');
  return {
    path: `mods/${mod.file}`, hashes: { ...mod.hashes },
    env: { client: 'required', server: 'unsupported' }, downloads: [mod.url], fileSize: mod.fileSize,
  };
}

export function createIndex(lock, core, viewer) {
  assert.equal(lock.minecraft, '1.16.1');
  assert.equal(lock.fabricLoader, '0.19.3');
  assert.equal(lock.mods.length, modIds.size, 'Unexpected third-party mod count');
  const seen = new Set();
  for (const mod of lock.mods) {
    assert(modIds.has(mod.id) && !seen.has(mod.id), `Unexpected or duplicate mod: ${mod.id}`);
    seen.add(mod.id);
  }
  version(core.version);
  version(viewer.version);
  const release = `${repository}/releases/download/v${core.version}`;
  const files = lock.mods.map(fileEntry);
  for (const [prefix, artifact] of [['zsg-rooms', core], ['zsg-replay-viewer', viewer]]) {
    const file = `${prefix}-${artifact.version}.jar`;
    files.push(fileEntry({ file, fileSize: artifact.bytes.length, hashes: hashes(artifact.bytes),
      url: `${release}/${file}` }));
  }
  assert.equal(new Set(files.map(file => file.path.toLowerCase())).size, files.length, 'Duplicate mod filename');
  return {
    formatVersion: 1, game: 'minecraft', versionId: core.version, name: 'ZSG Rooms',
    summary: 'ZSG Rooms racing and replay suite.',
    files, dependencies: { minecraft: lock.minecraft, 'fabric-loader': lock.fabricLoader },
  };
}

export function packDefaults() {
  return {
    'overrides/options.txt': 'soundCategory_music:0.0\n',
    'overrides/config/replaymod.json': JSON.stringify({ recording: {
      recordSingleplayer: false, recordServer: false, autoStartRecording: false,
    } }, null, 2) + '\n',
    'overrides/config/zsg-rooms-replay.properties':
      '# Clean pack defaults; recording remains opt-in.\nenabled=false\nreplayModRecordingDisabled=true\n',
    'overrides/ZSG-ROOMS-SETUP.txt':
      'ZSG Rooms\n\nOpen ZSG Rooms from the Minecraft title screen to create or join a room.\n'
      + 'Music starts at 0%; change it in Options > Music & Sounds. Other sound volumes keep their defaults.\n'
      + 'Recording is optional: Settings > Recording > Record Local Worlds. Wait for library setup before entering a world.\n'
      + 'ReplayMod automatic recording is disabled to avoid recording twice.\n'
      + 'Java 17 is recommended in the launcher. This pack does not override Java paths, memory, keybinds or launch commands.\n'
      + 'Optional picker/loading artwork: download zsg-rooms-filter-gallery.zip from the release, put it in resourcepacks and enable it.\n'
      + 'This is a Rooms/practice pack, not a certified leaderboard-legal speedrunning pack.\n'
      + `Setup and sources: ${repository}\n`,
  };
}

export async function verifyDownloads(files) {
  const failures = [];
  for (const file of files) {
    try {
      const response = await fetch(file.downloads[0], { signal: AbortSignal.timeout(120_000) });
      assert(response.ok, `HTTP ${response.status}`);
      assert(new URL(response.url).protocol === 'https:', 'Non-HTTPS redirect');
      const digests = { sha1: createHash('sha1'), sha512: createHash('sha512') };
      let size = 0;
      for await (const chunk of response.body) {
        size += chunk.length;
        assert(size <= file.fileSize, 'Download larger than expected');
        for (const digest of Object.values(digests)) digest.update(chunk);
      }
      assert.equal(size, file.fileSize, 'Download size mismatch');
      for (const [algorithm, digest] of Object.entries(digests)) {
        assert.equal(digest.digest('hex'), file.hashes[algorithm], `${algorithm} mismatch`);
      }
      console.log(`Verified ${file.path}`);
    } catch (error) {
      failures.push(`${file.path}: ${error.message}`);
    }
  }
  assert.equal(failures.length, 0, failures.join('\n'));
}

async function main() {
  const [mode, coreVersion, viewerVersion] = process.argv.slice(2);
  const lock = JSON.parse(await readFile(path.join(root, 'pack/mods.lock.json'), 'utf8'));
  if (mode === '--verify-downloads') {
    await verifyDownloads(lock.mods.map(fileEntry));
    return;
  }
  if (mode === '--verify-release') {
    const indexPath = coreVersion ? path.resolve(coreVersion) : path.join(root, 'build/easy-install-metadata/modrinth.index.json');
    const index = JSON.parse(await readFile(indexPath, 'utf8'));
    await verifyDownloads(index.files);
    return;
  }
  assert.equal(mode, '--build', 'Use --build CORE_VERSION VIEWER_VERSION, --verify-downloads or --verify-release');
  version(coreVersion);
  version(viewerVersion);
  const core = { version: coreVersion, bytes: await readFile(path.join(root, `build/libs/zsg-rooms-${coreVersion}.jar`)) };
  const viewer = { version: viewerVersion, bytes: await readFile(path.join(root, `replay-viewer/build/libs/zsg-replay-viewer-${viewerVersion}.jar`)) };
  const index = createIndex(lock, core, viewer);
  const outputs = { 'modrinth.index.json': JSON.stringify(index, null, 2) + '\n', ...packDefaults() };
  for (const [name, content] of Object.entries(outputs)) {
    const destination = path.join(root, 'build/easy-install-metadata', name);
    await mkdir(path.dirname(destination), { recursive: true });
    await writeFile(destination, content, 'utf8');
  }
  console.log(`Prepared ${index.files.length} pinned mods for ZSG Rooms ${coreVersion}.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) await main();
