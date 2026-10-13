package com.acme.order.gateway;

import java.nio.charset.StandardCharsets;
import com.acme.order.common.security.JwtService;
import com.acme.order.common.security.RequestAccess;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {
    private final JwtService jwt;
    public GatewayAuthFilter(JwtService jwt) { this.jwt = jwt; }
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (!RequestAccess.isSafePath(path)) return reject(exchange, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        var cleanRequest = exchange.getRequest().mutate().headers(h -> {
            h.remove("X-User-Id"); h.remove("X-User-Role"); h.remove("X-Service-Token");
        }).build();
        var clean = exchange.mutate().request(cleanRequest).build();
        if (RequestAccess.isInternal(path)) return reject(clean, HttpStatus.FORBIDDEN, "FORBIDDEN");
        if (RequestAccess.isPublic(exchange.getRequest().getMethod().name(), path)) return chain.filter(clean);
        String userId;
        String role;
        try {
            String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (auth == null || !auth.startsWith("Bearer ")) throw new IllegalArgumentException();
            var claims = jwt.parse(auth.substring(7));
            userId = claims.getSubject();
            role = claims.get("role", String.class);
        } catch (RuntimeException ex) {
            return reject(clean, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        if (RequestAccess.isAdmin(path) && !"ADMIN".equals(role)) {
            return reject(clean, HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        var verified = clean.getRequest().mutate().headers(h -> {
            h.set("X-User-Id", userId); h.set("X-User-Role", role);
        }).build();
        return chain.filter(clean.mutate().request(verified).build());
    }
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var data = exchange.getResponse().bufferFactory().wrap(("{\"code\":\"" + code
            + "\",\"message\":\"访问需要有效身份及对应权限\",\"data\":null}").getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(data));
    }
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }
}
