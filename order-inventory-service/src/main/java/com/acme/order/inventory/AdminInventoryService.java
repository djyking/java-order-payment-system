package com.acme.order.inventory;

import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import com.acme.order.common.core.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInventoryService {
    private final JdbcTemplate jdbc;

    public AdminInventoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AdminInventoryController.Stock get(long skuId) {
        var rows = jdbc.query("SELECT total_stock,available_stock,reserved_stock,sold_stock FROM inventory_stock WHERE sku_id=?",
            (r, n) -> new AdminInventoryController.Stock(Long.toString(skuId), r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4)), skuId);
        return rows.isEmpty() ? new AdminInventoryController.Stock(Long.toString(skuId), 0, 0, 0, 0) : rows.get(0);
    }

    @Transactional
    public AdminInventoryController.Stock setTotal(long skuId, int total, long actor) {
        if (skuId <= 0 || total < 0 || total > 1_000_000) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "库存数量应在 0 到 1000000 之间");
        }
        // Insert-or-lock serializes stock creation and updates with reservations/confirmations.
        jdbc.update("INSERT IGNORE INTO inventory_stock(sku_id,total_stock,available_stock,reserved_stock,sold_stock,version) VALUES(?,0,0,0,0,0)", skuId);
        var before = jdbc.queryForObject("SELECT total_stock FROM inventory_stock WHERE sku_id=? FOR UPDATE", Integer.class, skuId);
        int changed = jdbc.update("UPDATE inventory_stock SET total_stock=?,available_stock=?-reserved_stock-sold_stock,version=version+1,update_time=NOW(3) WHERE sku_id=? AND reserved_stock+sold_stock<=?",
            total, total, skuId, total);
        if (changed != 1) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "总库存不能低于已预占与已售数量之和");
        }
        jdbc.update("INSERT INTO inventory_admin_audit(id,sku_id,actor_id,before_total,after_total,create_time) VALUES(?,?,?,?,?,NOW(3))",
            Ids.next(), skuId, actor, before, total);
        return get(skuId);
    }
}
