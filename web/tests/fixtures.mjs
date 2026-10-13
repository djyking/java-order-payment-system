// Isolated frontend test data. Never imported by application code or deployed images.
import http from 'node:http';
import { pathToFileURL } from 'node:url';
export function createFixtures(port = 18080) {
  const skuId = '90071992547409931',
    userId = '90071992547409939';
  const products = [
    {
      skuId,
      spuId: '90071992547409930',
      spuName: '日常好物',
      skuName: '便携随行杯 · 雾白 500ml',
      priceCent: 8900,
      onShelf: true,
    },
    {
      skuId: '90071992547409932',
      spuId: '90071992547409933',
      spuName: '轻松生活',
      skuName: '桌面收纳盒 · 森林绿',
      priceCent: 12900,
      onShelf: true,
    },
    {
      skuId: '90071992547409934',
      spuId: '90071992547409935',
      spuName: '书写时光',
      skuName: '随行笔记本 · 暖灰',
      priceCent: 4900,
      onShelf: true,
    },
  ];
  const users = new Map([
    ['demo', { userId, username: 'demo', role: 'USER', accessToken: 'fixture-user' }],
    [
      'admin',
      {
        userId: '90071992547409940',
        username: 'admin',
        role: 'ADMIN',
        accessToken: 'fixture-admin',
      },
    ],
  ]);
  const line = {
    skuId,
    skuName: products[0].skuName,
    priceCent: 8900,
    quantity: 1,
    amountCent: 8900,
  };
  const orders = [
    {
      orderNo: 'ORDER2026101300001',
      userId,
      status: 'WAIT_DELIVERY',
      payStatus: 'PAID',
      totalAmountCent: 8900,
      items: [line],
      createTime: '2026-10-13T10:30:00',
    },
  ];
  const inventory = new Map(
    products.map((p) => [
      p.skuId,
      { skuId: p.skuId, total: 100, available: 99, reserved: 0, sold: 1 },
    ]),
  );
  const submissions = new Map(),
    receivedTokens = [],
    payments = [];
  function settle(items) {
    const lines = items.map((item) => {
      const p = products.find((p) => p.skuId === item.skuId);
      if (!p) throw new Error('商品不存在');
      return {
        ...item,
        skuName: p.skuName,
        priceCent: p.priceCent,
        amountCent: p.priceCent * item.quantity,
      };
    });
    return { items: lines, totalAmountCent: lines.reduce((sum, item) => sum + item.amountCent, 0) };
  }
  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1'),
      path = url.pathname,
      method = req.method;
    let text = '';
    for await (const chunk of req) text += chunk;
    const body = text ? JSON.parse(text) : undefined;
    const respond = (data, status = 200, code = 'OK', message = 'success') => {
      res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ code, message, data, traceId: 'frontend-test-fixture' }));
    };
    const error = (message, status = 400, code = 'VALIDATION_FAILED') =>
      respond(null, status, code, message);
    try {
      if (path === '/api/auth/register' || path === '/api/auth/login') {
        if (path.endsWith('register'))
          users.set(body.username, {
            userId,
            username: body.username,
            role: 'USER',
            accessToken: `fixture-${body.username}`,
          });
        const user = users.get(body.username);
        return user ? respond(user) : error('用户名或密码错误', 401, 'UNAUTHORIZED');
      }
      if (path === '/api/products')
        return respond(
          products.filter(
            (p) =>
              p.onShelf &&
              (!url.searchParams.get('keyword') ||
                p.skuName.includes(url.searchParams.get('keyword'))),
          ),
        );
      if (path.startsWith('/api/skus/'))
        return respond(products.find((p) => p.skuId === path.split('/').at(-1)));
      const token = req.headers.authorization?.replace('Bearer ', '');
      const user = [...users.values()].find((u) => u.accessToken === token);
      if (!user) return error('请先登录', 401, 'UNAUTHORIZED');
      if (path.startsWith('/api/admin/') && user.role !== 'ADMIN')
        return error('无管理员权限', 403, 'FORBIDDEN');
      if (path === '/api/users/me') return respond(user);
      if (path === '/api/payments/methods')
        return respond([
          {
            code: 'ALIPAY_PAGE',
            name: '支付宝电脑网站支付',
            available: false,
            reason: '待商户账号/API 配置与渠道联调后开放',
            actionType: 'REDIRECT',
          },
          {
            code: 'WECHAT_NATIVE',
            name: '微信扫码支付',
            available: false,
            reason: '待商户账号/API 配置与渠道联调后开放',
            actionType: 'QR_CODE',
          },
        ]);
      if (path === '/api/orders/settlement') return respond(settle(body.items));
      if (path === '/api/orders' && method === 'POST') {
        const token = req.headers['x-idempotency-token'];
        receivedTokens.push(token);
        if (submissions.has(token)) return respond(submissions.get(token));
        const order = {
          orderNo: `ORDER20261013${String(orders.length + 1).padStart(5, '0')}`,
          userId: user.userId,
          status: 'WAIT_PAY',
          payStatus: 'UNPAID',
          ...settle(body.items),
          createTime: new Date().toISOString(),
        };
        orders.unshift(order);
        submissions.set(token, order);
        return respond(order);
      }
      if (path === '/api/orders') return respond(orders.filter((o) => o.userId === user.userId));
      if (path === '/api/admin/orders')
        return respond(
          orders
            .filter(
              (o) => !url.searchParams.get('status') || o.status === url.searchParams.get('status'),
            )
            .map((o) => ({
              order_no: o.orderNo,
              user_id: o.userId,
              status: o.status,
              pay_status: o.payStatus,
              total_amount_cent: o.totalAmountCent,
              first_item_name: o.items[0].skuName,
              item_count: o.items.reduce((sum, i) => sum + i.quantity, 0),
              create_time: o.createTime,
            })),
        );
      if (path.startsWith('/api/admin/order-operations/') || path.startsWith('/api/orders/')) {
        const parts = path.split('/'),
          no = path.startsWith('/api/admin/') ? parts[4] : parts[3],
          operation = parts.at(-1);
        const order = orders.find((o) => o.orderNo === no);
        if (!order) return error('订单不存在', 404);
        if (user.role !== 'ADMIN' && order.userId !== user.userId) return error('无权访问', 403);
        if (method === 'GET') return respond(order);
        if (operation === 'cancel') order.status = 'CANCELED';
        if (operation === 'deliver') order.status = 'WAIT_RECEIVE';
        if (operation === 'receive') order.status = 'COMPLETED';
        if (operation === 'pay') {
          payments.push(body);
          return error('支付方式暂不可用');
        }
        return respond(null);
      }
      if (path === '/api/admin/products' && method === 'GET')
        return respond(
          products.filter(
            (p) =>
              !url.searchParams.get('keyword') ||
              p.skuName.includes(url.searchParams.get('keyword')),
          ),
        );
      if (path === '/api/admin/products' && method === 'POST') {
        const id = String(90071992547410000n + BigInt(products.length));
        const product = {
          skuId: id,
          spuId: id,
          spuName: body.name,
          skuName: body.skuName,
          priceCent: body.priceCent,
          onShelf: body.onShelf,
        };
        products.push(product);
        inventory.set(id, { skuId: id, total: 0, available: 0, reserved: 0, sold: 0 });
        return respond(product);
      }
      if (path.startsWith('/api/admin/products/') && method === 'PUT') {
        const p = products.find((p) => p.skuId === path.split('/').at(-1));
        Object.assign(p, {
          spuName: body.name,
          skuName: body.skuName,
          priceCent: body.priceCent,
          onShelf: body.onShelf,
        });
        return respond(p);
      }
      if (path.startsWith('/api/admin/inventory/')) {
        const id = path.split('/').at(-1),
          stock = inventory.get(id);
        if (method === 'PUT') {
          if (body.total < stock.reserved + stock.sold)
            return error('库存总量不能低于预留和已售数量');
          stock.total = body.total;
          stock.available = stock.total - stock.reserved - stock.sold;
        }
        return respond(stock);
      }
      return error('测试接口不存在', 404);
    } catch (e) {
      return error(e.message, 500);
    }
  });
  server.listen(port, '127.0.0.1');
  return {
    server,
    state: { products, orders, inventory, receivedTokens, payments, skuId, userId },
  };
}
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  createFixtures(Number(process.env.FIXTURE_PORT || 18080));
  console.log(
    'Frontend TEST FIXTURES on http://127.0.0.1:18080 — no real payments or real backend.',
  );
}
