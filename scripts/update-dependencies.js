#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

export function parseSemver(v) {
  const cleaned = String(v).replace(/^[^0-9]*/, '');
  const parts = cleaned.split('.').map((p) => {
    const num = parseInt(p, 10);
    return Number.isNaN(num) ? 0 : num;
  });
  return {
    raw: v,
    major: parts[0] ?? 0,
    minor: parts[1] ?? 0,
    patch: parts[2] ?? 0
  };
}

export function compareSemver(a, b) {
  const pa = parseSemver(a);
  const pb = parseSemver(b);
  if (pa.major !== pb.major) return pa.major - pb.major;
  if (pa.minor !== pb.minor) return pa.minor - pb.minor;
  if (pa.patch !== pb.patch) return pa.patch - pb.patch;
  return a.localeCompare(b);
}

export function isPreRelease(v) {
  return /(?:alpha|beta|cr\d*|rc\d*|preview|m\d+|snapshot|dev|canary|nightly|next)/i.test(String(v));
}

export function filterSafeVersions(versions, currentVersion, options = {}) {
  const allowMajor = options.allowMajor ?? false;
  const cur = parseSemver(currentVersion);

  const candidates = versions
    .filter((v) => !isPreRelease(v))
    .filter((v) => {
      const parsed = parseSemver(v);
      if (!allowMajor && parsed.major !== cur.major) return false;
      return compareSemver(v, currentVersion) > 0;
    })
    .sort(compareSemver);

  if (candidates.length === 0) return null;
  return candidates[candidates.length - 1];
}

export function parseMavenMetadataVersions(xml) {
  const matches = [...xml.matchAll(/<version>([^<]+)<\/version>/g)];
  return matches.map((m) => m[1]);
}

export function parseNpmVersions(input) {
  if (Array.isArray(input)) return input;
  try {
    const parsed = JSON.parse(input);
    return Array.isArray(parsed) ? parsed : [parsed];
  } catch {
    return [];
  }
}

export function updatePomProperty(pom, prop, val) {
  const re = new RegExp(`(<${prop}>)[^<]+(<\/${prop}>)`, 'g');
  return pom.replace(re, `$1${val}$2`);
}

export function updatePackageJsonDependency(pkg, dep, val) {
  const parsed = typeof pkg === 'string' ? JSON.parse(pkg) : pkg;
  for (const field of ['dependencies', 'devDependencies']) {
    if (parsed[field] && parsed[field][dep]) {
      const cur = parsed[field][dep];
      let prefix = '';
      if (cur.startsWith('^')) prefix = '^';
      else if (cur.startsWith('~')) prefix = '~';
      else if (cur.startsWith('npm:')) {
        const atIdx = cur.lastIndexOf('@');
        if (atIdx > 0) {
          parsed[field][dep] = `${cur.substring(0, atIdx + 1)}${val}`;
          continue;
        }
      }
      parsed[field][dep] = `${prefix}${val}`;
    }
  }
  return JSON.stringify(parsed, null, 2) + '\n';
}

export async function fetchMavenVersions(url) {
  try {
    const res = await fetch(url);
    if (!res.ok) return [];
    const xml = await res.text();
    return parseMavenMetadataVersions(xml);
  } catch {
    return [];
  }
}

export function fetchNpmVersions(pkgName) {
  try {
    const stdout = execSync(`npm view ${pkgName} versions --json`, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore']
    });
    return parseNpmVersions(stdout);
  } catch {
    return [];
  }
}

const MAVEN_REGISTRY = [
  {
    file: 'pom.xml',
    prop: 'quarkus.platform.version',
    name: 'io.quarkus.platform:quarkus-bom',
    url: 'https://repo1.maven.org/maven2/io/quarkus/platform/quarkus-bom/maven-metadata.xml'
  },
  {
    file: 'pom.xml',
    prop: 'javaparser.version',
    name: 'com.github.javaparser:javaparser-core',
    url: 'https://repo1.maven.org/maven2/com/github/javaparser/javaparser-core/maven-metadata.xml'
  },
  {
    file: 'pom.xml',
    prop: 'compiler-plugin.version',
    name: 'org.apache.maven.plugins:maven-compiler-plugin',
    url: 'https://repo1.maven.org/maven2/org/apache/maven/plugins/maven-compiler-plugin/maven-metadata.xml'
  },
  {
    file: 'pom.xml',
    prop: 'surefire-plugin.version',
    name: 'org.apache.maven.plugins:maven-surefire-plugin',
    url: 'https://repo1.maven.org/maven2/org/apache/maven/plugins/maven-surefire-plugin/maven-metadata.xml'
  },
  {
    file: 'pom.xml',
    prop: 'jacoco.version',
    name: 'org.jacoco:jacoco-maven-plugin',
    url: 'https://repo1.maven.org/maven2/org/jacoco/jacoco-maven-plugin/maven-metadata.xml'
  },
  {
    file: 'pom.xml',
    prop: 'enforcer-plugin.version',
    name: 'org.apache.maven.plugins:maven-enforcer-plugin',
    url: 'https://repo1.maven.org/maven2/org/apache/maven/plugins/maven-enforcer-plugin/maven-metadata.xml'
  },
  {
    file: 'pom.xml',
    prop: 'spotless-plugin.version',
    name: 'com.diffplug.spotless:spotless-maven-plugin',
    url: 'https://repo1.maven.org/maven2/com/diffplug/spotless/spotless-maven-plugin/maven-metadata.xml'
  },
  {
    file: 'pom.xml',
    prop: 'cyclonedx-plugin.version',
    name: 'org.cyclonedx:cyclonedx-maven-plugin',
    url: 'https://repo1.maven.org/maven2/org/cyclonedx/cyclonedx-maven-plugin/maven-metadata.xml'
  },
  {
    file: 'pom.xml',
    prop: 'frontend-plugin.version',
    name: 'com.github.eirslett:frontend-maven-plugin',
    url: 'https://repo1.maven.org/maven2/com/github/eirslett/frontend-maven-plugin/maven-metadata.xml'
  },
  {
    file: 'backend/pom.xml',
    customUpdate: (content, newVer) =>
      content.replace(
        /(<artifactId>quarkus-mcp-server-http<\/artifactId>\s*<version>)[^<]+(<\/version>)/,
        `$1${newVer}$2`
      ),
    currentExtractor: (content) => {
      const m = content.match(/<artifactId>quarkus-mcp-server-http<\/artifactId>\s*<version>([^<]+)<\/version>/);
      return m ? m[1] : null;
    },
    name: 'io.quarkiverse.mcp:quarkus-mcp-server-http',
    url: 'https://repo1.maven.org/maven2/io/quarkiverse/mcp/quarkus-mcp-server-http/maven-metadata.xml'
  }
];

export async function run() {
  const args = process.argv.slice(2);
  const checkOnly = args.includes('--check');
  const dryRun = args.includes('--dry-run');
  const allowMajor = args.includes('--major');
  const skipTests = args.includes('--skip-tests');

  console.log(`[dependency-governance] Mode: ${checkOnly ? 'CHECK ONLY' : 'UPDATE'} (allowMajor: ${allowMajor})`);

  const updates = [];

  // 1. Audit Maven Dependencies
  const pomPath = path.join(rootDir, 'pom.xml');
  let pomContent = fs.readFileSync(pomPath, 'utf8');

  const backendPomPath = path.join(rootDir, 'backend/pom.xml');
  let backendPomContent = fs.readFileSync(backendPomPath, 'utf8');

  for (const item of MAVEN_REGISTRY) {
    let currentVer = null;
    if (item.prop) {
      const match = pomContent.match(new RegExp(`<${item.prop}>([^<]+)<\/${item.prop}>`));
      currentVer = match ? match[1] : null;
    } else if (item.currentExtractor) {
      currentVer = item.currentExtractor(backendPomContent);
    }

    if (!currentVer) continue;

    const available = await fetchMavenVersions(item.url);
    const target = filterSafeVersions(available, currentVer, { allowMajor });

    if (target && target !== currentVer) {
      updates.push({
        ecosystem: 'Maven',
        component: item.name,
        current: currentVer,
        target,
        apply: () => {
          if (item.prop) {
            pomContent = updatePomProperty(pomContent, item.prop, target);
          } else if (item.customUpdate) {
            backendPomContent = item.customUpdate(backendPomContent, target);
          }
        }
      });
    }
  }

  // 2. Audit Frontend Packages
  const frontendPkgPath = path.join(rootDir, 'frontend/package.json');
  let frontendPkgContent = fs.readFileSync(frontendPkgPath, 'utf8');
  const frontendPkg = JSON.parse(frontendPkgContent);

  const npmDeps = {
    ...(frontendPkg.dependencies || {}),
    ...(frontendPkg.devDependencies || {})
  };

  for (const [pkgName, verSpec] of Object.entries(npmDeps)) {
    // Retain svelte-check typescript version invariant
    if (pkgName === 'typescript') continue;

    let currentVer = verSpec.replace(/^[^0-9]*/, '');
    let realPkgName = pkgName;

    if (verSpec.startsWith('npm:')) {
      const atIdx = verSpec.lastIndexOf('@');
      if (atIdx > 0) {
        realPkgName = verSpec.substring(4, atIdx);
        currentVer = verSpec.substring(atIdx + 1);
      }
    }

    const available = fetchNpmVersions(realPkgName);
    const target = filterSafeVersions(available, currentVer, { allowMajor });

    if (target && target !== currentVer) {
      updates.push({
        ecosystem: 'NPM',
        component: pkgName,
        current: currentVer,
        target,
        apply: () => {
          frontendPkgContent = updatePackageJsonDependency(frontendPkgContent, pkgName, target);
        }
      });
    }
  }

  if (updates.length === 0) {
    console.log('✔ All dependencies are currently on safe, up-to-date versions.');
    return;
  }

  console.log(`\nDiscovered ${updates.length} potential update(s):`);
  for (const u of updates) {
    console.log(`  • [${u.ecosystem}] ${u.component}: ${u.current} -> ${u.target}`);
  }

  if (checkOnly || dryRun) {
    console.log('\n[dependency-governance] Check complete. No files were modified.');
    return;
  }

  // Apply updates
  console.log('\n[dependency-governance] Applying updates to project manifests...');
  for (const u of updates) {
    u.apply();
  }

  fs.writeFileSync(pomPath, pomContent, 'utf8');
  fs.writeFileSync(backendPomPath, backendPomContent, 'utf8');
  fs.writeFileSync(frontendPkgPath, frontendPkgContent, 'utf8');

  // Update pnpm lockfile
  try {
    console.log('[dependency-governance] Updating frontend pnpm lockfile...');
    execSync('pnpm --prefix frontend install --no-frozen-lockfile', {
      cwd: rootDir,
      stdio: 'inherit'
    });
  } catch (err) {
    console.error('✖ pnpm install failed. Initiating rollback...');
    rollback();
    process.exit(1);
  }

  if (skipTests) {
    console.log('✔ Updates applied successfully (--skip-tests active).');
    return;
  }

  // Verification Suite
  console.log('\n[dependency-governance] Running comprehensive Dual-Gate verification suite...');
  try {
    console.log('  1/5 Running Spotless code formatting validation...');
    execSync('mvn -B spotless:check validate', { cwd: rootDir, stdio: 'inherit' });

    console.log('  2/5 Running Svelte runes and type diagnostics...');
    execSync('npm --prefix frontend run check', { cwd: rootDir, stdio: 'inherit' });

    console.log('  3/5 Running Frontend Vitest test suite...');
    execSync('npm --prefix frontend run test', { cwd: rootDir, stdio: 'inherit' });

    console.log('  4/5 Running Backend JUnit test suite...');
    execSync('mvn -B -f backend/pom.xml test', { cwd: rootDir, stdio: 'inherit' });

    console.log('  5/5 Running CLI test runner...');
    execSync('npm --prefix cli test', { cwd: rootDir, stdio: 'inherit' });

    console.log('\n✔ Dual-Gate verification passed! All updates are verified safe and regression-free.');
  } catch (err) {
    console.error('\n✖ Quality gate failure encountered during dependency verification!');
    console.error('[dependency-governance] Rolling back all manifest modifications...');
    rollback();
    process.exit(1);
  }
}

function rollback() {
  try {
    execSync(
      'git checkout -- pom.xml backend/pom.xml frontend/package.json frontend/pnpm-lock.yaml frontend/package-lock.json',
      { cwd: rootDir, stdio: 'inherit' }
    );
    console.log('✔ Rollback successful. Working tree restored to clean state.');
  } catch (e) {
    console.error('Failed to rollback git state:', e.message);
  }
}

// Auto-run if executed directly as a script
if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename)) {
  run().catch((err) => {
    console.error('Execution error:', err);
    process.exit(1);
  });
}
