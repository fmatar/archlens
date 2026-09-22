#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const newVersion = args.find((arg) => !arg.startsWith('--'));

if (!newVersion) {
  console.error('Usage: set-version.js <new-version> [--dry-run]');
  process.exit(1);
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
  }
];

console.log(`Setting version to: ${newVersion} ${dryRun ? '(DRY RUN)' : ''}`);

let updatedCount = 0;
for (const file of files) {
  const relPath = path.relative(rootDir, file.path);
  if (!fs.existsSync(file.path)) {
    console.error(`File not found: ${relPath}`);
    process.exit(1);
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

console.log(`Version synchronization complete. ${updatedCount} file(s) processed.`);
