package com.acme.order.inventory;

import com.acme.order.common.core.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

/** Administrator stock maintenance. Authentication is enforced by the shared servlet filter. */
@RestController
@RequestMapping("/api/admin/inventory")
public class AdminInventoryController {
    private final AdminInventoryService service;

    public AdminInventoryController(AdminInventoryService service) { this.service = service; }

    @GetMapping("/{skuId}")
    ApiResponse<Stock> get(@PathVariable long skuId) {
        return ApiResponse.ok(service.get(skuId), MDC.get("traceId"));
    }

    @PutMapping("/{skuId}")
    ApiResponse<Stock> update(@PathVariable long skuId, @RequestHeader("X-User-Id") long actor,
                             @Valid @RequestBody StockBody body) {
        return ApiResponse.ok(service.setTotal(skuId, body.total(), actor), MDC.get("traceId"));
    }

    public record StockBody(@NotNull @Min(0) @Max(1_000_000) Integer total) {}
    public record Stock(String skuId, int total, int available, int reserved, int sold) {}
}
