package com.acme.order.common.web;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import com.acme.order.common.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class ServiceAuthenticationFilterTest {
    private static final String SECRET = "unit-test-jwt-secret-with-at-least-32-bytes";
    private static final String SERVICE = "unit-test-internal-secret-with-at-least-32-bytes";
    private final JwtService jwt = new JwtService(SECRET, Duration.ofMinutes(5));
    private final ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter(SECRET, SERVICE, new ObjectMapper());

    @Test void forgedIdentityWithoutBearerCannotAccessDirectService() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-User-Id", "10001");
        request.addHeader("X-User-Role", "ADMIN");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (r, s) -> fail("must reject"));
        assertEquals(401, response.getStatus());
    }
    @Test void verifiedIdentityOverwritesForgedHeaders() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer " + jwt.issue(42, "USER"));
        request.addHeader("X-User-Id", "99");
        request.addHeader("X-User-Role", "ADMIN");
        filter.doFilter(request, new MockHttpServletResponse(), (r, s) -> {
            var http = (HttpServletRequest) r;
            assertEquals("42", http.getHeader("X-User-Id"));
            assertEquals("USER", http.getHeaders("X-User-Role").nextElement());
        });
    }
    @Test void userCannotBecomeAdminBySupplyingHeader() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/admin/products");
        request.addHeader("Authorization", "Bearer " + jwt.issue(42, "USER"));
        request.addHeader("X-User-Role", "ADMIN");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (r, s) -> fail("must reject"));
        assertEquals(403, response.getStatus());
    }
    @Test void adminCanReachAdminOperations() throws Exception {
        var request = new MockHttpServletRequest("PUT", "/api/admin/products/7");
        request.addHeader("Authorization", "Bearer " + jwt.issue(42, "ADMIN"));
        var reached = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (r, s) -> reached.set(true));
        assertTrue(reached.get());
    }
    @Test void catalogIsPublicForGetOnlyAndIdentityIsRemoved() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/products");
        request.addHeader("X-User-Id", "10001");
        filter.doFilter(request, new MockHttpServletResponse(), (r, s) -> assertNull(((HttpServletRequest) r).getHeader("X-User-Id")));
        var denied = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/products"), denied, (r, s) -> fail("must reject"));
        assertEquals(401, denied.getStatus());
        var lookalike = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/skus/1/private"), lookalike, (r, s) -> fail("must reject"));
        assertEquals(401, lookalike.getStatus());
    }
    @Test void internalCallsNeedServiceSecretEvenWithAdminJwt() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/payments");
        request.addHeader("Authorization", "Bearer " + jwt.issue(42, "ADMIN"));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (r, s) -> fail("must reject"));
        assertEquals(401, response.getStatus());
        var valid = new MockHttpServletRequest("POST", "/internal/payments");
        valid.addHeader("X-Service-Token", SERVICE);
        var reached = new AtomicBoolean();
        filter.doFilter(valid, new MockHttpServletResponse(), (r, s) -> reached.set(true));
        assertTrue(reached.get());
    }
    @Test void expiredJwtAndMissingConfiguredSecretsFailClosed() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer " + new JwtService(SECRET, Duration.ofSeconds(-1)).issue(42, "USER"));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (r, s) -> fail("must reject"));
        assertEquals(401, response.getStatus());
        assertThrows(IllegalArgumentException.class, () -> new ServiceAuthenticationFilter("", SERVICE, new ObjectMapper()));
        assertThrows(IllegalArgumentException.class, () -> new ServiceAuthenticationFilter(SECRET, "", new ObjectMapper()));
    }
    @Test void alternateAdminPathSpellingsCannotBypassRoleChecks() throws Exception {
        for (String path : new String[]{"/api/admin;ignored/products", "/api/%61dmin/products", "/api//admin/products",
                "/api/products/../admin/products", "/api/admin%2fproducts"}) {
            var request = new MockHttpServletRequest("POST", path);
            request.addHeader("Authorization", "Bearer " + jwt.issue(42, "USER"));
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (r, s) -> fail("must reject " + path));
            assertEquals(400, response.getStatus(), path);
        }
    }
}
