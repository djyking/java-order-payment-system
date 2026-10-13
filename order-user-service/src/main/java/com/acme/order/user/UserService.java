package com.acme.order.user;

import com.acme.order.api.user.UserDtos.LoginResponse;
import com.acme.order.api.user.UserDtos.UserView;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import com.acme.order.common.core.Ids;
import com.acme.order.common.security.JwtService;
import com.acme.order.common.security.PasswordHasher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository repository;
    private final JwtService jwt;
    private final PasswordHasher hasher = new PasswordHasher();
    // Compare a real hash for missing users too, avoiding an obvious username timing oracle.
    private final String dummyHash = hasher.hash(java.util.UUID.randomUUID().toString());
    public UserService(UserRepository repository, JwtService jwt) {
        this.repository = repository;
        this.jwt = jwt;
    }
    public LoginResponse register(String username, String password) {
        if (username == null || !username.matches("[A-Za-z0-9_.-]{3,64}") || password == null
                || password.isBlank() || password.length() < 10 || password.length() > 128) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "用户名或密码格式不符合要求");
        }
        var account = new UserRepository.Account(Ids.next(), username, hasher.hash(password), "USER", true);
        try {
            repository.insert(account);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "用户名已存在");
        }
        return session(account);
    }
    public LoginResponse login(String username, String password) {
        var account = repository.byUsername(username).orElse(null);
        boolean matches = hasher.matches(password, account == null ? dummyHash : account.passwordHash());
        if (!matches || account == null || !account.active()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return session(account);
    }
    public UserView me(long id) {
        var account = repository.byId(id).filter(UserRepository.Account::active)
            .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "账号不存在或已停用"));
        return new UserView(account.id(), account.username(), account.role());
    }
    /** Creates a first administrator only; it never promotes an existing ordinary account. */
    public void bootstrapAdmin(String username, String password) {
        if ((username == null || username.isBlank()) && (password == null || password.isBlank())) return;
        if (username == null || !username.matches("[A-Za-z0-9_.-]{3,64}") || password == null
                || password.isBlank() || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("Bootstrap admin needs a valid username and a 12-128 character password");
        }
        var existing = repository.byUsername(username);
        if (existing.isPresent()) {
            if (!"ADMIN".equals(existing.get().role()) || !existing.get().active()) {
                throw new IllegalStateException("Bootstrap username already belongs to a non-administrator or disabled account");
            }
            return;
        }
        try {
            repository.insert(new UserRepository.Account(Ids.next(), username, hasher.hash(password), "ADMIN", true));
        } catch (DuplicateKeyException ex) {
            var winner = repository.byUsername(username).orElseThrow(() -> ex);
            if (!"ADMIN".equals(winner.role()) || !winner.active()) {
                throw new IllegalStateException("Bootstrap username was claimed by another account", ex);
            }
        }
    }
    private LoginResponse session(UserRepository.Account account) {
        return new LoginResponse(account.id(), account.username(), account.role(), jwt.issue(account.id(), account.role()));
    }
}
