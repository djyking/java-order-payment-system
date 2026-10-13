package com.acme.order.user;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private static final RowMapper<Account> MAPPER = (rs, n) -> new Account(rs.getLong("id"),
        rs.getString("username"), rs.getString("password_hash"), rs.getString("role"), rs.getInt("status") == 1);
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Account> byUsername(String username) {
        return jdbc.query("SELECT * FROM sys_user WHERE username=?", MAPPER, username).stream().findFirst();
    }
    public Optional<Account> byId(long id) {
        return jdbc.query("SELECT * FROM sys_user WHERE id=?", MAPPER, id).stream().findFirst();
    }
    public void insert(Account account) {
        jdbc.update("INSERT INTO sys_user(id,username,password_hash,role,status) VALUES(?,?,?,?,?)",
            account.id(), account.username(), account.passwordHash(), account.role(), account.active() ? 1 : 0);
    }
    public record Account(long id, String username, String passwordHash, String role, boolean active) { }
}
