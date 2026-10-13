package com.acme.order.query;

import java.util.Map;

import com.acme.order.common.mq.MqConsumeGuard;
import com.acme.order.common.mq.RabbitTopology;
import com.acme.order.common.observability.OrderMetrics;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消费订单事件并维护查询投影。
 *
 * @author heyu
 * @since 2026-08-27
 */
@Component
public class OrderProjectionConsumer {

    private final JdbcTemplate jdbc;
    private final MqConsumeGuard guard;
    private final OrderMetrics metrics;

    public OrderProjectionConsumer(JdbcTemplate j, MqConsumeGuard g, OrderMetrics metrics) {
        jdbc = j;
        guard = g;
        this.metrics = metrics;
    }

    @Transactional
    @RabbitListener(queues = RabbitTopology.QUERY_QUEUE)
    public void consume(Map<String, Object> e) {
        try {
            consumeInTransaction(e);
        } catch (RuntimeException ex) {
            metrics.increment("mq_consume_failure_total", "consumer", "query-projection");
            throw ex;
        }
    }

    private void consumeInTransaction(Map<String, Object> e) {
        String id = String.valueOf(e.get("eventId"));
        String type = String.valueOf(e.get("eventType"));
        Map<?, ?> payload = (Map<?, ?>) e.get("payload");
        String no = String.valueOf(payload.get("orderNo"));
        if (!guard.first("query-projection", id)) {
            return;
        }
        String status = switch (type) {
            case "OrderCreated" -> "WAIT_PAY";
            case "OrderPaid" -> "WAIT_DELIVERY";
            case "OrderDelivered" -> "WAIT_RECEIVE";
            case "OrderCanceled" -> "CANCELED";
            case "OrderCompleted" -> "COMPLETED";
            default -> null;
        };
        if (status == null) {
            guard.success("query-projection", id);
            return;
        }
        long user = number(payload.get("userId"));
        long amount = number(payload.get("totalAmountCent"));
        long payAmount = "OrderPaid".equals(type) ? amount : 0;
        int itemCount = (int) number(payload.get("itemCount"));
        int skuCount = (int) number(payload.get("skuCount"));
        String firstItemName = payload.get("firstItemName") == null ? null : String.valueOf(payload.get("firstItemName"));
        boolean paid = "OrderPaid".equals(type);
        boolean canceled = "OrderCanceled".equals(type);
        int eventVersion = e.get("eventVersion") instanceof Number version ? version.intValue() : 1;
        // 状态只向更高版本推进；支付成功事实和不可变的创建快照允许迟到补全。
        jdbc.update(
            "INSERT INTO order_query_projection(order_no,user_id,status,pay_status,total_amount_cent,pay_amount_cent,"
                + "item_count,sku_count,first_item_name,event_version,pay_time,cancel_time,create_time,update_time) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,CASE WHEN ? THEN NOW(3) END,CASE WHEN ? THEN NOW(3) END,NOW(3),NOW(3)) "
                + "ON DUPLICATE KEY UPDATE "
                + "status=CASE WHEN VALUES(event_version)>=event_version THEN VALUES(status) ELSE status END,"
                + "pay_status=CASE WHEN VALUES(pay_status)='PAID' THEN 'PAID' ELSE pay_status END,"
                + "pay_amount_cent=GREATEST(pay_amount_cent,VALUES(pay_amount_cent)),"
                + "user_id=CASE WHEN user_id=0 THEN VALUES(user_id) ELSE user_id END,"
                + "total_amount_cent=CASE WHEN total_amount_cent=0 THEN VALUES(total_amount_cent) "
                + "ELSE total_amount_cent END,"
                + "item_count=CASE WHEN item_count=0 THEN VALUES(item_count) ELSE item_count END,"
                + "sku_count=CASE WHEN sku_count=0 THEN VALUES(sku_count) ELSE sku_count END,"
                + "first_item_name=COALESCE(first_item_name,VALUES(first_item_name)),"
                + "pay_time=COALESCE(pay_time,VALUES(pay_time)),"
                + "cancel_time=CASE WHEN VALUES(event_version)>=event_version "
                + "THEN COALESCE(VALUES(cancel_time),cancel_time) ELSE cancel_time END,"
                + "event_version=GREATEST(event_version,VALUES(event_version)),update_time=NOW(3)",
            no, user, status, paid ? "PAID" : "UNPAID", amount, payAmount, itemCount, skuCount, firstItemName,
            eventVersion, paid, canceled);
        guard.success("query-projection", id);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }
}
