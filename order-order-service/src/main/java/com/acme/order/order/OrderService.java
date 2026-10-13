package com.acme.order.order;

import java.util.List;

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
        var lines = items.stream().map(i -> {
            var sku = remote.sku(i.skuId());
            long amount = Math.multiplyExact(sku.priceCent(), i.quantity());
            return new OrderDtos.SettlementLine(sku.skuId(), sku.skuName(), sku.priceCent(), i.quantity(), amount);
        }).toList();
        return new OrderDtos.SettlementView(lines,
            lines.stream().mapToLong(OrderDtos.SettlementLine::amountCent).sum());
    }

    @SentinelResource(value = "order.create", blockHandler = "createBlocked")
    public OrderDtos.OrderView create(long user, String token, List<OrderDtos.Item> requested) {
        metrics.increment("order_create_total");
        if (!tokens.acquire(user, token)) {
            throw new BizException(ErrorCode.ORDER_DUPLICATE_SUBMIT, "请勿重复提交");
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
            writer.persist(id, no, user, quote);
            tokens.complete(user, token, no);
            metrics.success("order_create");
            return view(no, user);
        } catch (Exception e) {
            metrics.failure("order_create");
            if (reserved) {
                try {
                    remote.release(no);
                } catch (Exception ignored) {
                    // 主异常仍需向上抛出，库存释放由补偿任务继续处理。
                }
            }
            tokens.release(user, token);
            throw e;
        }
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

    public OrderDtos.PayView pay(String no, long user) {
        var s = repo.get(no, user);
        if (s.status() != OrderStatus.WAIT_PAY) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "仅待支付订单可支付");
        }
        var p = remote.createPayment(no, user, s.total());
        return new OrderDtos.PayView(p.payOrderNo(), p.status());
    }

    @Transactional
    public void deliver(String no, long user) {
        transit(no, user, OrderStatus.WAIT_DELIVERY, OrderStatus.WAIT_RECEIVE, "DELIVER", "OrderDelivered");
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
}
