package com.acme.order.common.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import com.acme.order.common.core.ApiResponse;
import com.acme.order.common.core.ErrorCode;
import com.acme.order.common.security.JwtService;
import com.acme.order.common.security.RequestAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticate at the service boundary, even when the gateway is bypassed. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ServiceAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final byte[] internalToken;
    private final ObjectMapper mapper;

    public ServiceAuthenticationFilter(@Value("${security.jwt.secret:${JWT_SECRET:}}") String secret,
            @Value("${security.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken, ObjectMapper mapper) {
        this.jwt = new JwtService(secret, Duration.ofHours(8));
        if (internalToken == null || internalToken.length() < 32) {
            throw new IllegalArgumentException("INTERNAL_SERVICE_TOKEN must contain at least 32 random characters");
        }
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!RequestAccess.isSafePath(path)) {
            reject(response, 400, ErrorCode.VALIDATION_FAILED, "请求路径格式无效");
            return;
        }
        if (RequestAccess.isPublic(request.getMethod(), path)) {
            chain.doFilter(new IdentityRequest(request, null, null), response);
            return;
        }
        if (RequestAccess.isInternal(path)) {
            String supplied = request.getHeader("X-Service-Token");
            if (supplied == null || !MessageDigest.isEqual(internalToken, supplied.getBytes(StandardCharsets.UTF_8))) {
                reject(response, 401, ErrorCode.UNAUTHORIZED, "服务身份验证失败");
                return;
            }
            chain.doFilter(new IdentityRequest(request, null, null), response);
            return;
        }
        String userId;
        String role;
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) throw new IllegalArgumentException();
            var claims = jwt.parse(auth.substring(7));
            userId = claims.getSubject();
            role = claims.get("role", String.class);
        } catch (RuntimeException ex) {
            reject(response, 401, ErrorCode.UNAUTHORIZED, "请先登录，或重新登录后重试");
            return;
        }
        if (RequestAccess.isAdmin(path) && !"ADMIN".equals(role)) {
            reject(response, 403, ErrorCode.FORBIDDEN, "需要管理员权限");
            return;
        }
        chain.doFilter(new IdentityRequest(request, userId, role), response);
    }

    private void reject(HttpServletResponse response, int status, ErrorCode code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message, MDC.get("traceId")));
    }

    private static final class IdentityRequest extends HttpServletRequestWrapper {
        private final String userId;
        private final String role;
        IdentityRequest(HttpServletRequest request, String userId, String role) {
            super(request);
            this.userId = userId;
            this.role = role;
        }
        @Override public String getHeader(String name) {
            if ("X-User-Id".equalsIgnoreCase(name)) return userId;
            if ("X-User-Role".equalsIgnoreCase(name)) return role;
            return super.getHeader(name);
        }
        @Override public Enumeration<String> getHeaders(String name) {
            if ("X-User-Id".equalsIgnoreCase(name) || "X-User-Role".equalsIgnoreCase(name)) {
                String value = getHeader(name);
                return Collections.enumeration(value == null ? Set.of() : Set.of(value));
            }
            return super.getHeaders(name);
        }
        @Override public Enumeration<String> getHeaderNames() {
            var names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.removeIf(n -> "X-User-Id".equalsIgnoreCase(n) || "X-User-Role".equalsIgnoreCase(n));
            if (userId != null) names.add("X-User-Id");
            if (role != null) names.add("X-User-Role");
            return Collections.enumeration(names);
        }
    }
}
