package com.acme.order.user;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real embedded Tomcat, bean wiring, request filters, JSON and JDBC together. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=", "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.discovery.enabled=false", "spring.cloud.service-registry.auto-registration.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:user-http;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "security.jwt.secret=http-integration-jwt-secret-at-least-32-bytes",
    "security.internal-token=http-integration-service-secret-at-least-32-bytes",
    "security.bootstrap-admin.username=", "security.bootstrap-admin.password=",
    "management.endpoints.web.exposure.include=health"
})
class UserHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void schema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_user(id BIGINT PRIMARY KEY,username VARCHAR(64) UNIQUE,"
            + "password_hash VARCHAR(255),role VARCHAR(32),status INT)");
        jdbc.update("DELETE FROM sys_user");
    }
    @Test void registrationLoginAndMeUsePersistedAccountAndVerifiedJwt() {
        var register = http.postForEntity("/api/auth/register",
            Map.of("username", "http_customer", "password", "customer-secure-password", "role", "ADMIN"), JsonNode.class);
        assertEquals(200, register.getStatusCode().value());
        var account = register.getBody().get("data");
        assertEquals("USER", account.get("role").asText());
        assertTrue(account.get("userId").isTextual());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        var login = http.postForEntity("/api/auth/login",
            Map.of("username", "http_customer", "password", "customer-secure-password"), JsonNode.class);
        assertEquals(200, login.getStatusCode().value());
        var headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().get("data").get("accessToken").asText());
        headers.set("X-User-Id", "99999");
        headers.set("X-User-Role", "ADMIN");
        var me = http.exchange("/api/users/me", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertEquals(200, me.getStatusCode().value());
        assertEquals(account.get("userId").asText(), me.getBody().get("data").get("userId").asText());
        assertEquals("USER", me.getBody().get("data").get("role").asText());
        assertEquals(403, http.exchange("/api/admin/products", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
            .getStatusCode().value());
        headers.remove(HttpHeaders.AUTHORIZATION);
        assertEquals(401, http.exchange("/api/users/me", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
            .getStatusCode().value());
        assertEquals(401, http.postForEntity("/api/auth/login",
            Map.of("username", "http_customer", "password", "wrong-password"), JsonNode.class).getStatusCode().value());
    }
}
