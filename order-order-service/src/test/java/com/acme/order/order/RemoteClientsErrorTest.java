package com.acme.order.order;

import java.util.List;
import com.acme.order.api.inventory.InventoryDtos.Line;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RemoteClientsErrorTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://upstream");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestClient client = builder.build();
    private final RemoteClients remote = new RemoteClients(client, client, client, "service-secret");

    @Test void offShelfResponseRetainsUsefulBusinessMessage() {
        server.expect(requestTo("http://upstream/internal/skus/1001"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"SKU_OFF_SHELF\",\"message\":\"商品已下架，请重新选购\",\"data\":null}"));
        var upstream = assertThrows(RestClientResponseException.class, () -> remote.sku(1001));
        var result = assertThrows(BizException.class, () -> remote.skuFallback(1001, upstream));
        assertEquals(ErrorCode.SKU_OFF_SHELF, result.getCode());
        assertEquals("商品已下架，请重新选购", result.getMessage());
        server.verify();
    }

    @Test void insufficientStockResponseRetainsUsefulBusinessMessage() {
        var lines = List.of(new Line(1001, 3));
        server.expect(requestTo("http://upstream/internal/inventory/reserve"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"INVENTORY_NOT_ENOUGH\",\"message\":\"库存不足，请调整购买数量\",\"data\":null}"));
        var upstream = assertThrows(RestClientResponseException.class, () -> remote.reserve("O1", 42, lines));
        var result = assertThrows(BizException.class, () -> remote.reserveFallback("O1", 42, lines, upstream));
        assertEquals(ErrorCode.INVENTORY_NOT_ENOUGH, result.getCode());
        assertEquals("库存不足，请调整购买数量", result.getMessage());
        server.verify();
    }

    @Test void malformedResponseFallsBackWithoutLeakingJavaExceptionName() {
        server.expect(requestTo("http://upstream/internal/inventory/release"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("not-json"));
        var upstream = assertThrows(RestClientResponseException.class, () -> remote.release("O1"));
        var result = assertThrows(BizException.class, () -> remote.releaseFallback("O1", upstream));
        assertEquals(ErrorCode.SYSTEM_BUSY, result.getCode());
        assertEquals("库存释放服务暂时不可用，请稍后重试", result.getMessage());
        server.verify();
    }

    @Test void internalAuthenticationFailureDoesNotLogTheBuyerOut() {
        server.expect(requestTo("http://upstream/internal/payments"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"UNAUTHORIZED\",\"message\":\"服务身份验证失败\",\"data\":null}"));
        var upstream = assertThrows(RestClientResponseException.class, () -> remote.createPayment("O1", 42, 9900, "MOCK"));
        var result = assertThrows(BizException.class, () -> remote.paymentFallback("O1", 42, 9900, "MOCK", upstream));
        assertEquals(ErrorCode.SYSTEM_BUSY, result.getCode());
        assertEquals("支付服务暂时不可用，请稍后重试", result.getMessage());
        server.verify();
    }
}
