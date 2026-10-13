package com.acme.order.common.mq;

import java.time.OffsetDateTime;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 基于消费日志实现的消息幂等守卫。
 *
 * @author heyu
 * @since 2026-07-15
 */
public final class MqConsumeGuard {

    private final JdbcTemplate jdbc;

    public MqConsumeGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean first(String group, String eventId) {
        return first(group, eventId, 0L);
    }

    public boolean first(String group, String eventId, long userId) {
        try {
            jdbc.update(
                "INSERT INTO mq_consume_log(id,user_id,consumer_group,event_id,consume_status,create_time,update_time) "
                    + "VALUES(?,?,?,?,?,?,?)",
                Math.abs((group + eventId).hashCode()), userId, group, eventId, 0, OffsetDateTime.now(),
                OffsetDateTime.now());
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    public void success(String group, String eventId) {
        success(group, eventId, 0L);
    }

    public void success(String group, String eventId, long userId) {
        jdbc.update(
            "UPDATE mq_consume_log SET consume_status=1,update_time=NOW(3) "
                + "WHERE consumer_group=? AND event_id=? AND user_id=?",
            group, eventId, userId);
    }
}
