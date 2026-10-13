package com.acme.order.order;

import com.acme.order.common.core.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供订单结算、创建、支付、取消和查询接口。
 *
 * @author heyu
 * @since 2026-08-12
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService s;

    public OrderController(OrderService s) {
        this.s = s;
    }

    @PostMapping("/settlement")
    ApiResponse<OrderDtos.SettlementView> settlement(@Valid @RequestBody OrderDtos.SettlementRequest r) {
        return ApiResponse.ok(s.settle(r.items()), MDC.get("traceId"));
    }

    @PostMapping
    ApiResponse<OrderDtos.OrderView> create(@RequestHeader("X-User-Id") long u,
        @RequestHeader("X-Idempotency-Token") String t, @Valid @RequestBody OrderDtos.CreateRequest r) {
        return ApiResponse.ok(s.create(u, t, r.items()), MDC.get("traceId"));
    }

    @GetMapping
    ApiResponse<java.util.List<OrderDtos.OrderView>> list(@RequestHeader("X-User-Id") long u,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(s.list(u, page, size), MDC.get("traceId"));
    }

    @GetMapping("/{no}")
    ApiResponse<OrderDtos.OrderView> get(@PathVariable String no, @RequestHeader("X-User-Id") long u) {
        return ApiResponse.ok(s.view(no, u), MDC.get("traceId"));
    }

    @PostMapping("/{no}/cancel")
    ApiResponse<Void> cancel(@PathVariable String no, @RequestHeader("X-User-Id") long u) {
        s.cancel(no, u, "USER");
        return ApiResponse.ok(null, MDC.get("traceId"));
    }

    @PostMapping("/{no}/pay")
    ApiResponse<OrderDtos.PayView> pay(@PathVariable String no, @RequestHeader("X-User-Id") long u,
        @Valid @RequestBody OrderDtos.PayRequest request) {
        return ApiResponse.ok(s.pay(no, u, request.methodCode()), MDC.get("traceId"));
    }

    @PostMapping("/{no}/receive")
    ApiResponse<Void> receive(@PathVariable String no, @RequestHeader("X-User-Id") long u) {
        s.receive(no, u);
        return ApiResponse.ok(null, MDC.get("traceId"));
    }
}
