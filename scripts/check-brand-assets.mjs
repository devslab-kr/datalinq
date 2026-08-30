import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const guide = 'https://devslab.kr/brand/open-source/';
const terminalLogo = 'DataLinq\n========\n[ source ] ===> [ target ]\nOpen source by DevsLab\n';
const assets = new Map([
  ['.github/assets/project-mark.svg', '51660ff854b4bd7e92d4ccc1929441c064f572d7a09ddac552fcd50c2fed789c'],
  ['.github/assets/project-lockup.svg', 'fbb891d126f83aa46dd192cf9215e334e1cc1e9d6e21812235c0a4732d8e7228'],
  ['.github/assets/readme-header.png', 'b2e6b2cc6234470d95a384efe98a80f3014398aa23dd91cffa0ea6dc9500db1d'],
  ['.github/assets/social-preview.png', '924e95e70a2992253ee91314d1114237178e6dce89ce09b4b9eb4b49bc1361b4'],
  ['src/main/resources/branding/logo.txt', 'aa4aa8c9bcc2b07257f96854a481496b8229d28bc27057eedbadbce4fa1cc6d7'],
]);

const file = (relative) => resolve(root, relative);
const text = (relative) => readFile(file(relative), 'utf8');
const hash = async (relative) => createHash('sha256').update(await readFile(file(relative))).digest('hex');

for (const [relative, expected] of assets) {
  assert.equal(await hash(relative), expected, `${relative} must match the oss-brand v0.2.0 O11 asset`);
}

const mark = await text('.github/assets/project-mark.svg');
assert.match(mark, /data-oss-project="O11"/, 'project mark must identify O11');
assert.match(mark, /data-layer="q-frame"/, 'project mark must use the shared Q frame');
assert.match(mark, /<rect x="5" y="5" width="16" height="16" rx="2"/, 'project mark must retain the rear Q frame');
assert.match(mark, /<rect x="11" y="11" width="16" height="16" rx="2"/, 'project mark must retain the front Q frame');
assert.match(mark, /M13 15H24/, 'project mark must retain the O11 forward transfer path');
assert.match(mark, /M25 21H14/, 'project mark must retain the O11 return transfer path');

assert.equal(await text('src/main/resources/branding/logo.txt'), terminalLogo, 'bundled terminal logo must be the stable UTF-8 no-color O11 asset');
assert.ok(!terminalLogo.includes('\u001b'), 'bundled terminal logo must contain no ANSI escape character');
assert.deepEqual(terminalLogo.trimEnd().split('\n').map((line) => line.length), [8, 8, 26, 22], 'terminal logo line widths must remain stable');

for (const [relative, endorsement] of [
  ['README.md', 'Open source by DevsLab'],
  ['README.ko.md', 'DevsLab 오픈소스'],
]) {
  const readme = await text(relative);
  assert.match(readme, /\.github\/assets\/readme-header\.png/, `${relative} must use the vendored O11 README header`);
  assert.ok(readme.includes(guide), `${relative} must link to the canonical OSS brand guide`);
  assert.ok(readme.includes(endorsement), `${relative} must include its localized DevsLab endorsement`);
}

const brandCommand = 'run: node scripts/check-brand-assets.mjs';
for (const [relative, gradleCommand] of [
  ['.github/workflows/ci.yml', 'run: ./gradlew build'],
  ['.github/workflows/release.yml', 'run: ./gradlew shadowJar'],
]) {
  const workflow = await text(relative);
  const brandIndex = workflow.indexOf(brandCommand);
  assert.ok(workflow.includes('actions/setup-node@v4') && workflow.includes("node-version: '22'"), `${relative} must provide Node 22 for the brand checker`);
  assert.ok(brandIndex >= 0 && brandIndex < workflow.indexOf(gradleCommand), `${relative} must verify O11 brand sources before Gradle`);
}

console.log(`O11 brand contract passed (${assets.size} exact v0.2.0 assets, terminal, README, CI, and release).`);
