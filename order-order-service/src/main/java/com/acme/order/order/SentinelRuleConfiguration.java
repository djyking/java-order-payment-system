package com.acme.order.order;

import java.util.List;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * 订单主链路的本地基础治理规则。生产环境可由 Nacos 数据源覆盖并持久化。
 *
 * @author heyu
 * @since 2026-09-02
 */
@Configuration
public class SentinelRuleConfiguration {

    @PostConstruct
    void loadRules() {
        FlowRule create = new FlowRule("order.create");
        create.setGrade(RuleConstant.FLOW_GRADE_QPS);
        create.setCount(50);
        FlowRuleManager.loadRules(List.of(create));

        DegradeRule inventorySlow = new DegradeRule("order.inventory");
        inventorySlow.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        inventorySlow.setCount(800);
        inventorySlow.setSlowRatioThreshold(0.5);
        inventorySlow.setMinRequestAmount(5);
        inventorySlow.setStatIntervalMs(10_000);
        inventorySlow.setTimeWindow(10);

        DegradeRule paymentErrors = new DegradeRule("order.payment");
        paymentErrors.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        paymentErrors.setCount(0.5);
        paymentErrors.setMinRequestAmount(5);
        paymentErrors.setStatIntervalMs(10_000);
        paymentErrors.setTimeWindow(10);
        DegradeRuleManager.loadRules(List.of(inventorySlow, paymentErrors));
    }
}
