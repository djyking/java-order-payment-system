package com.acme.order.order;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import com.acme.order.common.core.BizException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderRequestPersistenceTest {
    JdbcTemplate jdbc;
    OrderRepository repo;
    OrderTxWriter writer;
    TransactionTemplate transaction;
    final String hash = "a".repeat(64);
    final OrderDtos.SettlementView quote = new OrderDtos.SettlementView(
        List.of(new OrderDtos.SettlementLine(1001, "商品", 9900, 1, 9900)), 9900);

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        String schema = Files.readString(Path.of("../sql/04_order.sql")).replace("USE order_trade;", "");
        for (String statement : schema.split(";")) { if (!statement.isBlank()) { jdbc.execute(statement); } }
        jdbc.execute("""
            CREATE TABLE order_create_request(user_id BIGINT,request_token VARCHAR(64),request_hash CHAR(64),
            order_no VARCHAR(64) UNIQUE,create_time DATETIME(3),PRIMARY KEY(user_id,request_token))
            """);
        jdbc.execute("""
            CREATE TABLE outbox_event(id BIGINT PRIMARY KEY,user_id BIGINT,event_id VARCHAR(64) UNIQUE,
            event_type VARCHAR(64),aggregate_type VARCHAR(64),aggregate_id VARCHAR(64),payload TEXT,
            event_status INT,retry_count INT,next_retry_time DATETIME(3),create_time DATETIME(3),update_time DATETIME(3))
            """);
        repo = new OrderRepository(jdbc, mock(OrderRouteResolver.class));
        writer = new OrderTxWriter(repo);
    }

    @AfterEach void close() { jdbc.execute("SHUTDOWN"); }

    @Test void requestAndOrderCommitTogetherAndRejectChangedPayloadOrDuplicateOrder() {
        transaction.executeWithoutResult(tx -> writer.persist(1, "O1", 101, quote, "request-token", hash));
        assertEquals("O1", repo.completedRequest(101, "request-token", hash));
        assertThrows(BizException.class, () -> repo.completedRequest(101, "request-token", "b".repeat(64)));
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () ->
            transaction.executeWithoutResult(tx -> writer.persist(2, "O2", 101, quote, "request-token", hash)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_order", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_create_request", Integer.class));
        assertNull(repo.completedRequest(202, "request-token", hash));
    }

    @Test void rollbackLeavesNoRequestOrderOrEventSoRetryCanSucceed() {
        ReflectionTestUtils.setField(writer, "failBeforeCommit", true);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(tx ->
            writer.persist(1, "O1", 101, quote, "request-token", hash)));
        assertNull(repo.completedRequest(101, "request-token", hash));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trade_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class));
        ReflectionTestUtils.setField(writer, "failBeforeCommit", false);
        transaction.executeWithoutResult(tx -> writer.persist(2, "O2", 101, quote, "request-token", hash));
        assertEquals("O2", repo.completedRequest(101, "request-token", hash));
    }

    @Test void productNameWithControlCharactersProducesValidOrderEvent() throws Exception {
        var unusual = new OrderDtos.SettlementView(List.of(
            new OrderDtos.SettlementLine(1001, "商品\n\t\"选项\"", 9900, 1, 9900)), 9900);
        transaction.executeWithoutResult(tx -> writer.persist(1, "O1", 101, unusual, "request-token", hash));
        String payload = jdbc.queryForObject("SELECT payload FROM outbox_event", String.class);
        assertEquals("商品\n\t\"选项\"", new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload).get("firstItemName").asText());
    }
}
