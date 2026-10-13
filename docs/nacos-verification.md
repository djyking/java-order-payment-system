# Nacos 接入验收

验收日期：2026-09-04。

## 结论

Nacos 已真实接入，不是仅保留依赖或 YAML。Gateway 路由使用 `lb://`，订单服务使用服务名和 Spring Cloud LoadBalancer 调用商品、库存与支付服务。

## 验收记录

- Nacos：3.0.3，API `127.0.0.1:18848`，控制台 `127.0.0.1:18849`。
- `order-gateway`、user、product、inventory、order、payment、query 共 7 个服务均注册为健康实例。
- 额外启动 Inventory `8183` 后，Nacos 同时返回 `8083`、`8183` 两个健康实例，权重均为 1。
- 从 OrderService 连续创建 8 笔订单，两实例的 `inventory_reserve_success_total` 分别增加 4，证明请求经过服务发现和负载均衡，而不是固定地址调用。
- 8 笔验收订单随后全部取消，库存已释放；临时 8183 实例已停止。

## 生产注意事项

当前 Compose 为本机验收关闭 Nacos 鉴权，且配置 dataId 为空。生产环境必须启用鉴权、命名空间、配置持久化和变更审计。
