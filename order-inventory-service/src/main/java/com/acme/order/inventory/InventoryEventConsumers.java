package com.acme.order.inventory;

import java.util.Map;

import com.acme.order.common.mq.MqConsumeGuard;
import com.acme.order.common.mq.RabbitTopology;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import com.acme.order.common.observability.OrderMetrics;

/**
 * 消费订单领域事件并同步库存状态。
 *
 * @author heyu
 * @since 2026-08-02
 */
@Component
public class InventoryEventConsumers {

    private final InventoryService service;
    private final MqConsumeGuard guard;

    private final OrderMetrics metrics;

    @Value("${debug.failure.consumer-error:false}")
    private boolean consumerError;

    public InventoryEventConsumers(InventoryService s, MqConsumeGuard g, OrderMetrics metrics) {
        service = s;
        guard = g;
        this.metrics = metrics;
    }

    @Transactional
    @RabbitListener(queues = RabbitTopology.CONFIRM_QUEUE)
    public void confirm(Map<String, Object> e) {
        handle(e, "inventory-confirm", true);
    }

    @Transactional
    @RabbitListener(queues = RabbitTopology.RELEASE_QUEUE)
    public void release(Map<String, Object> e) {
        handle(e, "inventory-release", false);
    }

    private void handle(Map<String, Object> e, String group, boolean confirm) {
        try {
            if (consumerError) {
                throw new IllegalStateException("fault injection: inventory consumer error");
            }
            String event = String.valueOf(e.get("eventId"));
            Map<?, ?> payload = (Map<?, ?>) e.get("payload");
            String order = String.valueOf(payload.get("orderNo"));
            long user = payload.get("userId") instanceof Number value ? value.longValue() : 0L;
            if (!guard.first(group, event, user)) {
                return;
            }
            if (confirm) {
                service.confirm(order);
            } else {
                service.release(order);
            }
            guard.success(group, event, user);
        } catch (RuntimeException ex) {
            metrics.increment("mq_consume_failure_total", "consumer", group);
            throw ex;
        }
    }
}
