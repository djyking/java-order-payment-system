package com.acme.order.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 订单业务指标记录器。
 *
 * @author heyu
 * @since 2026-07-15
 */
@Component
public final class OrderMetrics {

    private final MeterRegistry registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void success(String operation) {
        registry.counter(operation + "_success_total").increment();
    }

    public void failure(String operation) {
        registry.counter(operation + "_failure_total").increment();
    }

    public void increment(String metricName) {
        registry.counter(metricName).increment();
    }

    public void increment(String metricName, String tagName, String tagValue) {
        registry.counter(metricName, tagName, tagValue).increment();
    }
}
