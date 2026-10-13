package com.acme.order.payment;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.acme.order.common.mq.RabbitTopology;
import com.acme.order.common.observability.OrderMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 定时发布支付本地消息表中的待发送事件。
 *
 * @author heyu
 * @since 2026-08-20
 */
@Component
public class PaymentOutboxPublisher {

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final OrderMetrics metrics;

    public PaymentOutboxPublisher(JdbcTemplate j, RabbitTemplate r, ObjectMapper m, MeterRegistry registry,
        OrderMetrics metrics) {
        jdbc = j;
        rabbit = r;
        mapper = m;
        this.metrics = metrics;
        Gauge.builder("outbox_pending_count", jdbc, x -> count(x, "event_status IN(0,5,20)"))
            .strongReference(true).register(registry);
        Gauge.builder("outbox_failed_count", jdbc, x -> count(x, "event_status=30"))
            .strongReference(true).register(registry);
    }

    @Scheduled(fixedDelayString = "${outbox.delay-ms:1000}")
    public void run() {
        var list = jdbc.query(
            "SELECT id,event_id,aggregate_id,payload FROM outbox_event "
                + "WHERE (event_status IN(0,20) AND next_retry_time<=NOW(3)) "
                + "OR (event_status=5 AND update_time<DATE_SUB(NOW(3),INTERVAL 1 MINUTE)) "
                + "ORDER BY id LIMIT 100",
            (x, n) -> Map.of("id", x.getLong(1), "event", x.getString(2), "aggregate", x.getString(3), "payload",
                x.getString(4)));
        for (var e : list) {
            try {
                if (jdbc.update(
                    "UPDATE outbox_event SET event_status=5,update_time=NOW(3) WHERE id=? "
                        + "AND (event_status IN(0,20) OR (event_status=5 "
                        + "AND update_time<DATE_SUB(NOW(3),INTERVAL 1 MINUTE)))",
                    e.get("id")) != 1) {
                    continue;
                }
                var correlation = new CorrelationData((String) e.get("event"));
                rabbit.convertAndSend(RabbitTopology.EXCHANGE, "payment.succeeded",
                    Map.of("eventId", e.get("event"), "eventType", "PaymentSucceeded", "eventVersion", 1, "occurredAt",
                        OffsetDateTime.now().toString(), "producer", "order-payment-service", "aggregateId",
                        e.get("aggregate"), "payload", mapper.readTree((String) e.get("payload"))),
                    correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException(confirm.getReason());
                }
                jdbc.update(
                    "UPDATE outbox_event SET event_status=10,published_time=NOW(3),update_time=NOW(3) WHERE id=?",
                    e.get("id"));
            } catch (Exception ex) {
                metrics.increment("mq_publish_failure_total");
                jdbc.update(
                    "UPDATE outbox_event SET event_status=IF(retry_count+1>=10,30,20),retry_count=retry_count+1,"
                        + "next_retry_time=DATE_ADD(NOW(),INTERVAL CASE retry_count "
                        + "WHEN 0 THEN 1 WHEN 1 THEN 5 WHEN 2 THEN 30 WHEN 3 THEN 120 ELSE 600 END SECOND),"
                        + "last_error=?,update_time=NOW(3) WHERE id=?",
                    ex.getMessage(), e.get("id"));
            }
        }
    }

    private static double count(JdbcTemplate jdbc, String predicate) {
        try {
            Long value = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE " + predicate, Long.class);
            return value == null ? 0 : value.doubleValue();
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }
}
