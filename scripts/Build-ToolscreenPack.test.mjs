import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { createIndex, packDefaults } from './Build-EasyInstallPack.mjs';
import { createToolscreenFiles, loadInstaller, toolscreenSetup, validateToolscreenLock,
  verifyInstaller } from './Build-ToolscreenPack.mjs';

const modLock = JSON.parse(await readFile(new URL('../pack/mods.lock.json', import.meta.url), 'utf8'));
const lock = JSON.parse(await readFile(new URL('../pack/toolscreen.lock.json', import.meta.url), 'utf8'));
const license = (await readFile(new URL('../pack/Toolscreen-LICENSE.txt', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
const core = { version: '1.1.1', bytes: Buffer.from('core fixture') };
const viewer = { version: '0.6.1', bytes: Buffer.from('viewer fixture') };
const installer = Buffer.from('installer fixture, never executed');
const fixtureLock = { ...lock, fileSize: installer.length,
  sha256: createHash('sha256').update(installer).digest('hex') };
const noNetwork = () => assert.fail('Unexpected network access');

async function scratch(t) {
  const parent = path.resolve(os.tmpdir());
  const folder = await mkdtemp(path.join(parent, 'zsg-toolscreen-pack-'));
  assert.equal(path.dirname(folder), parent);
  t.after(() => rm(folder, { recursive: true, force: true }));
  return folder;
}

function download(body = [installer], { ok = true, status = 200, url = lock.url } = {}) {
  return async (actualUrl, options) => {
    assert.equal(actualUrl, lock.url);
    assert(options.signal instanceof AbortSignal);
    return { ok, status, url, body: (async function* () { yield* body; })() };
  };
}

test('pins the official release asset, source commit, hash and license', () => {
  validateToolscreenLock(lock);
  assert.match(lock.url, /\/releases\/download\/v1\.5\.2\/Toolscreen-1\.5\.2-double-click-me\.exe$/);
  assert.equal(lock.sha256, '488c93ab87b7c2809cb9d631951e61b55218852928b3c538498d3b1aa5823005');
  assert.equal(lock.fileSize, 33032032);
  assert(license.includes('Copyright (c) 2026 jojoe77777'));
  assert(license.includes('The above copyright notice and this permission notice'));
});

test('rejects traversal, unpinned downloads, other hosts and malformed asset pins', () => {
  for (const patch of [
    { version: '../1.5.2' }, { version: 'latest' }, { sourceCommit: 'main' },
    { file: '../Toolscreen.exe' }, { file: 'C:\\Toolscreen.exe' }, { file: 'Toolscreen.jar' },
    { url: lock.url.replace('https:', 'http:') }, { url: lock.url.replace('github.com', 'example.com') },
    { url: `${lock.url}#anything` }, { url: lock.url.replace('v1.5.2/', 'latest/') },
    { sha256: '0' }, { fileSize: 0 }, { fileSize: 1.5 }, { fileSize: 65 * 1024 * 1024 }, { license: '' },
  ]) assert.throws(() => validateToolscreenLock({ ...lock, ...patch }), JSON.stringify(patch));
});

test('optional pack preserves the exact suite and defaults with only explicit root additions', () => {
  const original = JSON.stringify(modLock);
  const files = createToolscreenFiles(modLock, fixtureLock, core, viewer, installer, license);
  assert.deepEqual(Object.keys(files).sort(), [
    'modrinth.index.json', ...Object.keys(packDefaults()), lock.file,
    'Toolscreen-LICENSE.txt', 'TOOLSCREEN-SETUP.txt', 'toolscreen-release.json',
  ].sort());
  const index = JSON.parse(files['modrinth.index.json']);
  const standard = createIndex(modLock, core, viewer);
  assert.deepEqual(index.files, standard.files);
  assert.deepEqual(index.dependencies, standard.dependencies);
  assert.equal(index.versionId, standard.versionId);
  assert.match(index.name, /ToolScreen.*Windows/);
  for (const [name, content] of Object.entries(packDefaults())) assert.equal(files[name], content);
  assert.deepEqual(files[lock.file], installer);
  assert.equal(files['Toolscreen-LICENSE.txt'], license);
  assert.deepEqual(JSON.parse(files['toolscreen-release.json']), fixtureLock);
  assert.equal(JSON.stringify(modLock), original);
  assert(!('instance.cfg' in files), 'instance.cfg would bypass MultiMC Modrinth downloads');
  assert(!('overrides/instance.cfg' in files));
  assert(!('pre-launch.txt' in files));
});

test('no installer or startup command leaks into the standard pack', () => {
  const index = createIndex(modLock, core, viewer);
  assert.equal(index.files.length, 26);
  assert(!JSON.stringify(index).toLowerCase().includes('toolscreen'));
  const defaults = JSON.stringify(packDefaults());
  assert(!defaults.toLowerCase().includes('toolscreen'));
  assert(!defaults.includes('PreLaunchCommand'));
  assert(!defaults.includes('.exe'));
});

test('one-time setup clearly distinguishes instance folder, consent and uninstall', () => {
  const setup = toolscreenSetup(lock);
  for (const text of ['Windows 10/11', 'Leave Minecraft closed', 'Instance Folder', lock.file,
    'does not run the installer', 'never disables antivirus', 'Uninstall', 'Java 17 is recommended', 'Ctrl+I', lock.sha256]) {
    assert(setup.includes(text), text);
  }
  assert(!setup.includes('C:\\'));
});

test('installer and license validation happens before pack output', () => {
  verifyInstaller(fixtureLock, installer);
  assert.throws(() => verifyInstaller(fixtureLock, Buffer.from('wrong size')));
  assert.throws(() => verifyInstaller(fixtureLock, Buffer.alloc(installer.length)), /SHA-256 mismatch/);
  assert.throws(() => createToolscreenFiles(modLock, fixtureLock, core, viewer, installer, ''), /license/);
  assert.throws(() => createToolscreenFiles(modLock, fixtureLock, core, viewer, Buffer.alloc(installer.length), license));
});

test('downloads only a verified installer, then reuses its verified offline cache', async t => {
  const cache = await scratch(t);
  assert.deepEqual(await loadInstaller(fixtureLock, cache, { fetchImpl: download() }), installer);
  assert.deepEqual(await readdir(cache), [lock.file]);
  assert.deepEqual(await readFile(path.join(cache, lock.file)), installer);
  assert.deepEqual(await loadInstaller(fixtureLock, cache, { offline: true, fetchImpl: noNetwork }), installer);
});

test('offline cache misses never download', async t => {
  const cache = await scratch(t);
  await assert.rejects(loadInstaller(fixtureLock, cache, { offline: true, fetchImpl: noNetwork }), /not cached/);
  assert.deepEqual(await readdir(cache), []);
});

test('corrupt cache is refused, not silently replaced or packaged', async t => {
  const cache = await scratch(t);
  for (const bytes of [Buffer.from('wrong size'), Buffer.alloc(installer.length)]) {
    await writeFile(path.join(cache, lock.file), bytes);
    await assert.rejects(loadInstaller(fixtureLock, cache, { fetchImpl: noNetwork }), /mismatch/);
    assert.deepEqual(await readFile(path.join(cache, lock.file)), bytes);
  }
});

test('bad, truncated, oversized or insecure downloads never enter the cache', async t => {
  const cache = await scratch(t);
  for (const fetchImpl of [
    download([Buffer.alloc(installer.length)]), download([installer.subarray(1)]),
    download([installer, Buffer.from('extra')]), download([], { ok: false, status: 404 }),
    download([installer], { url: 'http://example.com/Toolscreen.exe' }),
    async () => { throw new Error('network interrupted'); },
  ]) {
    await assert.rejects(loadInstaller(fixtureLock, cache, { fetchImpl }));
    assert.deepEqual(await readdir(cache), []);
  }
});

test('releases point to the matching core tag, including prereleases', () => {
  const files = createToolscreenFiles(modLock, fixtureLock, { ...core, version: '1.1.2-test.1' }, viewer, installer, license);
  const index = JSON.parse(files['modrinth.index.json']);
  assert.equal(index.versionId, '1.1.2-test.1');
  assert(index.files.at(-1).downloads[0].includes('/v1.1.2-test.1/'));
});
