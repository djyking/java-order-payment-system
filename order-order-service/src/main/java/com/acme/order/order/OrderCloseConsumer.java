package com.acme.order.order;

import java.util.Map;

import com.acme.order.common.mq.MqConsumeGuard;
import com.acme.order.common.mq.RabbitTopology;
import com.acme.order.common.observability.OrderMetrics;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消费 TTL 队列到期后的关单事件；数据库扫描任务继续作为最终兜底。
 *
 * @author heyu
 * @since 2026-09-02
 */
@Component
public class OrderCloseConsumer {

    private final MqConsumeGuard guard;
    private final OrderService service;
    private final OrderMetrics metrics;

    public OrderCloseConsumer(MqConsumeGuard guard, OrderService service, OrderMetrics metrics) {
        this.guard = guard;
        this.service = service;
        this.metrics = metrics;
    }

    @Transactional
    @RabbitListener(queues = RabbitTopology.CLOSE_QUEUE)
    public void consume(Map<String, Object> event) {
        String eventId = String.valueOf(event.get("eventId")) + ":close";
        Map<?, ?> payload = (Map<?, ?>) event.get("payload");
        long user = ((Number) payload.get("userId")).longValue();
        try {
            if (!guard.first("order-close", eventId, user)) {
                return;
            }
            service.closeFromDelay(String.valueOf(payload.get("orderNo")), user);
            guard.success("order-close", eventId, user);
        } catch (RuntimeException ex) {
            metrics.increment("mq_consume_failure_total", "consumer", "order-close");
            throw ex;
        }
    }
}
