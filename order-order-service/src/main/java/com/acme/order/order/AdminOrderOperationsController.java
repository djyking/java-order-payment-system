package com.acme.order.order;

import com.acme.order.common.core.ApiResponse;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

/** 管理员操作入口，由公共安全过滤器验证 ADMIN 角色。userId 用于定位订单分片。 */
@RestController
@RequestMapping("/api/admin/order-operations")
public class AdminOrderOperationsController {
    private final OrderService orders;

    public AdminOrderOperationsController(OrderService orders) { this.orders = orders; }

    @GetMapping("/{no}")
    ApiResponse<OrderDtos.OrderView> get(@PathVariable String no, @RequestParam long userId) {
        return ApiResponse.ok(orders.view(no, userId), MDC.get("traceId"));
    }

    @PostMapping("/{no}/deliver")
    ApiResponse<Void> deliver(@PathVariable String no, @RequestParam long userId,
        @RequestHeader("X-User-Id") long operatorId) {
        orders.deliver(no, userId, operatorId);
        return ApiResponse.ok(null, MDC.get("traceId"));
    }
}
