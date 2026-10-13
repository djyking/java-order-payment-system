package com.acme.order.query;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.acme.order.common.mq.MqConsumeGuard;
import com.acme.order.common.observability.OrderMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Executes the real consumer and its upsert SQL against H2's MySQL compatibility mode. */
class OrderProjectionConsumerTest {

    private JdbcTemplate jdbc;
    private OrderProjectionConsumer consumer;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.execute("""
            CREATE TABLE order_query_projection (
                order_no VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL DEFAULT 0,
                status VARCHAR(32) NOT NULL, pay_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
                total_amount_cent BIGINT NOT NULL DEFAULT 0, pay_amount_cent BIGINT NOT NULL DEFAULT 0,
                item_count INT NOT NULL DEFAULT 0, sku_count INT NOT NULL DEFAULT 0,
                first_item_name VARCHAR(128), event_version INT NOT NULL DEFAULT 0,
                pay_time DATETIME(3), cancel_time DATETIME(3),
                create_time DATETIME(3) NOT NULL, update_time DATETIME(3) NOT NULL
            )
            """);
        jdbc.execute("""
            CREATE TABLE mq_consume_log (
                id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL DEFAULT 0,
                consumer_group VARCHAR(64) NOT NULL, event_id VARCHAR(64) NOT NULL,
                consume_status TINYINT NOT NULL, create_time DATETIME(3), update_time DATETIME(3),
                UNIQUE (consumer_group, event_id)
            )
            """);
        consumer = new OrderProjectionConsumer(jdbc, new MqConsumeGuard(jdbc),
            new OrderMetrics(new SimpleMeterRegistry()));
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void delayedPaymentFillsPaymentFactWithoutRollingBackDelivery() {
        consume(event("OrderDelivered", 2));
        consume(event("OrderPaid", 1));

        var row = row();
        assertEquals("WAIT_RECEIVE", row.get("status"));
        assertEquals(2, ((Number) row.get("event_version")).intValue());
        assertEquals("PAID", row.get("pay_status"));
        assertEquals(9900L, ((Number) row.get("pay_amount_cent")).longValue());
        assertNotNull(row.get("pay_time"));
    }

    @Test
    void delayedCreationFillsSnapshotWithoutRollingBackPayment() {
        consume(event("OrderPaid", 1));
        consume(event("OrderCreated", 0));

        var row = row();
        assertEquals("WAIT_DELIVERY", row.get("status"));
        assertEquals("PAID", row.get("pay_status"));
        assertEquals(1, ((Number) row.get("event_version")).intValue());
        assertSnapshot(row);
    }

    @Test
    void completedOrderRetainsFactsAcrossOlderAndDuplicateEvents() {
        var created = event("OrderCreated", 0);
        consume(created);
        consume(event("OrderPaid", 1));
        consume(event("OrderCompleted", 3));
        consume(event("OrderDelivered", 2));
        consume(created);

        var row = row();
        assertEquals("COMPLETED", row.get("status"));
        assertEquals(3, ((Number) row.get("event_version")).intValue());
        assertEquals("PAID", row.get("pay_status"));
        assertEquals(9900L, ((Number) row.get("pay_amount_cent")).longValue());
        assertSnapshot(row);
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM mq_consume_log WHERE consume_status=1",
            Integer.class));
    }

    @Test
    void delayedCreationDoesNotReopenCanceledOrder() {
        consume(event("OrderCanceled", 1));
        consume(event("OrderCreated", 0));

        var row = row();
        assertEquals("CANCELED", row.get("status"));
        assertEquals("UNPAID", row.get("pay_status"));
        assertNotNull(row.get("cancel_time"));
        assertSnapshot(row);
    }

    private void consume(Map<String, Object> event) {
        transaction.executeWithoutResult(ignored -> consumer.consume(event));
    }

    private Map<String, Object> event(String type, int version) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderNo", "O-test");
        payload.put("userId", 10001L);
        payload.put("totalAmountCent", 9900L);
        if ("OrderCreated".equals(type)) {
            payload.put("itemCount", 3);
            payload.put("skuCount", 2);
            payload.put("firstItemName", "测试商品");
        }
        return Map.of("eventId", UUID.randomUUID().toString(), "eventType", type,
            "eventVersion", version, "payload", payload);
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT * FROM order_query_projection WHERE order_no='O-test'");
    }

    private void assertSnapshot(Map<String, Object> row) {
        assertEquals(10001L, ((Number) row.get("user_id")).longValue());
        assertEquals(9900L, ((Number) row.get("total_amount_cent")).longValue());
        assertEquals(3, ((Number) row.get("item_count")).intValue());
        assertEquals(2, ((Number) row.get("sku_count")).intValue());
        assertEquals("测试商品", row.get("first_item_name"));
    }
}
