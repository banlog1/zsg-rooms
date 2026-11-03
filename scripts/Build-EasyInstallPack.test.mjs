import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { createIndex, hashes, packDefaults } from './Build-EasyInstallPack.mjs';

const lock = JSON.parse(await readFile(new URL('../pack/mods.lock.json', import.meta.url), 'utf8'));
const core = { version: '1.1.1', bytes: Buffer.from('core fixture') };
const viewer = { version: '0.6.1', bytes: Buffer.from('viewer fixture') };
const make = change => { const copy = structuredClone(lock); change(copy); return createIndex(copy, core, viewer); };

test('pins exactly the intended suite, with no experimental probe or private instance data', () => {
  const index = createIndex(lock, core, viewer);
  assert.equal(index.files.length, 26);
  assert.deepEqual(index.dependencies, { minecraft: '1.16.1', 'fabric-loader': '0.19.3' });
  assert.equal(index.formatVersion, 1);
  assert.equal(index.game, 'minecraft');
  for (const file of index.files) {
    assert.match(file.path, /^mods\/[^/]+\.jar$/);
    assert.deepEqual(file.env, { client: 'required', server: 'unsupported' });
    assert(!file.path.includes('performance-probe'));
  }
  assert.equal(index.files[24].downloads[0], 'https://github.com/banlog1/zsg-rooms/releases/download/v1.1.1/zsg-rooms-1.1.1.jar');
  assert.equal(index.files[25].downloads[0], 'https://github.com/banlog1/zsg-rooms/releases/download/v1.1.1/zsg-replay-viewer-0.6.1.jar');
  assert.deepEqual(index.files[24].hashes, hashes(core.bytes));
  assert.deepEqual(index.files[25].hashes, hashes(viewer.bytes));
});

test('rejects missing, additional and duplicate mods and loader drift', () => {
  assert.throws(() => make(copy => copy.mods.pop()));
  assert.throws(() => make(copy => copy.mods.push(copy.mods[0])));
  assert.throws(() => make(copy => copy.mods[0].id = 'zsg-performance-probe'));
  assert.throws(() => make(copy => copy.mods[0].id = copy.mods[1].id));
  assert.throws(() => make(copy => copy.fabricLoader = '0.14.0'));
  assert.throws(() => make(copy => copy.minecraft = '1.16.5'));
});

test('rejects traversal, renamed executables and duplicate destination paths', () => {
  for (const file of ['../outside.jar', 'C:\\private.jar', '/absolute.jar', 'bad.exe', 'bad..jar', 'a/b.jar']) {
    assert.throws(() => make(copy => copy.mods[0].file = file));
  }
  assert.throws(() => make(copy => copy.mods[0].file = copy.mods[1].file.toUpperCase().replace('.JAR', '.jar')));
});

test('requires hashes, valid sizes and approved HTTPS sources', () => {
  assert.throws(() => make(copy => copy.mods[0].hashes.sha512 = '0'));
  assert.throws(() => make(copy => copy.mods[0].hashes.sha1 = '0'));
  for (const size of [-1, 0, 1.5, Number.MAX_SAFE_INTEGER + 1]) {
    assert.throws(() => make(copy => copy.mods[0].fileSize = size));
  }
  for (const url of ['http://github.com/a.jar', 'file:///private.jar', 'https://example.com/a.jar',
    'https://user:secret@github.com/a.jar', 'https://github.com:8443/a.jar', 'https://github.com/a.jar#secret']) {
    assert.throws(() => make(copy => copy.mods[0].url = url));
  }
});

test('release versions cannot escape paths and prereleases stay pinned to their own tag', () => {
  assert.throws(() => createIndex(lock, { ...core, version: '../1.1.1' }, viewer));
  assert.throws(() => createIndex(lock, core, { ...viewer, version: 'latest' }));
  const index = createIndex(lock, { ...core, version: '1.1.1-experimental.1' }, viewer);
  assert(index.files[25].downloads[0].includes('/v1.1.1-experimental.1/'));
});

test('minimal defaults avoid duplicate recording and never export personal settings', () => {
  const defaults = packDefaults();
  assert.deepEqual(Object.keys(defaults).sort(), [
    'overrides/ZSG-ROOMS-SETUP.txt', 'overrides/config/replaymod.json',
    'overrides/config/zsg-rooms-replay.properties',
  ]);
  assert.deepEqual(JSON.parse(defaults['overrides/config/replaymod.json']), { recording: {
    recordSingleplayer: false, recordServer: false, autoStartRecording: false,
  } });
  assert.match(defaults['overrides/config/zsg-rooms-replay.properties'], /\nenabled=false\n/);
  assert.match(defaults['overrides/config/zsg-rooms-replay.properties'], /replayModRecordingDisabled=true/);
  assert(!JSON.stringify(defaults).includes('C:\\'));
});
