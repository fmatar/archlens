#!/usr/bin/env node
/**
 * Archlens Agent Skills Governance & Synchronization Engine
 * Keeps root `skills/` (npx skills / skills.sh standard), `cli/skills/` (@fmatar/archlens-skill),
 * and `.claude-plugin/plugin.json` synchronized and version-locked on every pack and release.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DEFAULT_REPO_ROOT = path.resolve(__dirname, '..');

export function parseSkillFrontmatter(markdown) {
  const match = markdown.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!match) return null;

  const lines = match[1].split(/\r?\n/);
  const result = { metadata: {} };
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim() || line.trim().startsWith('#')) {
      i++;
      continue;
    }

    const kv = line.match(/^([a-zA-Z0-9_-]+):\s*(.*)$/);
    if (!kv) {
      i++;
      continue;
    }

    const [, key, rawVal] = kv;
    const val = rawVal.trim();

    if (key === 'metadata' && !val) {
      i++;
      while (i < lines.length && /^\s{2,}[a-zA-Z0-9_-]+:/.test(lines[i])) {
        const subKv = lines[i].match(/^\s+([a-zA-Z0-9_-]+):\s*(.*)$/);
        if (subKv) {
          result.metadata[subKv[1]] = subKv[2].trim().replace(/^["']|["']$/g, '');
        }
        i++;
      }
      continue;
    }

    if (val === '>-' || val === '>' || val === '|' || val === '|-') {
      i++;
      const block = [];
      while (i < lines.length && /^\s{2,}/.test(lines[i])) {
        block.push(lines[i].trim());
        i++;
      }
      result[key] = block.join(val.startsWith('>') ? ' ' : '\n');
      continue;
    }

    result[key] = val.replace(/^["']|["']$/g, '');
    i++;
  }

  return result;
}

export function stampSkillVersion(markdown, version) {
  let updated = markdown.replace(
    /^(---\r?\n[\s\S]*?^version:\s*)[^\r\n]+/m,
    `$1${version}`
  );
  updated = updated.replace(
    /^(---\r?\n[\s\S]*?^\s+version:\s*)[^\r\n]+/m,
    `$1"${version}"`
  );
  return updated;
}

function listRelativeFiles(baseDir) {
  if (!fs.existsSync(baseDir)) return [];
  const results = [];
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name === '.DS_Store') continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else {
        results.push(path.relative(baseDir, full));
      }
    }
  }
  walk(baseDir);
  return results.sort();
}

export function syncSkills({ repoRoot = DEFAULT_REPO_ROOT, version = null } = {}) {
  const cliPkgPath = path.join(repoRoot, 'cli', 'package.json');
  const cliPkg = JSON.parse(fs.readFileSync(cliPkgPath, 'utf8'));
  const targetVersion = version || cliPkg.version;

  const rootSkillsDir = path.join(repoRoot, 'skills');
  const cliSkillsDir = path.join(repoRoot, 'cli', 'skills');

  const skillDirs = fs
    .readdirSync(rootSkillsDir, { withFileTypes: true })
    .filter((d) => d.isDirectory())
    .map((d) => d.name)
    .sort();

  for (const skillName of skillDirs) {
    const skillMdPath = path.join(rootSkillsDir, skillName, 'SKILL.md');
    if (fs.existsSync(skillMdPath)) {
      const content = fs.readFileSync(skillMdPath, 'utf8');
      const stamped = stampSkillVersion(content, targetVersion);
      if (stamped !== content) {
        fs.writeFileSync(skillMdPath, stamped, 'utf8');
      }
    }
  }

  // Mirror root skills/ -> cli/skills/ cleanly
  fs.rmSync(cliSkillsDir, { recursive: true, force: true });
  fs.cpSync(rootSkillsDir, cliSkillsDir, {
    recursive: true,
    filter: (src) => path.basename(src) !== '.DS_Store'
  });

  // Ensure .claude-plugin/plugin.json exposes Archlens skills at matching version
  const pluginManifestPath = path.join(repoRoot, '.claude-plugin', 'plugin.json');
  const pluginManifest = {
    name: 'archlens',
    version: targetVersion,
    description:
      'Archlens — Dynamic Clean Architecture Workbench, AST governance engine, DIP synthesizer, and MCP companion skills',
    skills: './skills',
    mcpServers: './.mcp.json'
  };
  fs.mkdirSync(path.dirname(pluginManifestPath), { recursive: true });
  fs.writeFileSync(pluginManifestPath, JSON.stringify(pluginManifest, null, 2) + '\n', 'utf8');

  return { version: targetVersion, skills: skillDirs };
}

export function checkSkillsSync({ repoRoot = DEFAULT_REPO_ROOT } = {}) {
  const errors = [];
  const rootPkgPath = path.join(repoRoot, 'package.json');
  const cliPkgPath = path.join(repoRoot, 'cli', 'package.json');

  if (!fs.existsSync(cliPkgPath)) {
    return { ok: false, errors: ['Missing cli/package.json'] };
  }

  const cliPkg = JSON.parse(fs.readFileSync(cliPkgPath, 'utf8'));
  const expectedVersion = cliPkg.version;
  // Base SemVer handles canary prereleases like 1.1.1-dev.42 where SKILL.md holds 1.1.1
  const expectedBaseVersion = expectedVersion.split('-')[0];

  if (fs.existsSync(rootPkgPath)) {
    const rootPkg = JSON.parse(fs.readFileSync(rootPkgPath, 'utf8'));
    if (rootPkg.version.split('-')[0] !== expectedBaseVersion) {
      errors.push(
        `Version mismatch: package.json (${rootPkg.version}) != cli/package.json (${expectedVersion})`
      );
    }
  }

  if (!Array.isArray(cliPkg.files) || !cliPkg.files.includes('skills')) {
    errors.push('cli/package.json "files" array must include "skills"');
  }

  const rootSkillsDir = path.join(repoRoot, 'skills');
  const cliSkillsDir = path.join(repoRoot, 'cli', 'skills');

  if (!fs.existsSync(rootSkillsDir)) {
    errors.push('Missing root skills/ directory');
    return { ok: false, errors };
  }

  const skillDirs = fs
    .readdirSync(rootSkillsDir, { withFileTypes: true })
    .filter((d) => d.isDirectory())
    .map((d) => d.name)
    .sort();

  if (skillDirs.length === 0) {
    errors.push('No skill directories found under skills/');
  }

  for (const skillName of skillDirs) {
    if (!/^[a-z0-9-]+$/.test(skillName) || skillName.length > 64) {
      errors.push(`Invalid skill folder name "${skillName}" (must be lowercase hyphenated <= 64 chars)`);
    }
    const skillMdPath = path.join(rootSkillsDir, skillName, 'SKILL.md');
    if (!fs.existsSync(skillMdPath)) {
      errors.push(`Missing SKILL.md in skills/${skillName}`);
      continue;
    }
    const content = fs.readFileSync(skillMdPath, 'utf8');
    const fm = parseSkillFrontmatter(content);
    if (!fm) {
      errors.push(`Missing YAML frontmatter in skills/${skillName}/SKILL.md`);
      continue;
    }
    if (fm.name !== skillName) {
      errors.push(`skills/${skillName}/SKILL.md frontmatter name "${fm.name}" != folder "${skillName}"`);
    }
    if (!fm.description || fm.description.length > 1024) {
      errors.push(`skills/${skillName}/SKILL.md description must be 1..1024 characters`);
    }
    if (fm.version !== expectedVersion && fm.version !== expectedBaseVersion) {
      errors.push(
        `skills/${skillName}/SKILL.md version "${fm.version}" != expected "${expectedBaseVersion}"`
      );
    }
    if (
      fm.metadata?.version &&
      fm.metadata.version !== expectedVersion &&
      fm.metadata.version !== expectedBaseVersion
    ) {
      errors.push(
        `skills/${skillName}/SKILL.md metadata.version "${fm.metadata.version}" != expected "${expectedBaseVersion}"`
      );
    }
  }

  // Verify byte-for-byte parity between skills/ and cli/skills/
  const rootFiles = listRelativeFiles(rootSkillsDir);
  const cliFiles = listRelativeFiles(cliSkillsDir);
  if (JSON.stringify(rootFiles) !== JSON.stringify(cliFiles)) {
    errors.push(
      `cli/skills file list (${cliFiles.join(', ')}) differs from skills/ (${rootFiles.join(', ')})`
    );
  } else {
    for (const rel of rootFiles) {
      const a = fs.readFileSync(path.join(rootSkillsDir, rel));
      const b = fs.readFileSync(path.join(cliSkillsDir, rel));
      if (!a.equals(b)) {
        errors.push(`cli/skills/${rel} is out of sync with skills/${rel}`);
      }
    }
  }

  // Verify .claude-plugin/plugin.json points to Archlens skills (npx skills manifest discovery)
  const pluginManifestPath = path.join(repoRoot, '.claude-plugin', 'plugin.json');
  if (!fs.existsSync(pluginManifestPath)) {
    errors.push('Missing .claude-plugin/plugin.json for npx skills & Claude plugin discovery');
  } else {
    try {
      const plugin = JSON.parse(fs.readFileSync(pluginManifestPath, 'utf8'));
      if (plugin.name !== 'archlens') {
        errors.push(`.claude-plugin/plugin.json name "${plugin.name}" must be "archlens"`);
      }
      if (plugin.skills !== './skills') {
        errors.push(`.claude-plugin/plugin.json skills "${plugin.skills}" must be "./skills"`);
      }
      if (plugin.version !== expectedVersion && plugin.version !== expectedBaseVersion) {
        errors.push(
          `.claude-plugin/plugin.json version "${plugin.version}" != expected "${expectedBaseVersion}"`
        );
      }
    } catch (err) {
      errors.push(`Invalid JSON in .claude-plugin/plugin.json: ${err.message}`);
    }
  }

  return { ok: errors.length === 0, errors, version: expectedVersion, skills: skillDirs };
}

export function runSyncSkillsCli(
  args = process.argv.slice(2),
  { repoRoot = DEFAULT_REPO_ROOT, exit = (code) => process.exit(code) } = {}
) {
  const ifRepo = args.includes('--if-repo');
  const checkOnly = args.includes('--check');

  if (ifRepo && !fs.existsSync(path.join(repoRoot, 'skills'))) {
    return exit(0);
  }

  if (checkOnly) {
    const res = checkSkillsSync({ repoRoot });
    if (!res.ok) {
      console.error('❌ Skills governance check failed:');
      for (const err of res.errors) console.error(`  - ${err}`);
      return exit(1);
    }
    console.log(`✅ Skills governance verified (${res.skills.join(', ')} @ v${res.version}).`);
    return exit(0);
  }

  const synced = syncSkills({ repoRoot });
  const verified = checkSkillsSync({ repoRoot });
  if (!verified.ok) {
    console.error('❌ Post-sync verification failed:');
    for (const err of verified.errors) console.error(`  - ${err}`);
    return exit(1);
  }
  console.log(`✅ Synchronized ${synced.skills.length} skills to cli/skills/ @ v${synced.version}.`);
  return exit(0);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  runSyncSkillsCli();
}
