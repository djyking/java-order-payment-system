package com.acme.order.order;

import java.util.List;

import com.acme.order.api.inventory.InventoryDtos.ChangeRequest;
import com.acme.order.api.inventory.InventoryDtos.Line;
import com.acme.order.api.inventory.InventoryDtos.ReserveRequest;
import com.acme.order.api.payment.PaymentDtos;
import com.acme.order.api.product.ProductDtos.SkuView;
import com.acme.order.common.core.ApiResponse;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 封装订单服务对商品、库存和支付服务的远程调用。
 *
 * @author heyu
 * @since 2026-08-12
 */
@Component
public class RemoteClients {

    private final RestClient product;
    private final RestClient inventory;
    private final RestClient payment;

    public RemoteClients(@Qualifier("productClient") RestClient p, @Qualifier("inventoryClient") RestClient i,
        @Qualifier("paymentClient") RestClient pay) {
        product = p;
        inventory = i;
        payment = pay;
    }

    @SentinelResource(value = "order.product", blockHandler = "skuBlocked", fallback = "skuFallback")
    public SkuView sku(long id) {
        return product.get().uri("/internal/skus/{id}", id).retrieve()
            .body(new ParameterizedTypeReference<ApiResponse<SkuView>>() {
            }).data();
    }

    @SentinelResource(value = "order.inventory", blockHandler = "reserveBlocked", fallback = "reserveFallback")
    public void reserve(String no, long user, List<Line> lines) {
        inventory.post().uri("/internal/inventory/reserve").contentType(MediaType.APPLICATION_JSON)
            .body(new ReserveRequest(no, user, lines)).retrieve().toBodilessEntity();
    }

    @SentinelResource(value = "order.inventory.release", blockHandler = "releaseBlocked", fallback = "releaseFallback")
    public void release(String no) {
        inventory.post().uri("/internal/inventory/release").contentType(MediaType.APPLICATION_JSON)
            .body(new ChangeRequest(no)).retrieve().toBodilessEntity();
    }

    @SentinelResource(value = "order.payment", blockHandler = "paymentBlocked", fallback = "paymentFallback")
    public PaymentDtos.PayView createPayment(String no, long user, long amount) {
        return payment.post().uri("/internal/payments").contentType(MediaType.APPLICATION_JSON)
            .body(new PaymentDtos.CreateRequest(no, user, amount)).retrieve()
            .body(new ParameterizedTypeReference<ApiResponse<PaymentDtos.PayView>>() {
            }).data();
    }

    public SkuView skuBlocked(long id, BlockException cause) {
        throw unavailable("商品服务正在限流或熔断", cause);
    }

    public SkuView skuFallback(long id, Throwable cause) {
        throw unavailable("商品服务暂时不可用", cause);
    }

    public void reserveBlocked(String no, long user, List<Line> lines, BlockException cause) {
        throw unavailable("库存服务正在限流或熔断", cause);
    }

    public void reserveFallback(String no, long user, List<Line> lines, Throwable cause) {
        throw unavailable("库存服务暂时不可用", cause);
    }

    public void releaseBlocked(String no, BlockException cause) {
        throw unavailable("库存释放服务正在限流或熔断", cause);
    }

    public void releaseFallback(String no, Throwable cause) {
        throw unavailable("库存释放服务暂时不可用", cause);
    }

    public PaymentDtos.PayView paymentBlocked(String no, long user, long amount, BlockException cause) {
        throw unavailable("支付服务正在限流或熔断", cause);
    }

    public PaymentDtos.PayView paymentFallback(String no, long user, long amount, Throwable cause) {
        throw unavailable("支付服务暂时不可用", cause);
    }

    private BizException unavailable(String message, Throwable cause) {
        return new BizException(ErrorCode.SYSTEM_BUSY, message + ": " + cause.getClass().getSimpleName());
    }
}
