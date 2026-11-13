#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { syncSkills } from './sync-skills.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DEFAULT_ROOT_DIR = path.resolve(__dirname, '..');

export function setVersion({ newVersion, dryRun = false, rootDir = DEFAULT_ROOT_DIR } = {}) {
  if (!newVersion) {
    throw new Error('Usage: set-version.js <new-version> [--dry-run]');
  }

  const files = [
    {
      path: path.join(rootDir, 'pom.xml'),
      update: (content) =>
        content.replace(
          /(<artifactId>archlens<\/artifactId>\s*<version>)[^<]+(<\/version>)/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, 'backend/pom.xml'),
      update: (content) =>
        content.replace(
          /(<artifactId>archlens<\/artifactId>\s*<version>)[^<]+(<\/version>)/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, 'frontend/pom.xml'),
      update: (content) =>
        content.replace(
          /(<artifactId>archlens<\/artifactId>\s*<version>)[^<]+(<\/version>)/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, 'cli/pom.xml'),
      update: (content) =>
        content.replace(
          /(<artifactId>archlens<\/artifactId>\s*<version>)[^<]+(<\/version>)/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, 'frontend/package.json'),
      update: (content) =>
        content.replace(
          /("name":\s*"archlens-frontend",[\s\S]*?"version":\s*")[^"]+(")/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, 'cli/package.json'),
      update: (content) =>
        content.replace(
          /("name":\s*"@fmatar\/archlens-skill",[\s\S]*?"version":\s*")[^"]+(")/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, 'package.json'),
      update: (content) =>
        content.replace(
          /("name":\s*"@fmatar\/archlens",[\s\S]*?"version":\s*")[^"]+(")/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, 'backend/src/main/resources/application.properties'),
      update: (content) =>
        content.replace(
          /(quarkus\.mcp\.server\.server-info\.version=\$\{quarkus\.application\.version:)[^}]+(\})/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, 'backend/src/main/java/io/slixes/archlens/delivery/rest/VersionResource.java'),
      update: (content) =>
        content.replace(
          /(private static final String DEFAULT_VERSION = ")[^"]+(";)/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, 'cli/src/mcp-server.js'),
      update: (content) =>
        content.replace(
          /(function getPackageVersion\(\)[\s\S]*?return ')[^']+(';)/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, 'scripts/smoke-test.js'),
      update: (content) =>
        content.replace(
          /(export async function runHttpSmokeChecks\(serverUrl, expectedVersion = ')[^']+(')/,
          `$1${newVersion}$2`
        )
    },
    {
      path: path.join(rootDir, '.mcp.json'),
      update: (content) =>
        content.replace(
          /("@fmatar\/archlens-skill@)[^"]+(")/,
          `$1${newVersion}$2`
        )
    }
  ];

  console.log(`Setting version to: ${newVersion} ${dryRun ? '(DRY RUN)' : ''}`);

  let updatedCount = 0;
  for (const file of files) {
    const relPath = path.relative(rootDir, file.path);
    if (!fs.existsSync(file.path)) {
      throw new Error(`File not found: ${relPath}`);
    }
    const oldContent = fs.readFileSync(file.path, 'utf8');
    const newContent = file.update(oldContent);
    if (oldContent === newContent) {
      console.warn(`[SKIP/SAME] ${relPath}`);
    } else {
      console.log(`[UPDATE] ${relPath}`);
      updatedCount++;
      if (!dryRun) {
        fs.writeFileSync(file.path, newContent, 'utf8');
      }
    }
  }

  let synced = null;
  if (!dryRun) {
    synced = syncSkills({ repoRoot: rootDir, version: newVersion });
    console.log(`[SYNC] skills/ -> cli/skills/ & .claude-plugin/plugin.json (${synced.skills.join(', ')} @ ${synced.version})`);
  }

  console.log(`Version synchronization complete. ${updatedCount} file(s) processed.`);
  return { updatedCount, synced, dryRun };
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const args = process.argv.slice(2);
  const dryRun = args.includes('--dry-run');
  const newVersion = args.find((arg) => !arg.startsWith('--'));
  try {
    setVersion({ newVersion, dryRun });
  } catch (err) {
    console.error(err.message);
    process.exit(1);
  }
}
