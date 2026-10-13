package com.acme.order.inventory;

import com.acme.order.common.core.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdminInventoryServiceTest {
    JdbcTemplate jdbc;
    AdminInventoryService service;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE inventory_stock(sku_id BIGINT PRIMARY KEY,total_stock INT,available_stock INT,reserved_stock INT,sold_stock INT,version INT,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE inventory_admin_audit(id BIGINT PRIMARY KEY,sku_id BIGINT,actor_id BIGINT,before_total INT,after_total INT,create_time TIMESTAMP)");
        service = new AdminInventoryService(jdbc);
    }

    @Test void initializeAndAuditStock() {
        assertEquals(0, service.get(100).total());
        assertEquals(12, service.setTotal(100, 12, 9).available());
        assertEquals(9L, jdbc.queryForObject("SELECT actor_id FROM inventory_admin_audit", Long.class));
    }

    @Test void adjustmentPreservesReservedAndSold() {
        jdbc.update("INSERT INTO inventory_stock VALUES(100,12,7,3,2,0,NOW())");
        var result = service.setTotal(100, 20, 9);
        assertEquals(15, result.available());
        assertEquals(3, result.reserved());
        assertEquals(2, result.sold());
        assertThrows(BizException.class, () -> service.setTotal(100, 4, 9));
        assertEquals(20, service.get(100).total());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_admin_audit", Integer.class));
    }
}
