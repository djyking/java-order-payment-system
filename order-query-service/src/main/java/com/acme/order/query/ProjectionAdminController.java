package com.acme.order.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.acme.order.common.core.ApiResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发环境查询投影重建入口。生产环境默认关闭，避免误操作。
 *
 * @author heyu
 * @since 2026-09-02
 */
@RestController
@RequestMapping("/admin/query-projection")
public class ProjectionAdminController {

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final boolean includeShards;

    public ProjectionAdminController(JdbcTemplate jdbc,
        @Value("${projection.rebuild-enabled:false}") boolean enabled,
        @Value("${projection.include-shards:true}") boolean includeShards) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.includeShards = includeShards;
    }

    @PostMapping("/rebuild")
    @Transactional
    ApiResponse<Map<String, Object>> rebuild() {
        if (!enabled) {
            return ApiResponse.ok(Map.of("enabled", false, "message", "投影重建入口未启用"), MDC.get("traceId"));
        }
        jdbc.update("DELETE FROM order_query_projection");
        int rebuilt = jdbc.update(rebuildSql());
        return ApiResponse.ok(Map.of("enabled", true, "rebuilt", rebuilt, "includeShards", includeShards),
            MDC.get("traceId"));
    }

    private String rebuildSql() {
        List<SourceTable> sources = new ArrayList<>();
        sources.add(new SourceTable("order_trade", "trade_order", "trade_order_item"));
        if (includeShards) {
            for (int database = 0; database < 2; database++) {
                for (int table = 0; table < 8; table++) {
                    sources.add(new SourceTable("order_trade_" + database, "trade_order_" + table,
                        "trade_order_item_" + table));
                }
            }
        }
        String projections = String.join(" UNION ALL ", sources.stream().map(this::sourceSelect).toList());
        return "INSERT INTO order_query_projection(order_no,user_id,status,pay_status,total_amount_cent,"
                + "pay_amount_cent,item_count,sku_count,first_item_name,event_version,pay_time,cancel_time,"
                + "create_time,update_time) "
                + projections;
    }

    private String sourceSelect(SourceTable source) {
        String schema = source.schema();
        String orderTable = source.orderTable();
        String itemTable = source.itemTable();
        return "SELECT o.order_no,o.user_id,CASE o.status WHEN 10 THEN 'WAIT_PAY' WHEN 20 THEN 'WAIT_DELIVERY' "
                + "WHEN 30 THEN 'WAIT_RECEIVE' WHEN 40 THEN 'COMPLETED' WHEN 90 THEN 'CANCELED' ELSE 'UNKNOWN' END,"
                + "IF(o.pay_status=20,'PAID','UNPAID'),o.total_amount_cent,o.pay_amount_cent,o.item_count,"
                + "(SELECT COUNT(DISTINCT i.sku_id) FROM " + schema + "." + itemTable
                + " i WHERE i.order_id=o.order_id),"
                + "(SELECT i.sku_name FROM " + schema + "." + itemTable
                + " i WHERE i.order_id=o.order_id ORDER BY i.id LIMIT 1),"
                + "o.version,o.pay_time,o.cancel_time,o.create_time,o.update_time FROM " + schema + "."
                + orderTable + " o";
    }

    private record SourceTable(String schema, String orderTable, String itemTable) {
    }
}
