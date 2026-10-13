package com.acme.order.order;

import java.time.Duration;

import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 通过 Redis 路由缓存和数据库路由表将订单号解析为订单分片主键。
 *
 * @author heyu
 * @since 2026-09-02
 */
@Component
public class OrderRouteResolver {

    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final String CACHE_PREFIX = "order:route:";

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public OrderRouteResolver(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    public Route resolve(String orderNo, long expectedUserId) {
        Route cached = readCache(orderNo);
        if (cached != null) {
            requireOwner(cached, expectedUserId);
            return cached;
        }
        Route route = jdbc.query(
            "SELECT order_id,user_id FROM order_route WHERE order_no=? AND user_id=?",
            result -> result.next() ? new Route(result.getLong(1), result.getLong(2)) : null,
            orderNo, expectedUserId);
        if (route == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        writeCache(orderNo, route);
        return route;
    }

    private Route readCache(String orderNo) {
        try {
            String value = redis.opsForValue().get(CACHE_PREFIX + orderNo);
            if (value == null) {
                return null;
            }
            String[] parts = value.split(":", 2);
            if (parts.length != 2) {
                redis.delete(CACHE_PREFIX + orderNo);
                return null;
            }
            return new Route(Long.parseLong(parts[1]), Long.parseLong(parts[0]));
        } catch (DataAccessException | NumberFormatException ignored) {
            return null;
        }
    }

    private void writeCache(String orderNo, Route route) {
        try {
            redis.opsForValue().set(CACHE_PREFIX + orderNo, route.userId() + ":" + route.orderId(), CACHE_TTL);
        } catch (DataAccessException ignored) {
            // Redis 仅保存加速路由，失败时数据库路由表仍是事实源。
        }
    }

    private void requireOwner(Route route, long expectedUserId) {
        if (route.userId() != expectedUserId) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
    }

    public record Route(long orderId, long userId) {
    }
}
