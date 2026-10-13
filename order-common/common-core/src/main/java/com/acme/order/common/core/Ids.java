package com.acme.order.common.core;

import java.time.Instant;

/**
 * 业务主键和单号生成工具。
 *
 * @author heyu
 * @since 2026-07-15
 */
public final class Ids {

    private static final long CUSTOM_EPOCH = 1_767_225_600_000L;
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS;
    private static final long WORKER_ID = resolveWorkerId();

    private static long lastTimestamp = -1L;
    private static long sequence;

    private Ids() {
    }

    public static synchronized long next() {
        long timestamp = Instant.now().toEpochMilli();
        if (timestamp < lastTimestamp) {
            long drift = lastTimestamp - timestamp;
            if (drift > 5) {
                throw new IllegalStateException("Clock moved backwards by " + drift + " ms");
            }
            timestamp = waitUntil(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT) | (WORKER_ID << WORKER_ID_SHIFT) | sequence;
    }

    public static String orderNo(long id) {
        return "O" + id;
    }

    public static String payNo(long id) {
        return "P" + id;
    }

    private static long waitUntil(long targetTimestamp) {
        long current = Instant.now().toEpochMilli();
        while (current < targetTimestamp) {
            Thread.onSpinWait();
            current = Instant.now().toEpochMilli();
        }
        return current;
    }

    private static long resolveWorkerId() {
        String configured = System.getenv("ORDER_WORKER_ID");
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("order.worker-id");
        }
        if (configured != null && !configured.isBlank()) {
            long value = Long.parseLong(configured);
            if (value < 0 || value > MAX_WORKER_ID) {
                throw new IllegalArgumentException("ORDER_WORKER_ID must be between 0 and " + MAX_WORKER_ID);
            }
            return value;
        }
        String host = System.getenv().getOrDefault("COMPUTERNAME",
            System.getenv().getOrDefault("HOSTNAME", "unknown-host"));
        return Math.floorMod((host + ':' + ProcessHandle.current().pid()).hashCode(), (int) MAX_WORKER_ID + 1);
    }
}
