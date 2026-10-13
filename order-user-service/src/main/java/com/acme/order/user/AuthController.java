package com.acme.order.user;

import com.acme.order.api.user.UserDtos.LoginResponse;
import com.acme.order.api.user.UserDtos.UserView;
import com.acme.order.common.core.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
    private final UserService users;
    public AuthController(UserService users) { this.users = users; }
    @PostMapping("/api/auth/login")
    ApiResponse<LoginResponse> login(@Valid @RequestBody LoginBody body) {
        return ApiResponse.ok(users.login(body.username(), body.password()), MDC.get("traceId"));
    }
    @PostMapping("/api/auth/register")
    ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterBody body) {
        return ApiResponse.ok(users.register(body.username(), body.password()), MDC.get("traceId"));
    }
    @GetMapping("/api/users/me")
    ApiResponse<UserView> me(@RequestHeader("X-User-Id") long userId) {
        return ApiResponse.ok(users.me(userId), MDC.get("traceId"));
    }
    public record LoginBody(@NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password) { }
    public record RegisterBody(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}",
            message = "须为 3 至 64 位字母、数字、点、下划线或短横线") String username,
            @NotBlank @Size(min = 10, max = 128) String password) { }
}
