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
import org.springframework.beans.factory.annotation.Value;
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
    private final String serviceToken;

    public RemoteClients(@Qualifier("productClient") RestClient p, @Qualifier("inventoryClient") RestClient i,
        @Qualifier("paymentClient") RestClient pay,
        @Value("${security.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String serviceToken) {
        product = p;
        inventory = i;
        payment = pay;
        this.serviceToken = serviceToken;
    }

    @SentinelResource(value = "order.product", blockHandler = "skuBlocked", fallback = "skuFallback")
    public SkuView sku(long id) {
        return product.get().uri("/internal/skus/{id}", id).header("X-Service-Token", serviceToken).retrieve()
            .body(new ParameterizedTypeReference<ApiResponse<SkuView>>() {
            }).data();
    }

    @SentinelResource(value = "order.inventory", blockHandler = "reserveBlocked", fallback = "reserveFallback")
    public void reserve(String no, long user, List<Line> lines) {
        inventory.post().uri("/internal/inventory/reserve").contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", serviceToken)
            .body(new ReserveRequest(no, user, lines)).retrieve().toBodilessEntity();
    }

    @SentinelResource(value = "order.inventory.release", blockHandler = "releaseBlocked", fallback = "releaseFallback")
    public void release(String no) {
        inventory.post().uri("/internal/inventory/release").contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", serviceToken)
            .body(new ChangeRequest(no)).retrieve().toBodilessEntity();
    }

    @SentinelResource(value = "order.payment", blockHandler = "paymentBlocked", fallback = "paymentFallback")
    public PaymentDtos.PayView createPayment(String no, long user, long amount, String method) {
        return payment.post().uri("/internal/payments").contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", serviceToken)
            .body(new PaymentDtos.CreateRequest(no, user, amount, method)).retrieve()
            .body(new ParameterizedTypeReference<ApiResponse<PaymentDtos.PayView>>() {
            }).data();
    }

    public SkuView skuBlocked(long id, BlockException cause) {
        throw unavailable("商品服务正在限流或熔断", cause);
    }

    public SkuView skuFallback(long id, Throwable cause) {
        throw remoteFailure("商品服务暂时不可用，请稍后重试", cause);
    }

    public void reserveBlocked(String no, long user, List<Line> lines, BlockException cause) {
        throw unavailable("库存服务正在限流或熔断", cause);
    }

    public void reserveFallback(String no, long user, List<Line> lines, Throwable cause) {
        throw remoteFailure("库存服务暂时不可用，请稍后重试", cause);
    }

    public void releaseBlocked(String no, BlockException cause) {
        throw unavailable("库存释放服务正在限流或熔断", cause);
    }

    public void releaseFallback(String no, Throwable cause) {
        throw remoteFailure("库存释放服务暂时不可用，请稍后重试", cause);
    }

    public PaymentDtos.PayView paymentBlocked(String no, long user, long amount, String method, BlockException cause) {
        throw unavailable("支付服务正在限流或熔断", cause);
    }

    public PaymentDtos.PayView paymentFallback(String no, long user, long amount, String method, Throwable cause) {
        throw remoteFailure("支付服务暂时不可用，请稍后重试", cause);
    }

    private BizException remoteFailure(String message, Throwable cause) {
        if (cause instanceof org.springframework.web.client.RestClientResponseException response
            && response.getStatusCode().is4xxClientError()) {
            try {
                ApiResponse<?> body = response.getResponseBodyAs(ApiResponse.class);
                if (body != null && body.code() != null && body.message() != null && !body.message().isBlank()) {
                    var code = ErrorCode.valueOf(body.code());
                    // 内部服务身份错误属于服务配置故障，不能误判为买家登录失效。
                    if (code != ErrorCode.UNAUTHORIZED && code != ErrorCode.FORBIDDEN
                        && code != ErrorCode.SYSTEM_BUSY && code != ErrorCode.MQ_PROCESS_FAILED) {
                        return new BizException(code, body.message());
                    }
                }
            } catch (RuntimeException ignored) {
                // 非标准响应、未知错误码和反序列化失败均返回稳定的服务提示。
            }
        }
        return unavailable(message, cause);
    }

    private BizException unavailable(String message, Throwable cause) {
        return new BizException(ErrorCode.SYSTEM_BUSY, message);
    }
}
