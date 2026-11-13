import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, rename, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createIndex, packDefaults } from './Build-EasyInstallPack.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const upstream = 'https://github.com/jojoe77777/Toolscreen';
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

export function validateToolscreenLock(lock) {
  assert.match(lock.version, /^\d+\.\d+\.\d+$/, 'ToolScreen must use a pinned stable version');
  assert.match(lock.sourceCommit, /^[a-f0-9]{40}$/, 'Missing ToolScreen source commit');
  assert.equal(lock.file, `Toolscreen-${lock.version}-double-click-me.exe`, 'Unexpected installer filename');
  assert.equal(lock.url, `${upstream}/releases/download/v${lock.version}/${lock.file}`, 'Unexpected installer source');
  assert(Number.isSafeInteger(lock.fileSize) && lock.fileSize > 0 && lock.fileSize <= 64 * 1024 * 1024,
    'Invalid installer size');
  assert.match(lock.sha256, /^[a-f0-9]{64}$/, 'Missing installer SHA-256');
  assert.equal(lock.license, 'MIT');
}

export function verifyInstaller(lock, bytes) {
  validateToolscreenLock(lock);
  assert.equal(bytes.length, lock.fileSize, 'ToolScreen installer size mismatch');
  assert.equal(sha256(bytes), lock.sha256, 'ToolScreen installer SHA-256 mismatch');
}

export async function loadInstaller(lock, cache, { offline = false, fetchImpl = fetch } = {}) {
  validateToolscreenLock(lock);
  const destination = path.join(cache, lock.file);
  try {
    assert.equal((await stat(destination)).size, lock.fileSize, 'Cached ToolScreen installer size mismatch');
    const bytes = await readFile(destination);
    verifyInstaller(lock, bytes);
    return bytes;
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
  assert(!offline, 'ToolScreen installer is not cached; run easyInstallToolscreenPack once without --offline');
  const response = await fetchImpl(lock.url, { signal: AbortSignal.timeout(120_000) });
  assert(response.ok, `ToolScreen download: HTTP ${response.status}`);
  assert.equal(new URL(response.url).protocol, 'https:', 'Non-HTTPS installer redirect');
  const chunks = [];
  let size = 0;
  for await (const chunk of response.body) {
    size += chunk.length;
    assert(size <= lock.fileSize, 'ToolScreen download larger than expected');
    chunks.push(chunk);
  }
  const bytes = Buffer.concat(chunks);
  verifyInstaller(lock, bytes);
  await mkdir(cache, { recursive: true });
  const temporary = `${destination}.${randomUUID()}.tmp`;
  try {
    await writeFile(temporary, bytes, { flag: 'wx' });
    await rename(temporary, destination);
  } finally {
    await rm(temporary, { force: true });
  }
  return bytes;
}

export function toolscreenSetup(lock) {
  validateToolscreenLock(lock);
  return `ZSG Rooms + ToolScreen (Windows 10/11 only)

The normal Rooms pack is also available without ToolScreen.
ToolScreen is a separate overlay/resize tool, not a Fabric mod.

1. Import zsg-rooms-multimc-toolscreen.zip with MultiMC's Add Instance > Import from ZIP.
2. Leave Minecraft closed. Right-click the imported instance > Instance Folder.
   Use the instance folder, not Minecraft Folder or the mods folder.
3. Double-click ${lock.file} and follow ToolScreen's setup for this instance.
   It is already in the correct folder: no extra download or moving files is needed.
   The installer configures ToolScreen's launch hook. Keep its installed files in place.
4. Launch Minecraft. Java 17 is recommended and can be selected in the instance's Java settings.
   In fullscreen, Ctrl+I opens ToolScreen settings. Open ZSG Rooms from the title screen.

Importing alone does not run the installer or activate ToolScreen. Windows or
ToolScreen may show security prompts because it injects an overlay into Minecraft.
Review prompts yourself; this pack never disables antivirus or adds exclusions.
You can cancel setup and use Rooms without ToolScreen. Do not bypass a security
block just to finish setup; use the normal pack and investigate it first.

To remove ToolScreen, close Minecraft, run the installer again and choose Uninstall.
The Rooms updater does not manage ToolScreen. This pack starts with ToolScreen
${lock.version}; any later ToolScreen updates are handled separately by that tool.
No personal ToolScreen profiles, accounts, Java paths or memory settings are included.

Official release: ${upstream}/releases/tag/v${lock.version}
Source: ${upstream}/tree/${lock.sourceCommit}
Installer SHA-256: ${lock.sha256}
License and copyright: Toolscreen-LICENSE.txt
Rooms/mod defaults: Minecraft Folder > ZSG-ROOMS-SETUP.txt
`;
}

export function createToolscreenFiles(modLock, lock, core, viewer, installer, license) {
  verifyInstaller(lock, installer);
  assert(license.startsWith('MIT License\n') && license.includes('Copyright (c) 2026 jojoe77777'),
    'Missing ToolScreen license attribution');
  const index = createIndex(modLock, core, viewer);
  index.name = 'ZSG Rooms + ToolScreen (Windows)';
  index.summary = 'Rooms racing/replay suite with the optional ToolScreen installer; one-time setup required.';
  // MultiMC preserves root files while moving Modrinth overrides into .minecraft.
  // Do not add instance.cfg: that selects the native importer and skips mod downloads.
  return {
    'modrinth.index.json': JSON.stringify(index, null, 2) + '\n',
    ...packDefaults(),
    [lock.file]: installer,
    'Toolscreen-LICENSE.txt': license,
    'TOOLSCREEN-SETUP.txt': toolscreenSetup(lock),
    'toolscreen-release.json': JSON.stringify(lock, null, 2) + '\n',
  };
}

async function main() {
  const [mode, coreVersion, viewerVersion, ...flags] = process.argv.slice(2);
  assert.equal(mode, '--build', 'Use --build CORE_VERSION VIEWER_VERSION [--offline]');
  assert(flags.length === 0 || (flags.length === 1 && flags[0] === '--offline'), 'Unexpected arguments');
  for (const version of [coreVersion, viewerVersion]) {
    assert.match(version, /^\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?$/, 'Invalid release version');
  }
  const lock = JSON.parse(await readFile(path.join(root, 'pack/toolscreen.lock.json'), 'utf8'));
  const modLock = JSON.parse(await readFile(path.join(root, 'pack/mods.lock.json'), 'utf8'));
  const core = { version: coreVersion, bytes: await readFile(path.join(root, `build/libs/zsg-rooms-${coreVersion}.jar`)) };
  const viewer = { version: viewerVersion,
    bytes: await readFile(path.join(root, `replay-viewer/build/libs/zsg-replay-viewer-${viewerVersion}.jar`)) };
  const installer = await loadInstaller(lock, path.join(root, 'build/toolscreen-download'), { offline: flags.includes('--offline') });
  const license = (await readFile(path.join(root, 'pack/Toolscreen-LICENSE.txt'), 'utf8')).replaceAll('\r\n', '\n');
  const outputs = createToolscreenFiles(modLock, lock, core, viewer, installer, license);
  for (const [name, content] of Object.entries(outputs)) {
    const destination = path.join(root, 'build/toolscreen-pack', name);
    await mkdir(path.dirname(destination), { recursive: true });
    await writeFile(destination, content);
  }
  console.log(`Prepared optional ToolScreen ${lock.version} pack; installer SHA-256 verified. No installer was executed.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) await main();
