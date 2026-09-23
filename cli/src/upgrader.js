/**
 * Archlens Configuration & Mailbox Upgrader
 * Migrates legacy .uml-viewer configurations and mailboxes to the modern .archlens layout.
 * Zero external dependencies.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import { COMPANION_PROTOCOL_HEADER, getCompanionProtocolBlock } from './installer.js';

export function upgradeProject(projectRoot, options = {}) {
  const root = path.resolve(projectRoot);
  const dryRun = Boolean(options.dryRun);
  const legacyDir = path.join(root, '.uml-viewer');
  const primaryDir = path.join(root, '.archlens');

  if (!fs.existsSync(root)) {
    throw new Error(`Target directory does not exist: ${root}`);
  }

  const legacyExists = fs.existsSync(legacyDir);
  const primaryExists = fs.existsSync(primaryDir);

  if (!legacyExists && !primaryExists) {
    throw new Error(
      `No Archlens configuration found to upgrade in ${root}. Run init to create a new .archlens configuration.`
    );
  }

  let migrated = false;
  const migratedFiles = [];

  if (legacyExists) {
    if (!primaryExists) {
      if (dryRun) {
        const files = fs.readdirSync(legacyDir);
        migratedFiles.push(...files);
        migrated = true;
      } else {
        try {
          fs.renameSync(legacyDir, primaryDir);
          const files = fs.readdirSync(primaryDir);
          migratedFiles.push(...files);
          migrated = true;
        } catch {
          // Fallback for cross-device mount links
          fs.mkdirSync(primaryDir, { recursive: true });
          const files = fs.readdirSync(legacyDir);
          for (const file of files) {
            const srcFile = path.join(legacyDir, file);
            const dstFile = path.join(primaryDir, file);
            fs.copyFileSync(srcFile, dstFile);
            migratedFiles.push(file);
          }
          fs.rmSync(legacyDir, { recursive: true, force: true });
          migrated = true;
        }
      }
    } else {
      // Both exist: migrate missing files into primaryDir and retire legacyDir
      const files = fs.readdirSync(legacyDir);
      for (const file of files) {
        const srcFile = path.join(legacyDir, file);
        const dstFile = path.join(primaryDir, file);
        if (!fs.existsSync(dstFile)) {
          if (!dryRun) {
            fs.copyFileSync(srcFile, dstFile);
          }
          migratedFiles.push(file);
        }
      }
      if (!dryRun) {
        fs.rmSync(legacyDir, { recursive: true, force: true });
      }
      migrated = true;
    }
  }

  // Update companion files: CLAUDE.md and AGENTS.md
  const companionFiles = ['CLAUDE.md', 'AGENTS.md'];
  const updatedCompanions = [];

  for (const filename of companionFiles) {
    const filePath = path.join(root, filename);
    if (!fs.existsSync(filePath)) {
      continue;
    }

    const content = fs.readFileSync(filePath, 'utf8');
    let updatedContent = content;
    let modified = false;

    if (content.includes('.uml-viewer')) {
      updatedContent = updatedContent.replaceAll('.uml-viewer', '.archlens');
      modified = true;
    }

    if (!updatedContent.includes(COMPANION_PROTOCOL_HEADER)) {
      const serverUrl = options.serverUrl || 'http://localhost:8088';
      const block = getCompanionProtocolBlock(serverUrl);
      updatedContent = updatedContent.trimEnd() + '\n\n' + block + '\n';
      modified = true;
    }

    if (modified) {
      if (!dryRun) {
        fs.writeFileSync(filePath, updatedContent, 'utf8');
      }
      updatedCompanions.push(filename);
    }
  }

  return {
    targetDir: root,
    legacyDir,
    primaryDir,
    migrated,
    migratedFiles,
    updatedCompanions,
    dryRun
  };
}
