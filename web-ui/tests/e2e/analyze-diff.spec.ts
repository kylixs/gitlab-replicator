import { test, expect } from '@playwright/test';

// Use authenticated state
test.use({ storageState: 'tests/.auth/user.json' });

test('analyze diff column layout and wrapping', async ({ page }) => {
  // Navigate to projects page
  await page.goto('http://localhost:3000/projects');

  // Wait for table to load
  await page.waitForSelector('.el-table__body', { timeout: 10000 });

  // Wait a bit for data to render
  await page.waitForTimeout(1000);

  // Find all rows
  const rows = page.locator('.el-table__body tr');
  const rowCount = await rows.count();
  console.log(`Total rows: ${rowCount}`);

  // Analyze each row to find one with branch diffs
  let targetRowIndex = -1;
  for (let i = 0; i < rowCount; i++) {
    const row = rows.nth(i);
    const diffCell = row.locator('td').nth(3);

    // Look for diff-value spans (branch diff indicators)
    const diffValues = diffCell.locator('.diff-value');
    const diffValueCount = await diffValues.count();

    if (diffValueCount > 0) {
      console.log(`\n✓ Found row ${i} with ${diffValueCount} diff value indicators`);
      targetRowIndex = i;
      break;
    }
  }

  if (targetRowIndex === -1) {
    console.log('\n⚠️ No rows with branch diffs found, analyzing first row instead');
    targetRowIndex = 0;
  }

  // Analyze the target row
  const targetRow = rows.nth(targetRowIndex);
  const diffCell = targetRow.locator('td').nth(3);
  const diffBadge = diffCell.locator('.diff-badge');

  console.log(`\n=== Analyzing Row ${targetRowIndex} ===`);

  // Get computed styles of diff-badge
  const badgeStyles = await diffBadge.evaluate((el) => {
    const computed = window.getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    return {
      display: computed.display,
      flexDirection: computed.flexDirection,
      flexWrap: computed.flexWrap,
      gap: computed.gap,
      width: rect.width,
      height: rect.height,
      innerHTML: el.innerHTML.substring(0, 300)
    };
  });

  console.log('\n=== Diff Badge Styles ===');
  console.log(JSON.stringify(badgeStyles, null, 2));

  // Get all direct children positions
  const childrenInfo = await diffBadge.evaluate((el) => {
    const children = Array.from(el.children);
    const parentRect = el.getBoundingClientRect();

    return children.map((child, index) => {
      const rect = child.getBoundingClientRect();
      const computed = window.getComputedStyle(child);
      return {
        index,
        tag: child.tagName,
        className: child.className,
        text: child.textContent?.trim().substring(0, 50),
        width: Math.round(rect.width),
        height: Math.round(rect.height),
        top: Math.round(rect.top - parentRect.top),
        left: Math.round(rect.left - parentRect.left),
        display: computed.display,
        whiteSpace: computed.whiteSpace,
        flexShrink: computed.flexShrink,
        flexWrap: computed.flexWrap
      };
    });
  });

  console.log('\n=== Children Elements ===');
  childrenInfo.forEach(child => {
    console.log(`[${child.index}] ${child.tag}.${child.className.split(' ')[0]}`);
    console.log(`  Text: "${child.text}"`);
    console.log(`  Size: ${child.width}x${child.height}, Position: (${child.left}, ${child.top})`);
    console.log(`  Styles: display=${child.display}, whiteSpace=${child.whiteSpace}, flexShrink=${child.flexShrink}`);
  });

  // Check for wrapping
  const topPositions = childrenInfo.map(c => c.top);
  const uniqueTops = [...new Set(topPositions)];
  const isWrapping = uniqueTops.length > 1;

  console.log('\n=== Wrapping Analysis ===');
  console.log('Top positions:', topPositions);
  console.log('Unique tops:', uniqueTops);
  console.log('IS WRAPPING:', isWrapping);

  if (isWrapping) {
    console.log('\n⚠️ WRAPPING DETECTED!');
    console.log('Elements on different rows:');
    uniqueTops.forEach(top => {
      const elemsOnRow = childrenInfo.filter(c => c.top === top);
      console.log(`  Row at top=${top}:`, elemsOnRow.map(e => `"${e.text}"`).join(', '));
    });
  }

  // Take screenshot
  await diffCell.screenshot({ path: 'tests/screenshots/diff-cell-analysis.png' });
  console.log('\n📸 Screenshot saved to: tests/screenshots/diff-cell-analysis.png');

  // Get cell width
  const cellWidth = await diffCell.evaluate(el => el.getBoundingClientRect().width);
  console.log('\n=== Cell Info ===');
  console.log('Cell width:', cellWidth);
  console.log('Badge width:', badgeStyles.width);
  console.log('Overflow:', badgeStyles.width > cellWidth ? 'YES' : 'NO');
});
