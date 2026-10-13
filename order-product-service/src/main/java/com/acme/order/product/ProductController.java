package com.acme.order.product;

import com.acme.order.api.product.ProductDtos.SkuView;
import com.acme.order.common.core.ApiResponse;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductController {
    private final ProductRepository repo;
    public ProductController(ProductRepository repo) { this.repo = repo; }
    @GetMapping({"/api/skus/{id}", "/internal/skus/{id}"})
    ApiResponse<SkuView> sku(@PathVariable long id) {
        var sku = repo.sku(id);
        if (!sku.onShelf()) throw new BizException(ErrorCode.SKU_OFF_SHELF, "SKU已下架");
        return ApiResponse.ok(sku, MDC.get("traceId"));
    }
    @GetMapping("/api/products")
    ApiResponse<List<SkuView>> products(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(repo.list(page, size, keyword.trim(), false), MDC.get("traceId"));
    }
    @GetMapping("/api/admin/products")
    ApiResponse<List<SkuView>> adminProducts(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(repo.list(page, size, keyword.trim(), true), MDC.get("traceId"));
    }
    @PostMapping("/api/admin/products")
    ApiResponse<SkuView> create(@Valid @RequestBody ProductBody body) {
        return ApiResponse.ok(repo.create(body.name(), body.skuName(), body.priceCent(), body.onShelf()), MDC.get("traceId"));
    }
    @PutMapping("/api/admin/products/{id}")
    ApiResponse<SkuView> update(@PathVariable long id, @Valid @RequestBody ProductBody body) {
        return ApiResponse.ok(repo.update(id, body.name(), body.skuName(), body.priceCent(), body.onShelf()), MDC.get("traceId"));
    }
    public record ProductBody(@NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 128) String skuName, @NotNull @Min(1) @Max(100_000_000) Long priceCent,
            @NotNull Boolean onShelf) { }
}
