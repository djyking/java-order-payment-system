package com.acme.order.payment;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import com.acme.order.api.payment.PaymentDtos.CreateRequest;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import com.acme.order.common.observability.OrderMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class PaymentRepositoryTest {
    JdbcTemplate jdbc;
    PaymentRepository repo;
    TransactionTemplate transaction;

    @BeforeEach void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("""
            CREATE TABLE pay_order(id BIGINT PRIMARY KEY,pay_order_no VARCHAR(64) UNIQUE,biz_order_no VARCHAR(64) UNIQUE,
            user_id BIGINT,amount_cent BIGINT,status INT,channel_code VARCHAR(32),version INT,pay_time DATETIME(3),
            create_time DATETIME(3),update_time DATETIME(3))
            """);
        jdbc.execute("""
            CREATE TABLE pay_notify_log(id BIGINT PRIMARY KEY,notify_id VARCHAR(64) UNIQUE,pay_order_no VARCHAR(64),
            channel_code VARCHAR(32),notify_payload TEXT,process_status INT,create_time DATETIME(3),update_time DATETIME(3))
            """);
        jdbc.execute("""
            CREATE TABLE outbox_event(id BIGINT PRIMARY KEY,event_id VARCHAR(64) UNIQUE,event_type VARCHAR(64),
            aggregate_type VARCHAR(64),aggregate_id VARCHAR(64),payload TEXT,event_status INT,retry_count INT,
            next_retry_time DATETIME(3),create_time DATETIME(3),update_time DATETIME(3))
            """);
        repo = new PaymentRepository(jdbc);
    }

    @AfterEach void close() { jdbc.execute("SHUTDOWN"); }

    @Test void repeatSameMethodReusesPaymentButDifferentAmountOrMethodCannotSilentlyReuse() {
        var first = repo.create("O1", 101, 9900, "MOCK");
        assertEquals(first.payOrderNo(), repo.create("O1", 101, 9900, "MOCK").payOrderNo());
        assertEquals(ErrorCode.PAY_METHOD_CONFLICT, assertThrows(BizException.class,
            () -> repo.create("O1", 101, 9900, "ALIPAY_PAGE")).getCode());
        assertEquals(ErrorCode.PAY_AMOUNT_MISMATCH, assertThrows(BizException.class,
            () -> repo.create("O1", 101, 9901, "MOCK")).getCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pay_order", Integer.class));
    }

    @Test void anotherUserCannotReadCreateOrCompleteExistingPayment() {
        var payment = repo.create("O1", 101, 9900, "MOCK");
        assertEquals(ErrorCode.PAY_ORDER_NOT_FOUND, assertThrows(BizException.class,
            () -> repo.get(payment.payOrderNo(), 202)).getCode());
        assertThrows(BizException.class, () -> repo.success(payment.payOrderNo(), 202, "notification"));
        assertThrows(BizException.class, () -> repo.create("O1", 202, 9900, "MOCK"));
        assertEquals("UNPAID", repo.get(payment.payOrderNo(), 101).status());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pay_notify_log", Integer.class));
    }

    @Test void concurrentDuplicateNotificationsWriteOnePaidEventUsingActualSql() throws Exception {
        var payment = repo.create("O1", 101, 9900, "MOCK");
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = java.util.stream.IntStream.range(0, 20).mapToObj(i -> (Callable<Boolean>) () ->
                transaction.execute(status -> repo.success(payment.payOrderNo(), 101, "same-notification"))).toList();
            int changed = 0;
            for (var future : pool.invokeAll(tasks)) { if (future.get()) { changed++; } }
            assertEquals(1, changed);
            assertEquals("PAID", repo.get(payment.payOrderNo(), 101).status());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void realMethodsAreUnavailableAndDefaultHasNoMock() {
        var config = new PaymentProviders.AdapterConfiguration();
        var providers = new PaymentProviders(List.of(config.alipayPage(), config.wechatNative()));
        var service = new PaymentService(repo, new OrderMetrics(new SimpleMeterRegistry()), providers);
        assertEquals(2, service.methods().size());
        assertTrue(service.methods().stream().noneMatch(m -> m.available()));
        for (var code : List.of("ALIPAY_PAGE", "WECHAT_NATIVE", "MOCK", "UNKNOWN")) {
            assertEquals(ErrorCode.PAY_METHOD_UNAVAILABLE, assertThrows(BizException.class,
                () -> service.create(new CreateRequest("O1", 101, 9900, code))).getCode());
        }
        assertThrows(BizException.class, () -> service.mockSuccess("P1", 101, "notification"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pay_order", Integer.class));
    }

    @Test void enabledMockCannotCompleteRealChannelPayment() {
        var config = new PaymentProviders.AdapterConfiguration();
        var service = new PaymentService(repo, new OrderMetrics(new SimpleMeterRegistry()),
            new PaymentProviders(List.of(config.mockProvider())));
        var real = repo.create("O1", 101, 9900, "ALIPAY_PAGE");
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BizException.class,
            () -> service.mockSuccess(real.payOrderNo(), 101, "notification")).getCode());
    }
}
