package com.acme.order.user;

import java.time.Duration;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import com.acme.order.common.security.JwtService;
import com.acme.order.common.security.PasswordHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {
    private JdbcTemplate jdbc;
    private UserRepository repository;
    private UserService users;
    private final JwtService jwt = new JwtService("test-user-jwt-secret-longer-than-thirty-two-bytes", Duration.ofMinutes(10));
    @BeforeEach void setup() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,username VARCHAR(64) UNIQUE,password_hash VARCHAR(255),role VARCHAR(32),status INT)");
        repository = new UserRepository(jdbc);
        users = new UserService(repository, jwt);
    }
    @Test void registerPersistsSaltedUserAndLoginReturnsRealIdentity() throws Exception {
        var first = users.register("alice", "a-secure-password");
        var second = users.register("bob", "a-secure-password");
        assertEquals("USER", first.role());
        var firstAccount = repository.byId(first.userId()).orElseThrow();
        assertTrue(firstAccount.passwordHash().startsWith("pbkdf2$600000$"));
        assertNotEquals(firstAccount.passwordHash(), repository.byId(second.userId()).orElseThrow().passwordHash());
        assertTrue(new PasswordHasher().matches("a-secure-password", firstAccount.passwordHash()));
        var login = users.login("alice", "a-secure-password");
        assertEquals(first.userId(), login.userId());
        assertEquals(Long.toString(first.userId()), jwt.parse(login.accessToken()).getSubject());
        assertEquals("alice", users.me(first.userId()).username());
        assertTrue(new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(login)).get("userId").isTextual());
    }
    @Test void duplicateRegistrationDoesNotOverwriteAccount() {
        var original = users.register("alice", "original-password");
        assertThrows(BizException.class, () -> users.register("alice", "different-password"));
        assertEquals(original.userId(), users.login("alice", "original-password").userId());
    }
    @Test void wrongPasswordsDisabledAccountsAndLegacyPlaintextCannotLogin() {
        var account = users.register("alice", "original-password");
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BizException.class,
            () -> users.login("alice", "wrong-password")).getCode());
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=?", account.userId());
        assertThrows(BizException.class, () -> users.login("alice", "original-password"));
        assertThrows(BizException.class, () -> users.me(account.userId()));
        repository.insert(new UserRepository.Account(10001, "demo", "{noop}demo123", "USER", true));
        assertThrows(BizException.class, () -> users.login("demo", "demo123"));
    }
    @Test void adminBootstrapIsExplicitIdempotentAndNeverPromotesRegisteredUsers() {
        users.bootstrapAdmin("", "");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        users.bootstrapAdmin("administrator", "admin-strong-password");
        users.bootstrapAdmin("administrator", "different-strong-password");
        assertEquals("ADMIN", users.login("administrator", "admin-strong-password").role());
        users.register("customer", "customer-password");
        assertThrows(IllegalStateException.class, () -> users.bootstrapAdmin("customer", "admin-strong-password"));
        assertEquals("USER", users.login("customer", "customer-password").role());
    }
}
