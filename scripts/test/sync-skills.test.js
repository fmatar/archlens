import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';
import {
  parseSkillFrontmatter,
  syncSkills,
  checkSkillsSync,
  runSyncSkillsCli
} from '../sync-skills.js';
import { setVersion } from '../set-version.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '../..');

function createFixtureRepo(version = '1.1.1') {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-skill-sync-'));
  fs.mkdirSync(path.join(tmp, 'skills', 'archlens', 'references'), { recursive: true });
  fs.mkdirSync(path.join(tmp, 'cli'), { recursive: true });
  fs.mkdirSync(path.join(tmp, '.claude-plugin'), { recursive: true });

  fs.writeFileSync(
    path.join(tmp, 'package.json'),
    JSON.stringify({ name: '@fmatar/archlens', version }, null, 2)
  );
  fs.writeFileSync(
    path.join(tmp, 'cli', 'package.json'),
    JSON.stringify({ name: '@fmatar/archlens-skill', version, files: ['bin', 'src', 'skills'] }, null, 2)
  );
  fs.writeFileSync(
    path.join(tmp, 'skills', 'archlens', 'SKILL.md'),
    `---
name: archlens
description: >-
  Clean Architecture governance and MCP companion skill.
license: Apache-2.0
version: ${version}
metadata:
  author: fmatar
  version: "${version}"
---

# Archlens
`
  );
  fs.writeFileSync(path.join(tmp, 'skills', 'archlens', 'references', 'guide.md'), '# Guide\n');
  fs.writeFileSync(
    path.join(tmp, '.claude-plugin', 'plugin.json'),
    JSON.stringify(
      {
        name: 'archlens',
        version,
        description: 'Archlens Clean Architecture Workbench & Skills',
        skills: './skills',
        mcpServers: './.mcp.json'
      },
      null,
      2
    )
  );
  return tmp;
}

describe('Skills Sync & Release Governance (scripts/sync-skills.js & scripts/set-version.js)', () => {
  it('parses single-line, folded, literal block, comments, and missing frontmatter in SKILL.md', () => {
    assert.equal(parseSkillFrontmatter('# No Frontmatter'), null);

    const parsed = parseSkillFrontmatter(`---
# Comment line
name: "archlens"
invalid line without colon
description: |
  Line one
  Line two
license: Apache-2.0
version: 1.1.1
metadata:
  author: "fmatar"
  version: "1.1.1"
---
# Body`);
    assert.equal(parsed.name, 'archlens');
    assert.equal(parsed.description, 'Line one\nLine two');
    assert.equal(parsed.version, '1.1.1');
    assert.equal(parsed.metadata.author, 'fmatar');
    assert.equal(parsed.metadata.version, '1.1.1');
  });

  it('syncs root skills/ to cli/skills/, stamps version, and removes stale files', () => {
    const tmp = createFixtureRepo('1.2.0');
    try {
      fs.writeFileSync(
        path.join(tmp, 'skills', 'archlens', 'SKILL.md'),
        `---\nname: archlens\ndescription: Governance skill\nversion: 1.0.0\nmetadata:\n  author: fmatar\n  version: "1.0.0"\n---\n# Archlens\n`
      );
      fs.mkdirSync(path.join(tmp, 'cli', 'skills', 'stale-skill'), { recursive: true });
      fs.writeFileSync(path.join(tmp, 'cli', 'skills', 'stale-skill', 'SKILL.md'), 'stale');

      const res = syncSkills({ repoRoot: tmp });
      assert.equal(res.version, '1.2.0');
      assert.deepEqual(res.skills, ['archlens']);
      assert.equal(fs.existsSync(path.join(tmp, 'cli', 'skills', 'stale-skill')), false);
      assert.equal(fs.existsSync(path.join(tmp, 'cli', 'skills', 'archlens', 'references', 'guide.md')), true);

      const syncedMd = fs.readFileSync(path.join(tmp, 'cli', 'skills', 'archlens', 'SKILL.md'), 'utf8');
      assert.match(syncedMd, /version: 1\.2\.0/);
      assert.match(syncedMd, /version: "1\.2\.0"/);

      const check = checkSkillsSync({ repoRoot: tmp });
      assert.equal(check.ok, true);
      assert.deepEqual(check.errors, []);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it('validates all failure modes in checkSkillsSync and runSyncSkillsCli', () => {
    const emptyTmp = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-empty-sync-'));
    try {
      // Missing cli/package.json
      assert.equal(checkSkillsSync({ repoRoot: emptyTmp }).ok, false);

      // --if-repo exits 0 cleanly when skills/ does not exist (e.g. standalone cli tarball)
      let exitCode = null;
      runSyncSkillsCli(['--if-repo'], { repoRoot: emptyTmp, exit: (c) => { exitCode = c; } });
      assert.equal(exitCode, 0);

      // Missing root skills/ directory
      fs.mkdirSync(path.join(emptyTmp, 'cli'), { recursive: true });
      fs.writeFileSync(path.join(emptyTmp, 'cli', 'package.json'), JSON.stringify({ version: '1.1.1', files: [] }));
      fs.writeFileSync(path.join(emptyTmp, 'package.json'), JSON.stringify({ version: '2.0.0' }));
      const noSkillsCheck = checkSkillsSync({ repoRoot: emptyTmp });
      assert.equal(noSkillsCheck.ok, false);
      assert.ok(noSkillsCheck.errors.some((e) => e.includes('Version mismatch')));
      assert.ok(noSkillsCheck.errors.some((e) => e.includes('"skills"')));
      assert.ok(noSkillsCheck.errors.some((e) => e.includes('Missing root skills/')));
    } finally {
      fs.rmSync(emptyTmp, { recursive: true, force: true });
    }

    const tmp = createFixtureRepo('1.1.1');
    try {
      // Run default sync via CLI entry
      let code = null;
      runSyncSkillsCli([], { repoRoot: tmp, exit: (c) => { code = c; } });
      assert.equal(code, 0);

      runSyncSkillsCli(['--check'], { repoRoot: tmp, exit: (c) => { code = c; } });
      assert.equal(code, 0);

      // Add malformed skill folders and drifted manifests
      fs.mkdirSync(path.join(tmp, 'skills', 'Bad_Skill'), { recursive: true });
      fs.mkdirSync(path.join(tmp, 'skills', 'no-frontmatter'), { recursive: true });
      fs.writeFileSync(path.join(tmp, 'skills', 'no-frontmatter', 'SKILL.md'), '# Missing YAML\n');
      fs.mkdirSync(path.join(tmp, 'skills', 'wrong-meta'), { recursive: true });
      fs.writeFileSync(
        path.join(tmp, 'skills', 'wrong-meta', 'SKILL.md'),
        '---\nname: other-name\ndescription: ""\nversion: 9.9.9\nmetadata:\n  version: "8.8.8"\n---\n'
      );
      fs.writeFileSync(path.join(tmp, '.claude-plugin', 'plugin.json'), '{bad json');

      const badCheck = checkSkillsSync({ repoRoot: tmp });
      assert.equal(badCheck.ok, false);
      assert.ok(badCheck.errors.some((e) => e.includes('Invalid skill folder name')));
      assert.ok(badCheck.errors.some((e) => e.includes('Missing SKILL.md')));
      assert.ok(badCheck.errors.some((e) => e.includes('Missing YAML frontmatter')));
      assert.ok(badCheck.errors.some((e) => e.includes('frontmatter name')));
      assert.ok(badCheck.errors.some((e) => e.includes('description')));
      assert.ok(badCheck.errors.some((e) => e.includes('version "9.9.9"')));
      assert.ok(badCheck.errors.some((e) => e.includes('metadata.version "8.8.8"')));
      assert.ok(badCheck.errors.some((e) => e.includes('Invalid JSON')));

      runSyncSkillsCli(['--check'], { repoRoot: tmp, exit: (c) => { code = c; } });
      assert.equal(code, 1);

      // Missing plugin.json and content-only mismatch in cli/skills
      const tmp2 = createFixtureRepo('1.1.1');
      try {
        syncSkills({ repoRoot: tmp2 });
        fs.unlinkSync(path.join(tmp2, '.claude-plugin', 'plugin.json'));
        fs.writeFileSync(path.join(tmp2, 'cli', 'skills', 'archlens', 'SKILL.md'), 'tampered');
        const check2 = checkSkillsSync({ repoRoot: tmp2 });
        assert.equal(check2.ok, false);
        assert.ok(check2.errors.some((e) => e.includes('Missing .claude-plugin/plugin.json')));
        assert.ok(check2.errors.some((e) => e.includes('out of sync')));
      } finally {
        fs.rmSync(tmp2, { recursive: true, force: true });
      }
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it('setVersion synchronizes all manifests, .mcp.json, skills/, cli/skills/, and plugin.json', () => {
    assert.throws(() => setVersion({}), /Usage: set-version\.js/);

    // Dry run on live repository mutates nothing
    const dryRes = setVersion({ newVersion: '1.1.1', dryRun: true, rootDir: REPO_ROOT });
    assert.equal(dryRes.dryRun, true);
    assert.equal(dryRes.updatedCount, 0);

    // Full execution on isolated fixture repository
    const tmp = createFixtureRepo('1.1.1');
    try {
      // Missing required files throws
      assert.throws(() => setVersion({ newVersion: '1.2.0', rootDir: tmp }), /File not found/);

      const pomXml = '<project><artifactId>archlens</artifactId>\n  <version>1.1.1</version></project>\n';
      for (const rel of ['pom.xml', 'backend/pom.xml', 'frontend/pom.xml', 'cli/pom.xml']) {
        const p = path.join(tmp, rel);
        fs.mkdirSync(path.dirname(p), { recursive: true });
        fs.writeFileSync(p, pomXml);
      }
      fs.writeFileSync(
        path.join(tmp, 'frontend/package.json'),
        JSON.stringify({ name: 'archlens-frontend', version: '1.1.1' }, null, 2)
      );
      const appProps = path.join(tmp, 'backend/src/main/resources/application.properties');
      fs.mkdirSync(path.dirname(appProps), { recursive: true });
      fs.writeFileSync(appProps, 'quarkus.mcp.server.server-info.version=${quarkus.application.version:1.1.1}\n');

      const verJava = path.join(tmp, 'backend/src/main/java/io/slixes/archlens/delivery/rest/VersionResource.java');
      fs.mkdirSync(path.dirname(verJava), { recursive: true });
      fs.writeFileSync(verJava, 'private static final String DEFAULT_VERSION = "1.1.1";\n');

      const mcpServerJs = path.join(tmp, 'cli/src/mcp-server.js');
      fs.mkdirSync(path.dirname(mcpServerJs), { recursive: true });
      fs.writeFileSync(mcpServerJs, "function getPackageVersion() {\n  return '1.1.1';\n}\n");

      const smokeJs = path.join(tmp, 'scripts/smoke-test.js');
      fs.mkdirSync(path.dirname(smokeJs), { recursive: true });
      fs.writeFileSync(smokeJs, "export async function runHttpSmokeChecks(serverUrl, expectedVersion = '1.1.1') {}\n");

      fs.writeFileSync(
        path.join(tmp, '.mcp.json'),
        JSON.stringify({ mcpServers: { archlens: { args: ['-y', '@fmatar/archlens-skill@1.1.1', 'mcp'] } } }, null, 2)
      );

      const bumped = setVersion({ newVersion: '1.3.0', rootDir: tmp });
      assert.equal(bumped.updatedCount, 12);
      assert.equal(bumped.synced.version, '1.3.0');

      const checkAfterBump = checkSkillsSync({ repoRoot: tmp });
      assert.equal(checkAfterBump.ok, true, checkAfterBump.errors.join('\n'));
      assert.match(fs.readFileSync(path.join(tmp, '.mcp.json'), 'utf8'), /@fmatar\/archlens-skill@1\.3\.0/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it('enforces live repository parity across skills/, cli/skills/, package.json, and .claude-plugin/plugin.json', () => {
    const check = checkSkillsSync({ repoRoot: REPO_ROOT });
    assert.equal(check.ok, true, `Live repo skill governance errors:\n${check.errors.join('\n')}`);
  });

  it('enforces CI and release workflow guardrails so skills can never be skipped on release', () => {
    const releaseYml = fs.readFileSync(path.join(REPO_ROOT, '.github', 'workflows', 'release.yml'), 'utf8');
    const devReleaseYml = fs.readFileSync(path.join(REPO_ROOT, '.github', 'workflows', 'dev-release.yml'), 'utf8');
    const ciYml = fs.readFileSync(path.join(REPO_ROOT, '.github', 'workflows', 'ci.yml'), 'utf8');

    assert.doesNotMatch(releaseYml, /['"]?\*\*\.md['"]?/, 'release.yml must not ignore **.md (masks skills/**/SKILL.md)');
    assert.doesNotMatch(devReleaseYml, /['"]?\*\*\.md['"]?/, 'dev-release.yml must not ignore **.md (masks skills/**/SKILL.md)');

    assert.match(ciYml, /skills:check|sync-skills\.js --check/);
    assert.match(releaseYml, /sync-skills\.js --check/);
    assert.match(releaseYml, /needs:\s*\[?\s*npm-release\s*\]?/);
    assert.match(releaseYml, /needs:\s*\[?\s*docker-release\s*\]?/);
    assert.match(releaseYml, /package\/skills\/archlens\/SKILL\.md/);
  });
});
