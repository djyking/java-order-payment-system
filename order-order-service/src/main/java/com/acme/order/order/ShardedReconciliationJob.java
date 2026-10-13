package com.acme.order.order;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.acme.order.common.core.Ids;
import com.acme.order.common.observability.OrderMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分片模式下逐物理库、逐物理表执行跨域对账，异常登记在订单所在物理库。
 *
 * @author heyu
 * @since 2026-09-02
 */
@Component
@Profile("sharding")
public class ShardedReconciliationJob {

    private final List<Shard> shards;
    private final OrderMetrics metrics;

    public ShardedReconciliationJob(
        @Qualifier("orderShard0DataSource") DataSource shard0,
        @Qualifier("orderShard1DataSource") DataSource shard1,
        OrderMetrics metrics) {
        this.shards = List.of(new Shard("ds0", new JdbcTemplate(shard0)),
            new Shard("ds1", new JdbcTemplate(shard1)));
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${jobs.reconciliation.delay-ms:300000}")
    public void scan() {
        shards.forEach(this::scanShard);
    }

    private void scanShard(Shard shard) {
        List<Issue> issues = new ArrayList<>();
        issues.addAll(shard.jdbc().query(
            "SELECT user_id,aggregate_id,IF(event_status=30,'OUTBOX_FAILED','OUTBOX_STUCK'),"
                + "CONCAT(event_type,': retry=',retry_count,', error=',COALESCE(last_error,'')) "
                + "FROM outbox_event WHERE event_status=30 OR (event_status IN(0,5,20) "
                + "AND update_time<DATE_SUB(NOW(3),INTERVAL 10 MINUTE)) LIMIT 100",
            (r, n) -> new Issue(r.getLong(1), r.getString(2), r.getString(3), r.getString(4))));

        for (int table = 0; table < 8; table++) {
            String orderTable = "trade_order_" + table;
            issues.addAll(shard.jdbc().query(
                "SELECT o.user_id,o.order_no,'PAID_ORDER_STATE_MISMATCH',"
                    + "CONCAT('orderStatus=',o.status,', payStatus=',o.pay_status) FROM " + orderTable + " o "
                    + "JOIN order_payment.pay_order p ON p.biz_order_no=o.order_no "
                    + "WHERE p.status=20 AND (o.pay_status<>20 OR o.status IN(10,90)) LIMIT 100",
                (r, n) -> new Issue(r.getLong(1), r.getString(2), r.getString(3), r.getString(4))));
            issues.addAll(shard.jdbc().query(
                "SELECT o.user_id,o.order_no,'INVENTORY_STATE_MISMATCH',"
                    + "CONCAT('orderStatus=',o.status,', reservationStatus=',i.status) FROM " + orderTable + " o "
                    + "JOIN order_inventory.inventory_reservation i ON i.order_no=o.order_no "
                    + "WHERE ((o.status=20 AND i.status=10) OR (o.status=90 AND i.status=10)) "
                    + "AND o.update_time<DATE_SUB(NOW(3),INTERVAL 5 MINUTE) LIMIT 100",
                (r, n) -> new Issue(r.getLong(1), r.getString(2), r.getString(3), r.getString(4))));
        }
        issues.forEach(issue -> recordIfAbsent(shard, issue));
    }

    private void recordIfAbsent(Shard shard, Issue issue) {
        Integer found = shard.jdbc().queryForObject(
            "SELECT COUNT(*) FROM reconciliation_exception WHERE user_id=? AND biz_no=? "
                + "AND exception_type=? AND status=0",
            Integer.class, issue.userId(), issue.bizNo(), issue.type());
        if (found != null && found == 0) {
            shard.jdbc().update(
                "INSERT INTO reconciliation_exception(id,user_id,biz_type,biz_no,exception_type,detail,status,"
                    + "create_time,update_time) VALUES(?,?,'ORDER',?,?,?,0,NOW(3),NOW(3))",
                Ids.next(), issue.userId(), issue.bizNo(), issue.type(),
                "[" + shard.name() + "] " + issue.detail());
            metrics.increment("reconciliation_exception_count", "shard", shard.name());
        }
    }

    private record Shard(String name, JdbcTemplate jdbc) {
    }

    private record Issue(long userId, String bizNo, String type, String detail) {
    }
}
