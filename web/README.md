# Java 订单支付系统 · Web

Vue 3、TypeScript、Vite 客户端与管理工作台。通过同源 `/api` 访问网关；应用代码不包含测试数据或固定账户。

## 页面

- 客户：注册登录、商品列表与搜索、商品详情、服务端结算、确认下单、订单列表与详情、取消订单、确认收货。
- 收银台：获取可用支付方式、选择渠道、展示跳转链接或二维码、查询订单付款结果。未开放渠道保持禁用，不把页面跳转或按钮点击视为付款成功。
- 管理员：商品新增与编辑、上下架、价格维护、库存初始化与调整、订单查询、订单详情与发货确认。

身份由后端返回，登录会话保存在当前标签页的 `sessionStorage`。管理路由做导航限制，真正权限与订单归属检查由后端执行。用户、商品编号始终按字符串处理；金额使用整数分。创建订单的幂等令牌在网络重试及页面刷新时保持不变，成功获取订单后清除。

## 本地开发

要求 Node.js 22.12+，pnpm 11.25.0（仓库锁定版本）。先按照根目录 README 启动数据库和服务。

```sh
corepack enable
corepack prepare pnpm@11.25.0 --activate
pnpm install --frozen-lockfile
pnpm dev
```

打开 `http://localhost:5173`。开发服务器默认代理 `http://localhost:8080`。网关在其他地址时设置环境变量 `API_PROXY_TARGET` 后启动，例如 PowerShell：

```powershell
$env:API_PROXY_TARGET = 'http://localhost:8080'
pnpm dev
```

首次使用可在页面注册普通用户。管理员由后端 `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` 环境变量初始化，不能从公开注册页面选择管理员角色。

## 构建与验证

```sh
pnpm test
pnpm build
pnpm test:e2e
```

`pnpm build` 先运行 Vue/TypeScript 类型检查，再生成 `dist/`。`pnpm test` 的 9 个单元测试覆盖金额精度、付款链接安全、字符串长编号、幂等重试、认证过期和业务错误处理。

`pnpm test:e2e` 启动 **仅供前端回归的隔离 HTTP 数据服务**，使用临时端口与本地 Vite 15175 端口。12 项浏览器检查覆盖注册登录、角色导航、下单响应丢失后重试、两种支付方式禁用状态、订单取消、管理员发货、商品及库存维护、买家收货和手机布局。截图写入 `tests/results/`（不提交）。Windows 默认使用已安装的 Edge；其他系统先运行 `pnpm exec playwright install chromium`。可通过 `PLAYWRIGHT_CHANNEL` 指定浏览器，例如 `msedge`。这组测试不代表真实数据库、消息队列或真实支付验证。

独立检查测试界面时可运行 `pnpm test:fixtures`（默认 18080；可用 `FIXTURE_PORT` 更改），在另一个终端设置 `API_PROXY_TARGET` 指向该服务后运行 `pnpm dev`。测试用户名 `demo` 与 `admin` 仅属于隔离测试服务；任何密码均只用于该测试数据服务，不能登录真实服务。不要把测试服务用于生产。

## 容器

根目录 `docker/docker-compose.app.yml` 包含 Web 服务。`web/Dockerfile` 构建静态文件，Nginx 提供路由回退并把 `/api/` 转发给 `order-gateway:8080`。图片、第三方字体及浏览器端密钥均不依赖外部资源。

真实支付渠道账号、商户权限、证书及签名验签配置在下一阶段由后端接入；前端通过渠道列表和统一收银台响应适配多种支付方式，无需将密钥放入 `VITE_` 变量。
