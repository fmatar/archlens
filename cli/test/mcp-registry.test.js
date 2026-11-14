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
  installMcpConfigs,
  resolveDockerHostWorkspace
} from '../src/mcp-registry.js';

test('ARCHLENS_TOOL_SCHEMAS defines 10 Clean Architecture tools', () => {
  const tools = Object.keys(ARCHLENS_TOOL_SCHEMAS);
  assert.equal(tools.length, 10);
  assert.ok(tools.includes('inspectArchitecture'));
  assert.ok(tools.includes('exportLlmDossier'));
  assert.ok(tools.includes('listSnapshots'));
  assert.ok(tools.includes('getSnapshot'));
  assert.ok(tools.includes('synthesizeDipInversion'));
  assert.ok(tools.includes('evaluateFitness'));
  assert.ok(tools.includes('getFitnessHistory'));
  assert.ok(tools.includes('proposeScreamingMigration'));
  assert.ok(tools.includes('getPolicy'));
  assert.ok(tools.includes('syncDipAdr'));

  assert.equal(ARCHLENS_TOOL_SCHEMAS.getSnapshot.parameters.required[0], 'snapshotId');
  assert.deepEqual(ARCHLENS_TOOL_SCHEMAS.synthesizeDipInversion.parameters.required, ['fromClass', 'toClass']);
  assert.deepEqual(ARCHLENS_TOOL_SCHEMAS.syncDipAdr.parameters.required, ['sourceClass', 'targetClass', 'interfacePortName']);
  assert.equal(typeof ARCHLENS_TOOL_SCHEMAS.inspectArchitecture.description, 'string');
});

test('getAntigravityInstructions produces markdown instructions listing all 10 tools', () => {
  const instructions = getAntigravityInstructions();
  assert.ok(instructions.includes('Archlens Clean Architecture MCP Server Instructions'));
  assert.ok(instructions.includes('Domain Core (L0)'));
  assert.ok(instructions.includes('Dependency Inversion Principle'));
  assert.ok(instructions.includes('synthesizeDipInversion'));
  assert.ok(instructions.includes('evaluateFitness'));
  assert.ok(instructions.includes('getFitnessHistory'));
  assert.ok(instructions.includes('proposeScreamingMigration'));
  assert.ok(instructions.includes('getPolicy'));
  assert.ok(instructions.includes('syncDipAdr'));
});

test('getClientConfigLocations returns expected multi-agent client targets', () => {
  const targets = getClientConfigLocations('/test/project', '/mock/home');
  const ids = targets.map((t) => t.id);
  assert.ok(ids.includes('claude-desktop'));
  assert.ok(ids.includes('claude-code'));
  assert.ok(ids.includes('antigravity-global'));
  assert.ok(ids.includes('antigravity-config'));
  assert.ok(ids.includes('gemini-cli'));
  assert.ok(ids.includes('grok'));
  assert.ok(ids.includes('windsurf'));
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

    // Setup Cursor and VS Code directories
    fs.mkdirSync(path.join(tmpProject, '.cursor'), { recursive: true });
    fs.mkdirSync(path.join(tmpProject, '.vscode'), { recursive: true });

    const result = installMcpConfigs({
      path: tmpProject,
      homedir: tmpHome,
      serverUrl: 'http://localhost:8088'
    });

    assert.equal(result.dryRun, false);
    assert.ok(result.configuredClients.length >= 4);

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

    // Verify VS Code config uses top-level "servers" key with type "stdio"
    const vscodeFile = path.join(tmpProject, '.vscode', 'mcp.json');
    assert.ok(fs.existsSync(vscodeFile));
    const vscodeConfig = JSON.parse(fs.readFileSync(vscodeFile, 'utf8'));
    assert.ok(vscodeConfig.servers.archlens);
    assert.equal(vscodeConfig.servers.archlens.type, 'stdio');
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

test('getMcpManifestEntry builds ephemeral docker run -i --rm mcp-stdio config when preferDocker is true', () => {
  const entry = getMcpManifestEntry('http://localhost:8088', {
    preferDocker: true,
    hostWorkspace: '/Users/fady/workspace'
  });

  assert.equal(entry.command, 'docker');
  assert.deepEqual(entry.args, [
    'run',
    '-i',
    '--rm',
    '-e',
    'QUARKUS_PROFILE',
    '-e',
    'ARCHLENS_HOST_WORKSPACE',
    '-v',
    '/Users/fady/workspace:/workspace',
    'ghcr.io/fmatar/archlens:latest'
  ]);
  assert.deepEqual(entry.env, {
    QUARKUS_PROFILE: 'mcp-stdio',
    ARCHLENS_HOST_WORKSPACE: '/Users/fady/workspace'
  });
});

test('installMcpConfigs writes docker run -i --rm manifests for global and local clients when preferDocker is true', () => {
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-docker-home-'));
  const tmpWorkspace = path.join(tmpHome, 'workspace');
  const tmpProject = path.join(tmpWorkspace, 'labs', 'demo-app');
  fs.mkdirSync(tmpProject, { recursive: true });

  try {
    const antigravityDir = path.join(tmpHome, '.gemini', 'antigravity');
    fs.mkdirSync(antigravityDir, { recursive: true });
    fs.mkdirSync(path.join(tmpProject, '.vscode'), { recursive: true });

    const result = installMcpConfigs({
      path: tmpProject,
      homedir: tmpHome,
      preferDocker: true
    });

    assert.equal(result.dryRun, false);

    // Global Antigravity mcp_config.json mounts ~/workspace -> /workspace
    const antigravityConfigFile = path.join(antigravityDir, 'mcp_config.json');
    const agyManifest = JSON.parse(fs.readFileSync(antigravityConfigFile, 'utf8'));
    assert.equal(agyManifest.mcpServers.archlens.command, 'docker');
    assert.ok(agyManifest.mcpServers.archlens.args.includes(`${tmpWorkspace}:/workspace`));
    assert.equal(agyManifest.mcpServers.archlens.env.QUARKUS_PROFILE, 'mcp-stdio');
    assert.equal(agyManifest.mcpServers.archlens.env.ARCHLENS_HOST_WORKSPACE, tmpWorkspace);

    // Local .mcp.json mounts projectRoot -> /workspace
    const claudeCodeFile = path.join(tmpProject, '.mcp.json');
    const localManifest = JSON.parse(fs.readFileSync(claudeCodeFile, 'utf8'));
    assert.equal(localManifest.mcpServers.archlens.command, 'docker');
    assert.ok(localManifest.mcpServers.archlens.args.includes(`${tmpProject}:/workspace`));
    assert.equal(localManifest.mcpServers.archlens.env.ARCHLENS_HOST_WORKSPACE, tmpProject);

    // VS Code servers.archlens includes type: stdio + docker command
    const vscodeFile = path.join(tmpProject, '.vscode', 'mcp.json');
    const vscodeManifest = JSON.parse(fs.readFileSync(vscodeFile, 'utf8'));
    assert.equal(vscodeManifest.servers.archlens.type, 'stdio');
    assert.equal(vscodeManifest.servers.archlens.command, 'docker');
  } finally {
    fs.rmSync(tmpHome, { recursive: true, force: true });
  }
});

test('resolveDockerHostWorkspace mounts projectRoot in global scope when project is outside ~/workspace', () => {
  const externalProject = path.resolve('/opt/external/service');
  const resolved = resolveDockerHostWorkspace({
    scope: 'global',
    homedir: '/Users/alice',
    path: externalProject,
    existsSync: () => true
  });
  assert.equal(resolved, externalProject);
});

test('installMcpConfigs honors scopeOnly filter to restrict updates to global or local manifests', () => {
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-scope-home-'));
  const tmpProject = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-scope-proj-'));

  try {
    const antigravityDir = path.join(tmpHome, '.gemini', 'antigravity');
    fs.mkdirSync(antigravityDir, { recursive: true });
    fs.mkdirSync(path.join(tmpProject, '.vscode'), { recursive: true });

    const globalOnly = installMcpConfigs({
      path: tmpProject,
      homedir: tmpHome,
      scopeOnly: 'global'
    });

    assert.ok(globalOnly.configuredClients.some((c) => c.id === 'antigravity-config'));
    assert.ok(!globalOnly.configuredClients.some((c) => c.id === 'claude-code'));
    assert.ok(!globalOnly.configuredClients.some((c) => c.id === 'vscode'));
    assert.equal(fs.existsSync(path.join(tmpProject, '.mcp.json')), false);
    assert.equal(fs.existsSync(path.join(tmpProject, '.vscode', 'mcp.json')), false);
    assert.equal(fs.existsSync(path.join(antigravityDir, 'mcp_config.json')), true);

    const localOnlyHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-local-home-'));
    try {
      fs.mkdirSync(path.join(localOnlyHome, '.gemini', 'antigravity'), { recursive: true });
      const localOnly = installMcpConfigs({
        path: tmpProject,
        homedir: localOnlyHome,
        scopeOnly: 'local'
      });
      assert.ok(localOnly.configuredClients.some((c) => c.id === 'claude-code'));
      assert.ok(localOnly.configuredClients.some((c) => c.id === 'vscode'));
      assert.ok(!localOnly.configuredClients.some((c) => c.id === 'antigravity-config'));
      assert.equal(fs.existsSync(path.join(localOnlyHome, '.gemini', 'antigravity', 'mcp_config.json')), false);
    } finally {
      fs.rmSync(localOnlyHome, { recursive: true, force: true });
    }
  } finally {
    fs.rmSync(tmpHome, { recursive: true, force: true });
    fs.rmSync(tmpProject, { recursive: true, force: true });
  }
});
