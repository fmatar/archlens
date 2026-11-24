import { test, expect } from '@playwright/test';

const E2E_TOKEN = process.env.ARCHLENS_TOKEN!;

test.describe('Archlens Workbench & Source Inspection', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`/?token=${encodeURIComponent(E2E_TOKEN)}`);
  });

  test('should render canvas, open class card, and view non-empty source code', async ({ page }) => {
    await expect(page.locator('header').getByText('Archlens', { exact: true })).toBeVisible();

    const componentHeader = page.locator('svg text').first();
    await expect(componentHeader).toBeVisible({ timeout: 10000 });

    const classText = page
      .locator('svg text', { hasText: /GraphCompiler|ClassNode|DiagramResource|Proposal/ })
      .first();
    await expect(classText).toBeVisible({ timeout: 10000 });
    await classText.click({ force: true });

    const classModal = page.locator('div[role="dialog"]');
    await expect(classModal).toBeVisible({ timeout: 5000 });

    const viewSourceBtn = page.locator('button', { hasText: 'View Source Code' });
    await expect(viewSourceBtn).toBeVisible();
    await viewSourceBtn.click();

    const sourceTable = page.locator('table');
    await expect(sourceTable).toBeVisible({ timeout: 5000 });

    const codeRows = page.locator('table tbody tr');
    await expect(codeRows.first()).toBeVisible({ timeout: 5000 });
    expect(await codeRows.count()).toBeGreaterThan(5);

    const targetRow = page.locator('tr.target-code-row');
    await expect(targetRow).toBeVisible();

    const codeText = await sourceTable.textContent();
    expect(codeText).not.toContain('<span');
    expect(codeText).not.toContain('undefined');
    expect(codeText).toContain('package');
  });

  test('should open command palette with shortcut and search items', async ({ page }) => {
    const quickFindBtn = page.locator('button[aria-label="Quick search (Cmd+K)"]');
    await expect(quickFindBtn).toBeVisible({ timeout: 10000 });
    await quickFindBtn.click();

    const palette = page.locator('input[placeholder*="Type to search"]');
    await expect(palette).toBeVisible({ timeout: 5000 });

    await palette.fill('GraphCompiler');
    const resultItem = page.locator('div[role="dialog"]', { hasText: 'GraphCompiler' });
    await expect(resultItem.first()).toBeVisible({ timeout: 5000 });

    await page.keyboard.press('Escape');
    await expect(palette).not.toBeVisible();
  });

  test('should switch to architecture proposal and display diff banner', async ({ page }) => {
    const proposalBtn = page.locator('button', { hasText: 'Clean Architecture Standard' });
    await expect(proposalBtn).toBeVisible({ timeout: 10000 });
    await proposalBtn.click();

    const diffBanner = page.locator('text=WHAT-IF PROPOSAL');
    await expect(diffBanner).toBeVisible({ timeout: 5000 });

    const compareRealBtn = page.locator('button', { hasText: 'Compare Real (Live)' });
    await expect(compareRealBtn).toBeVisible();
    await compareRealBtn.click();

    await expect(diffBanner).not.toBeVisible({ timeout: 5000 });
  });

  test('should cycle declutter modes', async ({ page }) => {
    const declutterBtn = page.getByRole('button', { name: /^Declutter Mode/ });
    await expect(declutterBtn).toBeVisible({ timeout: 10000 });
    await expect(declutterBtn).toContainText('ARROWS');

    await declutterBtn.click();
    await expect(declutterBtn).toContainText('REMOVE_ARROWS');

    await declutterBtn.click();
    await expect(declutterBtn).toContainText('ELEMENTS');

    await declutterBtn.click();
    await expect(declutterBtn).toContainText('CLASSES');
  });

  test('should smoothly drag a component node and reset layout', async ({ page }) => {
    const firstBox = page.locator('rect[data-testid="component-card"]').first();
    await expect(firstBox).toBeVisible({ timeout: 10000 });

    const initialBoxBounds = await firstBox.boundingBox();
    expect(initialBoxBounds).not.toBeNull();

    await page.mouse.move(initialBoxBounds!.x + 30, initialBoxBounds!.y + 15);
    await page.mouse.down();
    await page.mouse.move(initialBoxBounds!.x + 150, initialBoxBounds!.y + 120, { steps: 5 });
    await page.mouse.up();

    const movedBoxBounds = await firstBox.boundingBox();
    expect(movedBoxBounds!.x).toBeGreaterThan(initialBoxBounds!.x + 50);

    const resetLayoutBtn = page.locator('button[aria-label="Reset layout"]');
    await expect(resetLayoutBtn).toBeVisible();
    await resetLayoutBtn.click();

    await expect
      .poll(async () => {
        const b = await firstBox.boundingBox();
        return b ? Math.abs(b.x - initialBoxBounds!.x) : 999;
      })
      .toBeLessThan(10);
  });

  test('should display node frustum counter and transition to Semantic LOD on zoom out', async ({
    page
  }) => {
    const nodesCounter = page.locator('div[title*="Frustum Culled Rendered Nodes"]');
    await expect(nodesCounter).toBeVisible({ timeout: 10000 });
    await expect(nodesCounter).toContainText('nodes');

    const classRow = page.locator('svg g.group\\/row').first();
    await expect(classRow).toBeVisible({ timeout: 10000 });

    const zoomOutBtn = page.locator('button[aria-label="Zoom out"]');
    await expect(zoomOutBtn).toBeVisible();
    for (let i = 0; i < 7; i++) {
      await zoomOutBtn.click();
    }

    const lodText = page.locator('svg text', { hasText: /classes|class/ }).first();
    await expect(lodText).toBeVisible({ timeout: 5000 });
    await expect(classRow).not.toBeVisible();

    const resetZoomBtn = page.locator('button[aria-label="Reset zoom"]');
    await resetZoomBtn.click();
    await expect(classRow).toBeVisible({ timeout: 5000 });
  });

  test('should toggle edge bundling and multi-select declutter filters', async ({ page }) => {
    const bundlingBtn = page.locator('button[title*="Toggle Hierarchical Edge Bundling"]');
    await expect(bundlingBtn).toBeVisible({ timeout: 10000 });
    await expect(bundlingBtn).toContainText('Bundled');

    await bundlingBtn.click();
    await expect(bundlingBtn).toContainText('Detailed');

    await page.keyboard.press('b');
    await expect(bundlingBtn).toContainText('Bundled');

    const xrayBtn = page.locator('button[title*="Violation X-Ray Mode"]');
    await expect(xrayBtn).toBeVisible();
    await page.keyboard.press('v');
    await expect(xrayBtn).toHaveClass(/bg-rose-500/);

    const xrayToggle = page.locator('button', { hasText: 'Violation X-Ray Mode' });
    const compactToggle = page.locator('button', { hasText: 'Compact Macro Cards' });

    await expect(xrayToggle).toBeVisible();
    await expect(compactToggle).toBeVisible();

    await compactToggle.click();
    await expect(xrayToggle).toContainText('[✓]');
    await expect(compactToggle).toContainText('[✓]');

    await page.keyboard.press('v');
    await expect(xrayToggle).toContainText('[ ]');
    await expect(compactToggle).toContainText('[✓]');

    await compactToggle.click();
    await expect(compactToggle).toContainText('[ ]');
  });

  test('should support dynamic project switching and open directory modal', async ({ page }) => {
    const projectSelect = page.locator('select[title="Switch active repository"]');
    await expect(projectSelect).toBeVisible({ timeout: 10000 });
    await expect(projectSelect).toHaveValue('.');

    const openBtn = page.locator('button[aria-label="Open Project Folder"]');
    await expect(openBtn).toBeVisible();
    await openBtn.click();

    const modalTitle = page.getByRole('heading', { name: 'Open Repository or Directory' });
    await expect(modalTitle).toBeVisible({ timeout: 5000 });

    await page.keyboard.press('Escape');
    await expect(modalTitle).not.toBeVisible();

    await page.keyboard.press('Control+o');
    await expect(modalTitle).toBeVisible({ timeout: 5000 });
    await page.keyboard.press('Escape');
    await expect(modalTitle).not.toBeVisible();
  });

  test('should support stepwise class pagination on nodes and focus inventory in inspector', async ({
    page
  }) => {
    const pagerText = page.locator('svg text', { hasText: /1–5 of \d+ \(inspect ↗\)/ }).first();
    await expect(pagerText).toBeVisible({ timeout: 10000 });

    const nextBtn = page.locator('g[aria-label="Next classes"]').first();
    await expect(nextBtn).toBeVisible();
    await nextBtn.dispatchEvent('click');

    const page2Text = page.locator('svg text', { hasText: /6–\d+ of \d+ \(inspect ↗\)/ }).first();
    await expect(page2Text).toBeVisible({ timeout: 5000 });

    await page2Text.click({ force: true });

    const inspectorFilter = page.locator('input[placeholder*="Filter"][placeholder*="classes..."]');
    await expect(inspectorFilter).toBeVisible({ timeout: 5000 });

    await inspectorFilter.fill('Node');
    const filteredClass = page.locator('button', { hasText: 'ComponentNode' });
    await expect(filteredClass).toBeVisible({ timeout: 5000 });

    await filteredClass.click();
    const classModal = page.locator('div[role="dialog"]');
    await expect(classModal).toBeVisible({ timeout: 5000 });
    await page.keyboard.press('Escape');
  });

  test('should trigger agent regen without freezing and log telemetry events', async ({ page }) => {
    await page.route('**/api/mailbox/to-agent*', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ id: 1, op: 'REGEN', target: {}, payload: {} })
        });
      } else {
        await route.continue();
      }
    });

    const regenBtn = page.locator('button[aria-label="Re-index AST and Wake Agent"]');
    await expect(regenBtn).toBeVisible({ timeout: 10000 });
    await regenBtn.click();

    await expect(
      page.locator('text=AST re-indexed. Policy evaluation verified (0 violations).').first()
    ).toBeVisible({ timeout: 8000 });

    await expect(regenBtn).toContainText('Regen & Sync Mailbox', { timeout: 6000 });
  });

  test('should render diagnostic empty state card when workspace has no components', async ({
    page
  }) => {
    await page.route('**/api/graph*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Empty Workspace',
          isProposal: false,
          activeProposalId: null,
          components: [],
          edges: [],
          unassigned: []
        })
      });
    });

    await page.goto(`/?token=${encodeURIComponent(E2E_TOKEN)}`);

    await expect(page.locator('text=No Architecture Components Found')).toBeVisible({
      timeout: 6000
    });
    await expect(page.getByRole('button', { name: 'Switch Workspace' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Re-index AST', exact: true })).toBeVisible();
  });

  test('should browse filesystem and filter directories in open modal', async ({ page }) => {
    const openBtn = page.locator('button[aria-label="Open Project Folder"]');
    await expect(openBtn).toBeVisible({ timeout: 10000 });
    await openBtn.click();

    const modalTitle = page.getByRole('heading', { name: 'Open Repository or Directory' });
    await expect(modalTitle).toBeVisible({ timeout: 5000 });

    const filterInput = page.locator('input[placeholder*="Filter folders in this directory..."]');
    await expect(filterInput).toBeVisible({ timeout: 5000 });

    await filterInput.fill('backend');
    await expect(filterInput).toHaveValue('backend');

    await filterInput.fill('');
    await expect(filterInput).toHaveValue('');

    const toggleExplorerBtn = page.locator('button', { hasText: /Hide Explorer|Explore Folders/ });
    await expect(toggleExplorerBtn).toBeVisible();
    await toggleExplorerBtn.click();
    await expect(filterInput).not.toBeVisible();

    await toggleExplorerBtn.click();
    await expect(filterInput).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(modalTitle).not.toBeVisible();
  });

  test('should open git version comparator dropdown and toggle comparison mode', async ({
    page
  }) => {
    await page.route('**/api/snapshots*', async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/snapshots') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            snapshots: [{ id: 'v1.1.0', tag: 'v1.1.0', label: 'v1.1.0', date: '2026-11-10' }]
          })
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            title: 'Archlens v1.1.0',
            isProposal: false,
            components: [],
            edges: [],
            unassigned: []
          })
        });
      }
    });
    await page.goto(`/?token=${encodeURIComponent(E2E_TOKEN)}`);

    const compareBtn = page.locator('button', { hasText: 'Compare Release' });
    await expect(compareBtn).toBeVisible({ timeout: 10000 });
    await compareBtn.click();

    const menuTitle = page.locator('span', { hasText: 'Git Release Comparator' });
    await expect(menuTitle).toBeVisible({ timeout: 5000 });

    const releaseOption = page.locator('button', { hasText: 'v1.1.0' }).first();
    await expect(releaseOption).toBeVisible({ timeout: 5000 });
    await releaseOption.click();

    const exitDiffBtn = page.locator('button', { hasText: 'Exit' });
    await expect(exitDiffBtn).toBeVisible({ timeout: 10000 });

    await exitDiffBtn.click();
    await expect(compareBtn).toBeVisible({ timeout: 5000 });
  });
});
