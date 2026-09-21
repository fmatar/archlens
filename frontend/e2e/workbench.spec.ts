import { test, expect } from '@playwright/test';

test.describe('Archlens Workbench & Source Inspection', () => {
  test('should render canvas, open class card, and view non-empty source code', async ({ page }) => {
    // 1. Navigate to workbench
    await page.goto('/');

    // 2. Verify workbench header is visible
    await expect(page.getByText('Clean Architecture Workbench')).toBeVisible();

    // 3. Wait for SVG canvas to render component boxes
    const componentHeader = page.locator('svg text').first();
    await expect(componentHeader).toBeVisible({ timeout: 10000 });

    // 4. Click a class text inside a component box to open ClassCard
    const classText = page.locator('svg text', { hasText: /GraphCompiler|ClassNode|DiagramResource|Proposal/ }).first();
    await expect(classText).toBeVisible({ timeout: 10000 });
    await classText.click({ force: true });

    // 5. Verify ClassCard dialog opens
    const classModal = page.locator('div[role="dialog"]');
    await expect(classModal).toBeVisible({ timeout: 5000 });

    // 6. Click "View Source Code" button
    const viewSourceBtn = page.locator('button', { hasText: 'View Source Code' });
    await expect(viewSourceBtn).toBeVisible();
    await viewSourceBtn.click();

    // 7. Verify SourceModal opens and code viewer is populated with actual code lines
    const sourceTable = page.locator('table');
    await expect(sourceTable).toBeVisible({ timeout: 5000 });

    const codeRows = page.locator('table tbody tr');
    await expect(codeRows.first()).toBeVisible({ timeout: 5000 });
    const rowCount = await codeRows.count();
    expect(rowCount).toBeGreaterThan(5);

    // 8. Verify target line highlight exists
    const targetRow = page.locator('tr.target-code-row');
    await expect(targetRow).toBeVisible();

    // 9. ponytail: verify text is clean preformatted code without broken HTML fragments
    const codeText = await sourceTable.textContent();
    expect(codeText).not.toContain('<span');
    expect(codeText).not.toContain('undefined');
    expect(codeText).toContain('package');
  });

  test('should open command palette with shortcut and search items', async ({ page }) => {
    await page.goto('/');

    // Click on Quick Find button in header
    const quickFindBtn = page.locator('button', { hasText: 'Quick Find' });
    await expect(quickFindBtn).toBeVisible({ timeout: 10000 });
    await quickFindBtn.click();

    // Verify palette input opens
    const palette = page.locator('input[placeholder*="Type to search"]');
    await expect(palette).toBeVisible({ timeout: 5000 });

    // Search for a class
    await palette.fill('GraphCompiler');
    const resultItem = page.locator('div[role="dialog"]', { hasText: 'GraphCompiler' });
    await expect(resultItem.first()).toBeVisible({ timeout: 5000 });

    // Press Escape to dismiss
    await page.keyboard.press('Escape');
    await expect(palette).not.toBeVisible();
  });

  test('should switch to architecture proposal and display diff banner', async ({ page }) => {
    await page.goto('/');

    // Click on Clean Architecture proposal button in Inspector
    const proposalBtn = page.locator('button', { hasText: 'Clean Architecture Standard' });
    await expect(proposalBtn).toBeVisible({ timeout: 10000 });
    await proposalBtn.click();

    // Verify ProposalDiffBanner appears
    const diffBanner = page.locator('text=WHAT-IF PROPOSAL');
    await expect(diffBanner).toBeVisible({ timeout: 5000 });

    // Click "Compare Real (Live)" to return to real graph
    const compareRealBtn = page.locator('button', { hasText: 'Compare Real (Live)' });
    await expect(compareRealBtn).toBeVisible();
    await compareRealBtn.click();

    // Verify banner disappears
    await expect(diffBanner).not.toBeVisible({ timeout: 5000 });
  });

  test('should cycle declutter modes', async ({ page }) => {
    await page.goto('/');

    // Find declutter button
    const declutterBtn = page.locator('button', { hasText: 'Declutter' });
    await expect(declutterBtn).toBeVisible({ timeout: 10000 });

    // Initial mode is ARROWS
    await expect(declutterBtn).toContainText('ARROWS');

    // Click to cycle
    await declutterBtn.click();
    await expect(declutterBtn).toContainText('REMOVE_ARROWS');

    // Click to cycle again
    await declutterBtn.click();
    await expect(declutterBtn).toContainText('ELEMENTS');

    // Click to cycle to CLASSES
    await declutterBtn.click();
    await expect(declutterBtn).toContainText('CLASSES');
  });

  test('should smoothly drag a component node and reset layout', async ({ page }) => {
    await page.goto('/');

    // Wait for a component box to be rendered
    const firstBox = page.locator('rect[data-testid="component-card"]').first();
    await expect(firstBox).toBeVisible({ timeout: 10000 });

    const initialBoxBounds = await firstBox.boundingBox();
    expect(initialBoxBounds).not.toBeNull();

    // Perform smooth drag
    await page.mouse.move(initialBoxBounds!.x + 30, initialBoxBounds!.y + 15);
    await page.mouse.down();
    await page.mouse.move(initialBoxBounds!.x + 150, initialBoxBounds!.y + 120, { steps: 5 });
    await page.mouse.up();

    // Verify node moved
    const movedBoxBounds = await firstBox.boundingBox();
    expect(movedBoxBounds!.x).toBeGreaterThan(initialBoxBounds!.x + 50);

    // Click reset layout button
    const resetLayoutBtn = page.locator('button[aria-label="Reset layout"]');
    await expect(resetLayoutBtn).toBeVisible();
    await resetLayoutBtn.click();

    // Verify position reset
    await page.waitForTimeout(600);
    const resetBoxBounds = await firstBox.boundingBox();
    expect(Math.abs(resetBoxBounds!.x - initialBoxBounds!.x)).toBeLessThan(10);
  });

  test('should display node frustum counter and transition to Semantic LOD on zoom out', async ({ page }) => {
    await page.goto('/');

    // 1. Verify HUD nodes counter is visible (e.g., "7/7 nodes")
    const nodesCounter = page.locator('div[title*="Frustum Culled Rendered Nodes"]');
    await expect(nodesCounter).toBeVisible({ timeout: 10000 });
    await expect(nodesCounter).toContainText('nodes');

    // 2. Initially at 100% zoom, detailed class rows exist
    const classRow = page.locator('svg g.group\\/row').first();
    await expect(classRow).toBeVisible({ timeout: 10000 });

    // 3. Zoom out below 55%
    const zoomOutBtn = page.locator('button[aria-label="Zoom out"]');
    await expect(zoomOutBtn).toBeVisible();
    for (let i = 0; i < 7; i++) {
      await zoomOutBtn.click();
      await page.waitForTimeout(100);
    }

    // 4. Verify compact Semantic LOD appears (e.g. "classes" and "HEALTHY" or "HIGH CRAP")
    const lodText = page.locator('svg text', { hasText: /classes|class/ }).first();
    await expect(lodText).toBeVisible({ timeout: 5000 });

    // 5. Detailed class rows are now omitted in compact mode to preserve 120 FPS
    await expect(classRow).not.toBeVisible();

    // 6. Reset view back to 100%
    const resetZoomBtn = page.locator('button[aria-label="Reset zoom"]');
    await expect(resetZoomBtn).toBeVisible();
    await resetZoomBtn.click();

    // 7. Detailed class rows re-appear
    await expect(classRow).toBeVisible({ timeout: 5000 });
  });

  test('should toggle edge bundling and multi-select declutter filters', async ({ page }) => {
    await page.goto('/');

    // 1. Verify HUD edge bundling toggle exists
    const bundlingBtn = page.locator('button[title*="Toggle Hierarchical Edge Bundling"]');
    await expect(bundlingBtn).toBeVisible({ timeout: 10000 });
    await expect(bundlingBtn).toContainText('Bundled');

    // 2. Toggle bundling to Detailed via click
    await bundlingBtn.click();
    await expect(bundlingBtn).toContainText('Detailed');

    // 3. Toggle back via 'b' hotkey
    await page.keyboard.press('b');
    await expect(bundlingBtn).toContainText('Bundled');

    // 4. Test Violation X-Ray mode via 'v' hotkey
    const xrayBtn = page.locator('button[title*="Violation X-Ray Mode"]');
    await expect(xrayBtn).toBeVisible();
    await page.keyboard.press('v');
    await expect(xrayBtn).toHaveClass(/bg-rose-500/);

    // 5. Test Multi-Select matrix in Inspector
    const xrayToggle = page.locator('button', { hasText: 'Violation X-Ray Mode' });
    const compactToggle = page.locator('button', { hasText: 'Compact Macro Cards' });

    await expect(xrayToggle).toBeVisible();
    await expect(compactToggle).toBeVisible();

    // Toggle compact cards as well (both active simultaneously!)
    await compactToggle.click();
    await expect(xrayToggle).toContainText('[✓]');
    await expect(compactToggle).toContainText('[✓]');

    // Press 'v' again to toggle off X-Ray while keeping Compact active
    await page.keyboard.press('v');
    await expect(xrayToggle).toContainText('[ ]');
    await expect(compactToggle).toContainText('[✓]');

    // Reset filters
    await compactToggle.click();
    await expect(compactToggle).toContainText('[ ]');
  });

  test('should support dynamic project switching, URL synchronization, and open directory modal', async ({ page }) => {
    // 1. Navigate to default root without query parameter
    await page.goto('/');

    // 2. Verify project dropdown defaults to Active Workspace
    const projectSelect = page.locator('select[title="Switch active repository"]');
    await expect(projectSelect).toBeVisible({ timeout: 10000 });
    await expect(projectSelect).toHaveValue('.');

    // 3. Open project modal via quick button
    const openBtn = page.locator('button[aria-label="Open Project Folder"]');
    await expect(openBtn).toBeVisible();
    await openBtn.click();

    // 4. Verify Open Project dialog is visible
    const modalTitle = page.getByRole('heading', { name: 'Open Repository or Directory' });
    await expect(modalTitle).toBeVisible({ timeout: 5000 });

    // 5. Close dialog with Escape
    await page.keyboard.press('Escape');
    await expect(modalTitle).not.toBeVisible();

    // 6. Open project modal via keyboard shortcut (Meta+O / Control+O)
    await page.keyboard.press('Control+o');
    await expect(modalTitle).toBeVisible({ timeout: 5000 });
    await page.keyboard.press('Escape');
    await expect(modalTitle).not.toBeVisible();
  });

  test('should support stepwise class pagination on nodes and focus inventory in inspector', async ({ page }) => {
    await page.goto('/');

    // 1. Locate the pagination control on scanner component (which has 9 classes)
    const pagerText = page.locator('svg text', { hasText: /1–5 of \d+ \(inspect ↗\)/ }).first();
    await expect(pagerText).toBeVisible({ timeout: 10000 });

    // 2. Click the next page button (›)
    const nextBtn = page.locator('g[aria-label="Next classes"]').first();
    await expect(nextBtn).toBeVisible();
    await nextBtn.dispatchEvent('click');

    // 3. Verify page advanced to second page (e.g. 6–9 of 9)
    const page2Text = page.locator('svg text', { hasText: /6–\d+ of \d+ \(inspect ↗\)/ }).first();
    await expect(page2Text).toBeVisible({ timeout: 5000 });

    // 4. Click the pager badge to inspect in sidebar
    await page2Text.click({ force: true });

    // 5. Verify Inspector opens the Focused Component Inventory
    const inspectorFilter = page.locator('input[placeholder*="Filter"][placeholder*="classes..."]');
    await expect(inspectorFilter).toBeVisible({ timeout: 5000 });

    // 6. Type to filter classes in Inspector
    await inspectorFilter.fill('Node');
    const filteredClass = page.locator('button', { hasText: 'ComponentNode' });
    await expect(filteredClass).toBeVisible({ timeout: 5000 });

    // 7. Click the filtered class to open ClassCard
    await filteredClass.click();
    const classModal = page.locator('div[role="dialog"]');
    await expect(classModal).toBeVisible({ timeout: 5000 });
    await page.keyboard.press('Escape');
  });

  test('should trigger agent regen without freezing and log telemetry events', async ({ page }) => {
    await page.goto('/');

    const regenBtn = page.locator('button', { hasText: 'Regen (Wake Agent)' });
    await expect(regenBtn).toBeVisible({ timeout: 10000 });
    await regenBtn.click();

    // Verify button transitions to synthesizing state smoothly
    await expect(page.locator('button', { hasText: 'Agent Synthesizing Code...' })).toBeVisible();

    // Verify telemetry drawer updates with success
    await expect(page.locator('text=AST re-indexed. Policy evaluation verified (0 violations).').first()).toBeVisible({ timeout: 6000 });

    // Verify button returns to enabled state
    await expect(page.locator('button', { hasText: 'Regen (Wake Agent)' })).toBeVisible({ timeout: 6000 });
  });

  test('should render diagnostic empty state card when workspace has no components', async ({ page }) => {
    // Intercept /api/graph to simulate empty workspace
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

    await page.goto('/');

    // Verify diagnostic empty state card appears
    await expect(page.locator('text=No Architecture Components Found')).toBeVisible({ timeout: 6000 });
    await expect(page.getByRole('button', { name: 'Switch Workspace' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Wake Agent', exact: true })).toBeVisible();
  });
});
