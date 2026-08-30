import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const guide = 'https://devslab.kr/brand/open-source/';
const terminalLogo = 'DataLinq\n========\n[ source ] ===> [ target ]\nOpen source by DevsLab\n';
const assets = new Map([
  ['.github/assets/project-mark.svg', '6a5ddb30c2f0c6251d60a96f78b88f026e946aa7c5cda31284b29194f8482309'],
  ['.github/assets/project-lockup.svg', '123d4746b874a955b6f4c4a9f1373a2b312b01016028703ff3257fcc0e0e2c6d'],
  ['.github/assets/readme-header.png', 'cc5b10846a31c4c458ccd784fe158c83616368df0ffb645015561b059343267d'],
  ['.github/assets/social-preview.png', '9b83784be89547d512a174853e7e8bbd56ea43975b076530ce930839b7067c3a'],
  ['src/main/resources/branding/logo.txt', 'aa4aa8c9bcc2b07257f96854a481496b8229d28bc27057eedbadbce4fa1cc6d7'],
]);

const file = (relative) => resolve(root, relative);
const text = (relative) => readFile(file(relative), 'utf8');
const hash = async (relative) => createHash('sha256').update(await readFile(file(relative))).digest('hex');

for (const [relative, expected] of assets) {
  assert.equal(await hash(relative), expected, `${relative} must match the oss-brand v0.1.1 O11 asset`);
}

const mark = await text('.github/assets/project-mark.svg');
assert.match(mark, /data-oss-project="O11"/, 'project mark must identify O11');
assert.match(mark, /M5 6H11V12H5Z/, 'project mark must retain O11 source column geometry');
assert.match(mark, /M17 12L21 16L17 20/, 'project mark must retain O11 transfer path geometry');

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

console.log(`O11 brand contract passed (${assets.size} exact v0.1.1 assets, terminal, README, CI, and release).`);
