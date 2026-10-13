package com.acme.order.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserService users;
    private final String username;
    private final String password;
    public AdminBootstrap(UserService users,
            @Value("${security.bootstrap-admin.username:${BOOTSTRAP_ADMIN_USERNAME:}}") String username,
            @Value("${security.bootstrap-admin.password:${BOOTSTRAP_ADMIN_PASSWORD:}}") String password) {
        this.users = users;
        this.username = username;
        this.password = password;
    }
    @Override public void run(ApplicationArguments args) { users.bootstrapAdmin(username, password); }
}
