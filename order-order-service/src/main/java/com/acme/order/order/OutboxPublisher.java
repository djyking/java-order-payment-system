package com.acme.order.order;

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
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 定时发布订单本地消息表中的待发送事件。
 *
 * @author heyu
 * @since 2026-08-12
 */
@Component
public class OutboxPublisher {

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final OrderMetrics metrics;

    @Value("${debug.failure.mq-publish:false}")
    private boolean failMqPublish;

    public OutboxPublisher(JdbcTemplate j, RabbitTemplate r, ObjectMapper m, MeterRegistry registry,
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
    public void publish() {
        var rows = jdbc.query(
            "SELECT id,user_id,event_id,event_type,aggregate_id,payload FROM outbox_event "
                + "WHERE (event_status IN (0,20) AND next_retry_time<=NOW(3)) "
                + "OR (event_status=5 AND update_time<DATE_SUB(NOW(3),INTERVAL 1 MINUTE)) "
                + "ORDER BY id LIMIT 100",
            (x, n) -> Map.of("id", x.getLong(1), "userId", x.getLong(2), "eventId", x.getString(3), "type",
                x.getString(4), "aggregate", x.getString(5), "payload", x.getString(6)));
        for (var e : rows) {
            try {
                if (jdbc.update(
                    "UPDATE outbox_event SET event_status=5,update_time=NOW(3) WHERE id=? AND user_id=? "
                        + "AND (event_status IN(0,20) OR (event_status=5 "
                        + "AND update_time<DATE_SUB(NOW(3),INTERVAL 1 MINUTE)))",
                    e.get("id"), e.get("userId")) != 1) {
                    continue;
                }
                String key = switch ((String) e.get("type")) {
                    case "OrderPaid" -> "order.paid";
                    case "OrderCanceled" -> "order.canceled";
                    case "OrderDelivered" -> "order.delivered";
                    case "OrderCompleted" -> "order.completed";
                    default -> "order.created";
                };
                var payload = mapper.readTree((String) e.get("payload"));
                int eventVersion = payload.path("orderVersion").asInt(1);
                var envelope = Map.of("eventId", e.get("eventId"), "eventType", e.get("type"), "eventVersion",
                    eventVersion,
                    "occurredAt", OffsetDateTime.now().toString(), "producer", "order-order-service", "aggregateId",
                    e.get("aggregate"), "payload", payload);
                var correlation = new CorrelationData((String) e.get("eventId"));
                if (failMqPublish) {
                    throw new IllegalStateException("fault injection: RabbitMQ publish unavailable");
                }
                rabbit.convertAndSend(RabbitTopology.EXCHANGE, key, envelope, correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException(confirm.getReason());
                }
                jdbc.update(
                    "UPDATE outbox_event SET event_status=10,published_time=NOW(3),update_time=NOW(3) "
                        + "WHERE id=? AND user_id=?",
                    e.get("id"), e.get("userId"));
            } catch (Exception ex) {
                metrics.increment("mq_publish_failure_total");
                jdbc.update(
                    "UPDATE outbox_event SET event_status=IF(retry_count+1>=10,30,20),retry_count=retry_count+1,"
                        + "next_retry_time=DATE_ADD(NOW(),INTERVAL CASE retry_count "
                        + "WHEN 0 THEN 1 WHEN 1 THEN 5 WHEN 2 THEN 30 WHEN 3 THEN 120 ELSE 600 END SECOND),"
                        + "last_error=?,update_time=NOW(3) WHERE id=? AND user_id=?",
                    ex.getMessage(), e.get("id"), e.get("userId"));
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
