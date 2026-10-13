package com.acme.order.order;

import java.util.Map;

import com.acme.order.common.mq.MqConsumeGuard;
import com.acme.order.common.mq.RabbitTopology;
import com.acme.order.common.observability.OrderMetrics;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消费支付成功事件并推进订单状态。
 *
 * @author heyu
 * @since 2026-08-12
 */
@Component
public class PaymentSucceededConsumer {

    private final MqConsumeGuard guard;
    private final OrderService service;
    private final OrderMetrics metrics;

    public PaymentSucceededConsumer(MqConsumeGuard g, OrderService s, OrderMetrics metrics) {
        guard = g;
        service = s;
        this.metrics = metrics;
    }

    @Transactional
    @RabbitListener(queues = RabbitTopology.PAYMENT_QUEUE)
    public void consume(Map<String, Object> event) {
        String id = String.valueOf(event.get("eventId"));
        Map<?, ?> payload = (Map<?, ?>) event.get("payload");
        String no = String.valueOf(payload.get("orderNo"));
        long user = ((Number) payload.get("userId")).longValue();
        try {
            if (!guard.first("order-payment", id, user)) {
                return;
            }
            service.paymentSucceeded(no, user);
            guard.success("order-payment", id, user);
        } catch (RuntimeException ex) {
            metrics.increment("mq_consume_failure_total", "consumer", "order-payment");
            throw ex;
        }
    }
}
