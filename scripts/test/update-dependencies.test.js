import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  isPreRelease,
  filterSafeVersions,
  parseMavenMetadataVersions,
  parseNpmVersions,
  updatePomProperty,
  updatePackageJsonDependency
} from '../update-dependencies.js';

describe('Dependency Governance Engine', () => {
  describe('isPreRelease', () => {
    it('should flag pre-release and candidate qualifiers as unsafe', () => {
      assert.strictEqual(isPreRelease('3.40.0.CR1'), true);
      assert.strictEqual(isPreRelease('3.40.0-Beta1'), true);
      assert.strictEqual(isPreRelease('2.1.0-alpha1'), true);
      assert.strictEqual(isPreRelease('1.0.0-rc.1'), true);
      assert.strictEqual(isPreRelease('2.0.0-preview'), true);
      assert.strictEqual(isPreRelease('0.0.1-SNAPSHOT'), true);
      assert.strictEqual(isPreRelease('3.0.0-M1'), true);
      assert.strictEqual(isPreRelease('3.7.0-dev.f9f6b2'), true);
      assert.strictEqual(isPreRelease('7.1.0-dev.20260925.1'), true);
      assert.strictEqual(isPreRelease('1.0.0-canary.5'), true);
      assert.strictEqual(isPreRelease('2.0.0-nightly'), true);
      assert.strictEqual(isPreRelease('3.0.0-next.1'), true);
    });

    it('should identify general availability stable releases as safe', () => {
      assert.strictEqual(isPreRelease('3.39.5'), false);
      assert.strictEqual(isPreRelease('2.0.1'), false);
      assert.strictEqual(isPreRelease('8.3.1'), false);
      assert.strictEqual(isPreRelease('1.48.0'), false);
      assert.strictEqual(isPreRelease('7.0.2'), false);
    });
  });

  describe('filterSafeVersions', () => {
    const versions = [
      '3.38.0',
      '3.39.4',
      '3.39.5',
      '3.40.0.CR1',
      '4.0.0.Alpha1',
      '4.0.0'
    ];

    it('should select highest stable minor/patch without upgrading major by default', () => {
      const selected = filterSafeVersions(versions, '3.39.4', { allowMajor: false });
      assert.strictEqual(selected, '3.39.5');
    });

    it('should allow major version upgrade when allowMajor is true', () => {
      const selected = filterSafeVersions(versions, '3.39.4', { allowMajor: true });
      assert.strictEqual(selected, '4.0.0');
    });

    it('should return null when currently on latest available version', () => {
      const selected = filterSafeVersions(versions, '3.39.5', { allowMajor: false });
      assert.strictEqual(selected, null);
    });
  });

  describe('parseMavenMetadataVersions', () => {
    it('should extract versions from maven metadata XML', () => {
      const xml = `<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <versioning>
    <latest>3.39.5</latest>
    <release>3.39.5</release>
    <versions>
      <version>3.39.3</version>
      <version>3.39.4</version>
      <version>3.39.5</version>
      <version>3.40.0.CR1</version>
    </versions>
  </versioning>
</metadata>`;
      const extracted = parseMavenMetadataVersions(xml);
      assert.deepStrictEqual(extracted, ['3.39.3', '3.39.4', '3.39.5', '3.40.0.CR1']);
    });
  });

  describe('parseNpmVersions', () => {
    it('should parse json array string or array into string array', () => {
      const input = JSON.stringify(['1.0.0', '1.1.0', '2.0.0-rc1']);
      const parsed = parseNpmVersions(input);
      assert.deepStrictEqual(parsed, ['1.0.0', '1.1.0', '2.0.0-rc1']);
    });
  });

  describe('updatePomProperty', () => {
    it('should replace xml property value cleanly', () => {
      const xml = '  <quarkus.platform.version>3.39.4</quarkus.platform.version>';
      const updated = updatePomProperty(xml, 'quarkus.platform.version', '3.39.5');
      assert.strictEqual(updated, '  <quarkus.platform.version>3.39.5</quarkus.platform.version>');
    });
  });

  describe('updatePackageJsonDependency', () => {
    it('should preserve caret prefix when updating dependencies', () => {
      const pkg = JSON.stringify({
        dependencies: {
          '@lucide/svelte': '^1.47.0'
        }
      }, null, 2);

      const updated = updatePackageJsonDependency(pkg, '@lucide/svelte', '1.48.0');
      const parsed = JSON.parse(updated);
      assert.strictEqual(parsed.dependencies['@lucide/svelte'], '^1.48.0');
    });

    it('should preserve tilde prefix when updating dependencies', () => {
      const pkg = JSON.stringify({
        devDependencies: {
          'typescript': '~6.0.2'
        }
      }, null, 2);

      const updated = updatePackageJsonDependency(pkg, 'typescript', '6.0.3');
      const parsed = JSON.parse(updated);
      assert.strictEqual(parsed.devDependencies['typescript'], '~6.0.3');
    });
  });
});
