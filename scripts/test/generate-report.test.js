import { describe, it } from 'node:test';
import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {
  parseJaCoCoCsv,
  parseSurefireReports,
  parseXmlCount,
  parseFrontendCoverage,
  parseGitProperties,
  parseSbom,
  generateReportHtml,
  generateSummaryMarkdown
} from '../generate-report.js';

describe('generate-report telemetry engine', () => {
  it('should parse JaCoCo CSV metrics accurately', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'jacoco-test-'));
    const csvPath = path.join(tempDir, 'jacoco.csv');
    const csvContent = `GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED
Archlens,com.test.pkg,TestClass,10,90,5,15,2,8,1,5,0,2
`;
    fs.writeFileSync(csvPath, csvContent, 'utf8');

    const result = parseJaCoCoCsv(csvPath);
    assert.strictEqual(result.lineCovered, 8);
    assert.strictEqual(result.lineTotal, 10);
    assert.strictEqual(result.linePct, 80);
    assert.strictEqual(result.branchCovered, 15);
    assert.strictEqual(result.branchTotal, 20);
    assert.strictEqual(result.branchPct, 75);
    assert.strictEqual(result.packages.length, 1);
    assert.strictEqual(result.packages[0].name, 'com.test.pkg');
  });

  it('should return null if JaCoCo CSV does not exist', () => {
    const result = parseJaCoCoCsv('/nonexistent/path/jacoco.csv');
    assert.strictEqual(result, null);
  });

  it('should count XML tag occurrences accurately', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'xml-count-test-'));
    const xmlPath = path.join(tempDir, 'test.xml');
    const xmlContent = `<pmd><violation line="10"/><violation line="25"/></pmd>`;
    fs.writeFileSync(xmlPath, xmlContent, 'utf8');

    const count = parseXmlCount(xmlPath, 'violation');
    assert.strictEqual(count, 2);

    const zeroCount = parseXmlCount(xmlPath, 'missingTag');
    assert.strictEqual(zeroCount, 0);
  });

  it('should parse frontend coverage JSON metrics', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'fe-cov-test-'));
    const jsonPath = path.join(tempDir, 'coverage.json');
    const data = {
      '/app/frontend/src/file1.ts': {
        s: { 0: 1, 1: 1, 2: 0 },
        b: { 0: [1, 0] },
        f: { 0: 1 }
      }
    };
    fs.writeFileSync(jsonPath, JSON.stringify(data), 'utf8');

    const result = parseFrontendCoverage(jsonPath);
    assert.strictEqual(result.statementsPct, 66.7);
    assert.strictEqual(result.branchesPct, 50);
    assert.strictEqual(result.functionsPct, 100);
    assert.strictEqual(result.files.length, 1);
  });

  it('should parse git properties file', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'git-props-test-'));
    const propsPath = path.join(tempDir, 'git.properties');
    const content = `git.build.version=1.2.3\ngit.commit.id.abbrev=abcdef1\ngit.branch=feature-test\n`;
    fs.writeFileSync(propsPath, content, 'utf8');

    const result = parseGitProperties(propsPath);
    assert.strictEqual(result.version, '1.2.3');
    assert.strictEqual(result.commitId, 'abcdef1');
    assert.strictEqual(result.branch, 'feature-test');
  });

  it('should generate well-formed HTML and Markdown outputs', () => {
    const mockData = {
      backend: { linePct: 85, branchPct: 65, lineCovered: 850, lineTotal: 1000, packages: [] },
      backendTests: { tests: 35, failures: 0, errors: 0, skipped: 0 },
      quality: { pmd: 0, cpd: 0, spotbugs: 0 },
      frontend: { statementsPct: 70, branchesPct: 50, functionsPct: 80, files: [] },
      git: { version: '0.0.1-Alpha-03', branch: 'develop', commitId: '1234567', buildTime: '2026-09-22' },
      sbom: { componentCount: 120, specVersion: '1.5' },
      totalTests: 67
    };

    const html = generateReportHtml(mockData);
    assert.ok(html.includes('Archlens Monorepo'));
    assert.ok(html.includes('v0.0.1-Alpha-03'));
    assert.ok(html.includes('85%'));

    const md = generateSummaryMarkdown(mockData);
    assert.ok(md.includes('# 📊 Archlens Monorepo Full-Stack Health & Coverage Report'));
    assert.ok(md.includes('85%'));
    assert.ok(md.includes('67 tests passed'));
  });
});
