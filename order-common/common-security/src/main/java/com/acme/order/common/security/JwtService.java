package com.acme.order.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 签发与解析服务。
 *
 * @author heyu
 * @since 2026-07-15
 */
public final class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(String secret, Duration ttl) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
            || secret.startsWith("change-this-")) {
            throw new IllegalArgumentException("JWT_SECRET must be configured with at least 32 random bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public String issue(long userId, String role) {
        var now = Instant.now();
        return Jwts.builder().subject(Long.toString(userId)).claim("role", role).issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl))).signWith(key).compact();
    }

    public Claims parse(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (Long.parseLong(claims.getSubject()) <= 0
            || !("USER".equals(claims.get("role", String.class)) || "ADMIN".equals(claims.get("role", String.class)))) {
            throw new IllegalArgumentException("Invalid token identity");
        }
        return claims;
    }
}
