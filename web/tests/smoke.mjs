import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createFixtures } from './fixtures.mjs';
const { server, state } = createFixtures(0);
await once(server, 'listening');
const vite = spawn(
  process.execPath,
  ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '15175', '--strictPort'],
  {
    env: { ...process.env, API_PROXY_TARGET: `http://127.0.0.1:${server.address().port}` },
    stdio: 'ignore',
  },
);
let browser;
const results = [];
const check = (name) => {
  results.push(name);
  console.log(`PASS ${name}`);
};
try {
  for (let i = 0; i < 60; i++) {
    try {
      if ((await fetch('http://127.0.0.1:15175')).ok) break;
    } catch {}
    await new Promise((r) => setTimeout(r, 250));
  }
  browser = await chromium.launch({
    headless: true,
    ...(process.env.PLAYWRIGHT_CHANNEL
      ? { channel: process.env.PLAYWRIGHT_CHANNEL }
      : process.platform === 'win32'
        ? { channel: 'msedge' }
        : {}),
  });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } }),
    errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('http://127.0.0.1:15175');
  await page.getByRole('heading', { name: '在售商品' }).waitFor();
  await page.getByRole('heading', { name: '便携随行杯 · 雾白 500ml' }).waitFor();
  await mkdir('tests/results', { recursive: true });
  await page.screenshot({ path: 'tests/results/catalog-desktop.png', fullPage: true });
  check('public catalog loads without authentication');
  await page.getByRole('link', { name: '我的订单', exact: true }).click();
  await page.getByRole('heading', { name: '欢迎回来' }).waitFor();
  await page.getByRole('button', { name: '注册账户', exact: true }).click();
  await page.getByLabel('用户名', { exact: true }).fill('test-buyer');
  await page.getByLabel('密码', { exact: true }).fill('test-password123');
  await page.getByLabel('确认密码', { exact: true }).fill('test-password123');
  await page.getByRole('button', { name: '注册并登录', exact: true }).click();
  await page.getByRole('heading', { name: '我的订单', exact: true }).waitFor();
  check('registration signs in and restores intended protected route');
  await page.goto('http://127.0.0.1:15175/admin/products');
  await page.waitForURL('http://127.0.0.1:15175/');
  check('buyer is redirected away from admin routes');
  await page.getByRole('heading', { name: '便携随行杯 · 雾白 500ml' }).click();
  await page.waitForURL(`**/products/${state.skuId}`);
  assert.ok(page.url().endsWith(state.skuId));
  await page.getByLabel('购买数量').fill('2');
  await page.getByRole('button', { name: '确认选购' }).click();
  await page.getByRole('heading', { name: '确认订单', exact: true }).waitFor();
  let failedOnce = false;
  await page.route('**/api/orders', async (route) => {
    if (route.request().method() === 'POST' && !failedOnce) {
      failedOnce = true;
      await route.fetch();
      await route.abort('failed');
    } else await route.continue();
  });
  await page.getByRole('button', { name: '提交订单，选择支付' }).click();
  await page.getByRole('alert').filter({ hasText: '网络连接失败' }).waitFor();
  await page.getByRole('button', { name: '提交订单，选择支付' }).click();
  await page.getByRole('heading', { name: '选择支付方式', exact: true }).waitFor();
  assert.equal(state.receivedTokens.length, 2);
  assert.equal(state.receivedTokens[0], state.receivedTokens[1]);
  assert.equal(state.orders.length, 2);
  check('lost create response retries with same token and creates only one order');
  await page.getByText('暂时没有可用的支付方式', { exact: true }).waitFor();
  assert.equal(await page.getByRole('radio').count(), 2);
  for (const radio of await page.getByRole('radio').all())
    assert.equal(await radio.isDisabled(), true);
  assert.equal(await page.getByRole('button', { name: '确认支付方式' }).isDisabled(), true);
  assert.equal(state.payments.length, 0);
  await page.screenshot({ path: 'tests/results/checkout-desktop.png', fullPage: true });
  check('both real payment methods display unavailable and cannot trigger payment');
  const orderNo = state.orders[0].orderNo;
  await page.getByRole('link', { name: '查看订单详情' }).click();
  await page.getByRole('button', { name: '取消订单', exact: true }).click();
  await page.getByRole('button', { name: '确认操作' }).click();
  await page.getByRole('heading', { name: '订单已取消' }).waitFor();
  assert.equal(state.orders[0].status, 'CANCELED');
  check('buyer can cancel an unpaid order');
  await page.getByRole('button', { name: '退出', exact: true }).click();
  await page.getByRole('link', { name: '登录 / 注册' }).click();
  await page.getByLabel('用户名', { exact: true }).fill('admin');
  await page.getByLabel('密码', { exact: true }).fill('admin-password123');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.getByRole('link', { name: '管理工作台', exact: true }).click();
  await page.getByRole('heading', { name: '订单处理', exact: true }).waitFor();
  await page.getByRole('link', { name: '处理发货' }).click();
  await page.getByRole('button', { name: '确认发货' }).click();
  await page.getByRole('button', { name: '确认操作' }).click();
  await page.getByRole('heading', { name: '订单已发货，等待确认收货' }).waitFor();
  assert.equal(state.orders[1].status, 'WAIT_RECEIVE');
  check('administrator can deliver a paid order with exact buyer ID');
  await page.getByRole('link', { name: '商品与库存' }).click();
  await page.getByRole('button', { name: '新增商品' }).click();
  await page.getByLabel('商品名称', { exact: true }).fill('测试商品');
  await page.getByLabel('规格名称', { exact: true }).fill('测试商品 · 默认');
  await page.getByLabel('销售价格（元）').fill('12.29');
  await page.getByLabel('上架销售').check();
  await page.getByRole('button', { name: '保存商品' }).click();
  const row = page.getByRole('row').filter({ hasText: '测试商品 · 默认' });
  await row.waitFor();
  assert.equal(state.products.at(-1).priceCent, 1229);
  await row.getByRole('button', { name: '库存', exact: true }).click();
  await page.getByLabel('库存总量').fill('25');
  await page.getByRole('button', { name: '更新库存' }).click();
  await page.getByText('库存已更新。', { exact: true }).waitFor();
  assert.equal(state.inventory.get(state.products.at(-1).skuId).available, 25);
  await page.getByRole('button', { name: '关闭', exact: true }).last().click();
  await page.screenshot({ path: 'tests/results/admin-products-desktop.png', fullPage: true });
  check('admin creates catalog item with exact cents and initializes stock');
  await row.getByRole('button', { name: '编辑', exact: true }).click();
  await page.getByLabel('上架销售').uncheck();
  await page.getByRole('button', { name: '保存商品' }).click();
  await row.getByText('已下架', { exact: true }).waitFor();
  assert.equal(await row.getByRole('link', { name: '预览', exact: true }).count(), 0);
  check('off-shelf product hides public preview');
  await page.getByRole('button', { name: '退出', exact: true }).click();
  await page.getByRole('link', { name: '登录 / 注册' }).click();
  await page.getByLabel('用户名', { exact: true }).fill('test-buyer');
  await page.getByLabel('密码', { exact: true }).fill('test-password123');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.goto('http://127.0.0.1:15175/orders/ORDER2026101300001');
  await page.getByRole('button', { name: '确认收货', exact: true }).click();
  await page.getByRole('button', { name: '确认操作' }).click();
  await page.getByRole('heading', { name: '订单已完成，感谢你的选购' }).waitFor();
  check('buyer confirms receipt after administrator delivery');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('http://127.0.0.1:15175');
  await page.getByRole('heading', { name: '在售商品' }).waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  await page.screenshot({ path: 'tests/results/catalog-mobile.png', fullPage: true });
  await page.goto(`http://127.0.0.1:15175/orders/${orderNo}`);
  await page.getByRole('heading', { name: '订单详情', exact: true }).waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  await page.screenshot({ path: 'tests/results/order-mobile.png', fullPage: true });
  check('390px catalog and order detail have no horizontal overflow');
  assert.deepEqual(errors, []);
  check('no uncaught browser runtime errors');
  console.log(
    `Completed ${results.length} browser assertions against isolated HTTP fixtures; no real backend or payment validation.`,
  );
} finally {
  await browser?.close();
  vite.kill();
  await new Promise((resolve) => server.close(resolve));
}
