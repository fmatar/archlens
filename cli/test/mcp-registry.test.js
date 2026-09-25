import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import {
  ARCHLENS_TOOL_SCHEMAS,
  getAntigravityInstructions,
  getClientConfigLocations,
  getMcpManifestEntry,
  installMcpConfigs
} from '../src/mcp-registry.js';

test('ARCHLENS_TOOL_SCHEMAS defines 4 core Clean Architecture tools', () => {
  const tools = Object.keys(ARCHLENS_TOOL_SCHEMAS);
  assert.ok(tools.includes('inspectArchitecture'));
  assert.ok(tools.includes('exportLlmDossier'));
  assert.ok(tools.includes('listSnapshots'));
  assert.ok(tools.includes('getSnapshot'));

  assert.equal(ARCHLENS_TOOL_SCHEMAS.getSnapshot.parameters.required[0], 'snapshotId');
  assert.equal(typeof ARCHLENS_TOOL_SCHEMAS.inspectArchitecture.description, 'string');
});

test('getAntigravityInstructions produces markdown instructions', () => {
  const instructions = getAntigravityInstructions();
  assert.ok(instructions.includes('Archlens Clean Architecture MCP Server Instructions'));
  assert.ok(instructions.includes('Domain Core (L0)'));
  assert.ok(instructions.includes('Dependency Inversion Principle'));
});

test('getClientConfigLocations returns expected client targets', () => {
  const targets = getClientConfigLocations('/test/project', '/mock/home');
  const ids = targets.map((t) => t.id);
  assert.ok(ids.includes('claude-desktop'));
  assert.ok(ids.includes('claude-code'));
  assert.ok(ids.includes('antigravity-global'));
  assert.ok(ids.includes('cursor'));
  assert.ok(ids.includes('vscode'));
});

test('installMcpConfigs writes Antigravity schemas and updates client manifests', () => {
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-home-'));
  const tmpProject = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-project-'));

  try {
    // Setup existing parent directories
    const antigravityParent = path.join(tmpHome, '.gemini', 'antigravity', 'mcp');
    fs.mkdirSync(antigravityParent, { recursive: true });

    // Setup existing Claude Desktop config with existing other tool
    const claudeDir = process.platform === 'darwin'
      ? path.join(tmpHome, 'Library', 'Application Support', 'Claude')
      : process.platform === 'win32'
        ? path.join(tmpHome, 'AppData', 'Roaming', 'Claude')
        : path.join(tmpHome, '.config', 'Claude');

    fs.mkdirSync(claudeDir, { recursive: true });
    const claudeConfigFile = path.join(claudeDir, 'claude_desktop_config.json');
    fs.writeFileSync(
      claudeConfigFile,
      JSON.stringify({
        mcpServers: {
          existingServer: { command: 'echo', args: ['hello'] }
        }
      })
    );

    // Setup Cursor directory
    fs.mkdirSync(path.join(tmpProject, '.cursor'), { recursive: true });

    const result = installMcpConfigs({
      path: tmpProject,
      homedir: tmpHome,
      serverUrl: 'http://localhost:8088'
    });

    assert.equal(result.dryRun, false);
    assert.ok(result.configuredClients.length >= 3);

    // Verify Antigravity schemas
    const archlensMcpDir = path.join(antigravityParent, 'archlens');
    assert.ok(fs.existsSync(archlensMcpDir));
    assert.ok(fs.existsSync(path.join(archlensMcpDir, 'inspectArchitecture.json')));
    assert.ok(fs.existsSync(path.join(archlensMcpDir, 'exportLlmDossier.json')));
    assert.ok(fs.existsSync(path.join(archlensMcpDir, 'instructions.md')));

    // Verify Claude Desktop config preserved existing server and added archlens
    const updatedClaudeConfig = JSON.parse(fs.readFileSync(claudeConfigFile, 'utf8'));
    assert.ok(updatedClaudeConfig.mcpServers.existingServer);
    assert.ok(updatedClaudeConfig.mcpServers.archlens);
    assert.equal(updatedClaudeConfig.mcpServers.archlens.command, 'npx');
    assert.deepEqual(updatedClaudeConfig.mcpServers.archlens.args, ['-y', '@fmatar/archlens-skill', 'mcp']);

    // Verify Cursor config
    const cursorFile = path.join(tmpProject, '.cursor', 'mcp.json');
    assert.ok(fs.existsSync(cursorFile));
    const cursorConfig = JSON.parse(fs.readFileSync(cursorFile, 'utf8'));
    assert.ok(cursorConfig.mcpServers.archlens);
  } finally {
    fs.rmSync(tmpHome, { recursive: true, force: true });
    fs.rmSync(tmpProject, { recursive: true, force: true });
  }
});

test('installMcpConfigs respects dryRun option', () => {
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-dry-'));
  const tmpProject = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-proj-'));

  try {
    const antigravityParent = path.join(tmpHome, '.gemini', 'antigravity', 'mcp');
    fs.mkdirSync(antigravityParent, { recursive: true });

    const result = installMcpConfigs({
      path: tmpProject,
      homedir: tmpHome,
      dryRun: true
    });

    assert.equal(result.dryRun, true);
    const archlensMcpDir = path.join(antigravityParent, 'archlens');
    assert.ok(!fs.existsSync(archlensMcpDir));
  } finally {
    fs.rmSync(tmpHome, { recursive: true, force: true });
    fs.rmSync(tmpProject, { recursive: true, force: true });
  }
});
