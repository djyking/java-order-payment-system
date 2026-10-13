package com.acme.order.query;

import java.util.List;
import java.util.Map;

import com.acme.order.common.core.ApiResponse;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供后台订单分页查询接口。
 *
 * @author heyu
 * @since 2026-08-27
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final JdbcTemplate jdbc;

    public AdminOrderController(JdbcTemplate j) {
        jdbc = j;
    }

    @GetMapping
    ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        int safe = Math.min(Math.max(size, 1), 100);
        var sql = "SELECT order_no,user_id,status,pay_status,total_amount_cent,pay_amount_cent,item_count,sku_count,"
            + "first_item_name,pay_time,cancel_time,create_time,update_time "
            + "FROM order_query_projection"
            + (status == null ? "" : " WHERE status=?") + " ORDER BY create_time DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> rows = status == null ? jdbc.queryForList(sql, safe, page * safe)
            : jdbc.queryForList(sql, status, safe, page * safe);
        return ApiResponse.ok(rows, MDC.get("traceId"));
    }
}
