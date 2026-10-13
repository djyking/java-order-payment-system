package com.acme.order.order;

import java.util.List;
import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.acme.order.api.inventory.InventoryDtos.Line;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import com.acme.order.common.core.Ids;
import com.acme.order.common.redis.IdempotencyTokenService;
import com.acme.order.common.observability.OrderMetrics;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编排订单结算、创建和状态流转业务。
 *
 * @author heyu
 * @since 2026-08-12
 */
@Service
public class OrderService {

    private final RemoteClients remote;
    private final OrderRepository repo;
    private final OrderStateMachine machine;
    private final IdempotencyTokenService tokens;
    private final OrderTxWriter writer;
    private final OrderMetrics metrics;

    @Value("${debug.failure.inventory-after-reserve:false}")
    private boolean failAfterInventoryReserve;

    public OrderService(RemoteClients r, OrderRepository p, OrderStateMachine m, IdempotencyTokenService t,
        OrderTxWriter w, OrderMetrics metrics) {
        remote = r;
        repo = p;
        machine = m;
        tokens = t;
        writer = w;
        this.metrics = metrics;
    }

    public OrderDtos.SettlementView settle(List<OrderDtos.Item> items) {
        validateItems(items);
        var lines = items.stream().map(i -> {
            var sku = remote.sku(i.skuId());
            long amount = Math.multiplyExact(sku.priceCent(), i.quantity());
            return new OrderDtos.SettlementLine(sku.skuId(), sku.skuName(), sku.priceCent(), i.quantity(), amount);
        }).toList();
        return new OrderDtos.SettlementView(lines,
            lines.stream().mapToLong(OrderDtos.SettlementLine::amountCent).reduce(0, Math::addExact));
    }

    @SentinelResource(value = "order.create", blockHandler = "createBlocked")
    public OrderDtos.OrderView create(long user, String token, List<OrderDtos.Item> requested) {
        metrics.increment("order_create_total");
        if (token == null || !token.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "下单令牌应为 8 到 64 位字母、数字、下划线或短横线");
        }
        String fingerprint = fingerprint(requested);
        String completed = repo.completedRequest(user, token, fingerprint);
        if (completed != null) { return view(completed, user); }
        if (!tokens.acquire(user, token)) {
            completed = repo.completedRequest(user, token, fingerprint);
            if (completed != null) { return view(completed, user); }
            throw new BizException(ErrorCode.ORDER_DUPLICATE_SUBMIT, "订单正在处理中，请使用同一下单令牌重试");
        }
        long id = Ids.next();
        String no = Ids.orderNo(id);
        boolean reserved = false;
        try {
            var quote = settle(requested);
            remote.reserve(no, user, requested.stream().map(i -> new Line(i.skuId(), i.quantity())).toList());
            reserved = true;
            if (failAfterInventoryReserve) {
                throw new IllegalStateException("fault injection: inventory reserved before order persistence");
            }
            writer.persist(id, no, user, quote, token, fingerprint);
        } catch (Exception e) {
            metrics.failure("order_create");
            if (reserved) {
                try {
                    remote.release(no);
                } catch (Exception ignored) {
                    // 主异常仍需向上抛出，库存释放由补偿任务继续处理。
                }
            }
            try { tokens.release(user, token); } catch (Exception ignored) { /* 数据库映射为最终幂等依据。 */ }
            if (e instanceof org.springframework.dao.DuplicateKeyException) {
                completed = repo.completedRequest(user, token, fingerprint);
                if (completed != null) { return view(completed, user); }
            }
            throw e;
        }
        // 此时订单事务已经提交；缓存或响应查询失败均不得释放已提交订单的库存。
        try { tokens.complete(user, token, no); } catch (Exception ignored) { /* 可由持久化请求映射恢复。 */ }
        metrics.success("order_create");
        return view(no, user);
    }

    public OrderDtos.OrderView createBlocked(long user, String token, List<OrderDtos.Item> requested,
        BlockException cause) {
        throw new BizException(ErrorCode.SYSTEM_BUSY, "订单创建请求过多，请稍后重试");
    }

    public OrderDtos.OrderView view(String no, long user) {
        var s = repo.get(no, user);
        return new OrderDtos.OrderView(no, user, s.status().name(), s.payStatus() == 20 ? "PAID" : "UNPAID", s.total(),
            repo.items(s), s.created());
    }

    public List<OrderDtos.OrderView> list(long user, int page, int size) {
        int safe = Math.min(Math.max(size, 1), 100);
        return repo.list(user, Math.max(page, 0), safe).stream().map(x -> view(x.no(), user)).toList();
    }

    @Transactional
    public void cancel(String no, long user, String reason) {
        var s = repo.get(no, user);
        if (s.status() == OrderStatus.CANCELED) {
            return;
        }
        machine.require(s.status(), OrderStatus.CANCELED);
        if (!repo.transit(s, OrderStatus.CANCELED, "USER_CANCEL", "OrderCanceled")) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单状态已变化");
        }
    }

    public OrderDtos.PayView pay(String no, long user, String method) {
        var s = repo.get(no, user);
        if (s.status() != OrderStatus.WAIT_PAY) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "仅待支付订单可支付");
        }
        var p = remote.createPayment(no, user, s.total(), method);
        return new OrderDtos.PayView(p.payOrderNo(), p.status(), p.methodCode(), p.actionType(),
            p.payUrl(), p.qrCode(), p.expiresAt());
    }

    @Transactional
    public void deliver(String no, long user, long operatorId) {
        var snap = repo.get(no, user);
        if (snap.status() == OrderStatus.WAIT_RECEIVE) { return; }
        machine.require(snap.status(), OrderStatus.WAIT_RECEIVE);
        if (!repo.transit(snap, OrderStatus.WAIT_RECEIVE, "DELIVER", "OrderDelivered", operatorId)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单状态已变化");
        }
    }

    @Transactional
    public void receive(String no, long user) {
        transit(no, user, OrderStatus.WAIT_RECEIVE, OrderStatus.COMPLETED, "RECEIVE", "OrderCompleted");
    }

    private void transit(String no, long user, OrderStatus expected, OrderStatus target, String op, String event) {
        var snap = repo.get(no, user);
        if (snap.status() != expected) {
            machine.require(snap.status(), target);
        }
        machine.require(snap.status(), target);
        if (!repo.transit(snap, target, op, event)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单状态已变化");
        }
    }

    @Transactional
    public void paymentSucceeded(String no, long user) {
        var s = repo.get(no, user);
        if (s.status() == OrderStatus.WAIT_DELIVERY) {
            return;
        }
        repo.markPaid(s);
    }

    @Transactional
    public void closeExpired() {
        for (var s : repo.expired()) {
            if (repo.transit(s, OrderStatus.CANCELED, "TIMEOUT_CANCEL", "OrderCanceled")) {
                metrics.increment("order_timeout_close_total");
            }
        }
    }

    @Transactional
    public void closeFromDelay(String no, long user) {
        var snapshot = repo.get(no, user);
        if (snapshot.status() == OrderStatus.WAIT_PAY) {
            if (repo.transit(snapshot, OrderStatus.CANCELED, "MQ_TIMEOUT_CANCEL", "OrderCanceled")) {
                metrics.increment("order_timeout_close_total");
            }
        }
    }

    private String fingerprint(List<OrderDtos.Item> items) {
        validateItems(items);
        String canonical = items.stream().sorted(Comparator.comparingLong(OrderDtos.Item::skuId))
            .map(item -> item.skuId() + ":" + item.quantity()).collect(java.util.stream.Collectors.joining(","));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void validateItems(List<OrderDtos.Item> items) {
        if (items == null || items.isEmpty() || items.size() > 100
            || items.stream().anyMatch(i -> i == null || i.skuId() <= 0 || i.quantity() < 1 || i.quantity() > 999)
            || items.stream().map(OrderDtos.Item::skuId).distinct().count() != items.size()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请提交 1 到 100 个不同商品，单项数量为 1 到 999");
        }
    }
}
