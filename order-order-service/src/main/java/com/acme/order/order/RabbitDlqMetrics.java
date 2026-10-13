package com.acme.order.order;

import java.util.List;

import com.acme.order.common.mq.RabbitTopology;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.stereotype.Component;

/**
 * 将订单域关键死信队列深度暴露给 Prometheus。
 *
 * @author heyu
 * @since 2026-09-02
 */
@Component
public class RabbitDlqMetrics {

    private final AmqpAdmin admin;

    public RabbitDlqMetrics(AmqpAdmin admin, MeterRegistry registry) {
        this.admin = admin;
        for (String queue : List.of(RabbitTopology.PAYMENT_DLQ, RabbitTopology.INVENTORY_DLQ,
            RabbitTopology.CLOSE_DLQ, RabbitTopology.QUERY_DLQ)) {
            Gauge.builder("dlq_message_count", this, metrics -> metrics.messages(queue))
                .tag("queue", queue).strongReference(true).register(registry);
        }
    }

    private double messages(String queue) {
        try {
            var information = admin.getQueueInfo(queue);
            return information == null ? 0 : information.getMessageCount();
        } catch (AmqpException ignored) {
            return Double.NaN;
        }
    }
}
