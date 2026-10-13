package com.acme.order.order;

import java.util.ArrayList;
import java.util.List;

import com.acme.order.common.core.Ids;
import com.acme.order.common.observability.OrderMetrics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

/**
 * 对订单、支付、库存和 Outbox 的跨域最终一致性进行只读核对并登记异常。
 *
 * @author heyu
 * @since 2026-09-02
 */
@Component
@Profile("!sharding")
public class ReconciliationJob {

    private final JdbcTemplate jdbc;
    private final OrderMetrics metrics;

    public ReconciliationJob(JdbcTemplate jdbc, OrderMetrics metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${jobs.reconciliation.delay-ms:300000}")
    @Transactional
    public void scan() {
        List<Issue> issues = new ArrayList<>();
        issues.addAll(jdbc.query(
            "SELECT user_id,aggregate_id,IF(event_status=30,'OUTBOX_FAILED','OUTBOX_STUCK'),"
                + "CONCAT(event_type,': retry=',retry_count,', error=',COALESCE(last_error,'')) "
                + "FROM outbox_event WHERE event_status=30 OR (event_status IN(0,5,20) "
                + "AND update_time<DATE_SUB(NOW(3),INTERVAL 10 MINUTE)) LIMIT 100",
            (r, n) -> new Issue(r.getLong(1), r.getString(2), r.getString(3), r.getString(4))));
        issues.addAll(jdbc.query(
            "SELECT o.user_id,o.order_no,'PAID_ORDER_STATE_MISMATCH',"
                + "CONCAT('orderStatus=',o.status,', payStatus=',o.pay_status) FROM trade_order o "
                + "JOIN order_payment.pay_order p ON p.biz_order_no=o.order_no "
                + "WHERE p.status=20 AND (o.pay_status<>20 OR o.status IN(10,90)) LIMIT 100",
            (r, n) -> new Issue(r.getLong(1), r.getString(2), r.getString(3), r.getString(4))));
        issues.addAll(jdbc.query(
            "SELECT o.user_id,o.order_no,'INVENTORY_STATE_MISMATCH',"
                + "CONCAT('orderStatus=',o.status,', reservationStatus=',i.status) FROM trade_order o "
                + "JOIN order_inventory.inventory_reservation i ON i.order_no=o.order_no "
                + "WHERE ((o.status=20 AND i.status=10) OR (o.status=90 AND i.status=10)) "
                + "AND o.update_time<DATE_SUB(NOW(3),INTERVAL 5 MINUTE) LIMIT 100",
            (r, n) -> new Issue(r.getLong(1), r.getString(2), r.getString(3), r.getString(4))));
        issues.forEach(this::recordIfAbsent);
    }

    private void recordIfAbsent(Issue issue) {
        Integer found = jdbc.queryForObject(
            "SELECT COUNT(*) FROM reconciliation_exception WHERE biz_no=? AND exception_type=? AND status=0",
            Integer.class, issue.bizNo(), issue.type());
        if (found != null && found == 0) {
            jdbc.update(
                "INSERT INTO reconciliation_exception(id,user_id,biz_type,biz_no,exception_type,detail,status,"
                    + "create_time,update_time) VALUES(?,?,'ORDER',?,?,?,0,NOW(3),NOW(3))",
                Ids.next(), issue.userId(), issue.bizNo(), issue.type(), issue.detail());
            metrics.increment("reconciliation_exception_count");
        }
    }

    private record Issue(long userId, String bizNo, String type, String detail) {
    }
}
