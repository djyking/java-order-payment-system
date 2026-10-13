package com.acme.order.api.user;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 用户服务接口使用的数据传输对象集合。
 *
 * @author heyu
 * @since 2026-07-15
 */
public final class UserDtos {

    private UserDtos() {
    }

    /**
     * 用户登录请求。
     *
     * @param username 用户名
     * @param password 密码
     * @author heyu
     * @since 2026-07-15
     */
    public record LoginRequest(String username, String password) {
    }

    /**
     * 用户登录结果。
     *
     * @param userId 用户标识
     * @param username 用户名
     * @param role 用户角色
     * @param accessToken 访问令牌
     * @author heyu
     * @since 2026-07-15
     */
    public record LoginResponse(@JsonFormat(shape = JsonFormat.Shape.STRING) long userId, String username, String role, String accessToken) {
    }

    /**
     * 用户信息视图。
     *
     * @param userId 用户标识
     * @param username 用户名
     * @param role 用户角色
     * @author heyu
     * @since 2026-07-15
     */
    public record UserView(@JsonFormat(shape = JsonFormat.Shape.STRING) long userId, String username, String role) {
    }
}
