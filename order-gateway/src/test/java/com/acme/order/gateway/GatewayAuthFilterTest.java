package com.acme.order.gateway;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import com.acme.order.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class GatewayAuthFilterTest {
    private final JwtService jwt = new JwtService("gateway-test-secret-with-more-than-32-bytes", Duration.ofMinutes(5));
    private final GatewayAuthFilter filter = new GatewayAuthFilter(jwt);
    @Test void publicCatalogStripsForgedIdentityAndServiceToken() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
            .header("X-User-Id", "1").header("X-User-Role", "ADMIN").header("X-Service-Token", "secret"));
        filter.filter(exchange, e -> {
            assertNull(e.getRequest().getHeaders().getFirst("X-User-Id"));
            assertNull(e.getRequest().getHeaders().getFirst("X-Service-Token"));
            return Mono.empty();
        }).block();
    }
    @Test void userRoleCannotAccessAdminAndAmbiguousPathsAreRejected() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/admin/products")
            .header("Authorization", "Bearer " + jwt.issue(1, "USER")).header("X-User-Role", "ADMIN"));
        filter.filter(exchange, e -> { fail("must reject"); return Mono.empty(); }).block();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        var matrix = MockServerWebExchange.from(MockServerHttpRequest.post("/api/admin;ignored/products")
            .header("Authorization", "Bearer " + jwt.issue(1, "USER")));
        filter.filter(matrix, e -> { fail("must reject"); return Mono.empty(); }).block();
        assertEquals(HttpStatus.BAD_REQUEST, matrix.getResponse().getStatusCode());
    }
    @Test void verifiedAdminCanReachAdminRoute() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/orders")
            .header("Authorization", "Bearer " + jwt.issue(7, "ADMIN")));
        var reached = new AtomicBoolean();
        filter.filter(exchange, e -> {
            assertEquals("7", e.getRequest().getHeaders().getFirst("X-User-Id"));
            reached.set(true);
            return Mono.empty();
        }).block();
        assertTrue(reached.get());
    }
}
