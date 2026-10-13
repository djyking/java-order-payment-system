package com.acme.order.payment;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.acme.order.api.payment.PaymentDtos.MethodView;
import com.acme.order.api.payment.PaymentDtos.PayView;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/** 注册表支持新增渠道适配器，不把尚未接通的真实渠道标记为可用。 */
@Component
public class PaymentProviders {
    private final Map<String, PaymentProvider> providers = new LinkedHashMap<>();

    public PaymentProviders(List<PaymentProvider> adapters) {
        for (var adapter : adapters) {
            if (providers.putIfAbsent(adapter.method().code(), adapter) != null) {
                throw new IllegalArgumentException("Duplicate payment method: " + adapter.method().code());
            }
        }
    }

    public List<MethodView> methods() {
        return providers.values().stream().map(PaymentProvider::method).toList();
    }

    public PaymentProvider requireAvailable(String code) {
        var adapter = providers.get(code);
        if (adapter == null || !adapter.method().available()) {
            throw new BizException(ErrorCode.PAY_METHOD_UNAVAILABLE,
                adapter == null ? "支付方式不可用" : adapter.method().reason());
        }
        return adapter;
    }

    @Configuration
    static class AdapterConfiguration {
        @Bean
        PaymentProvider alipayPage() {
            return unavailable("ALIPAY_PAGE", "支付宝电脑网站支付", "REDIRECT");
        }

        @Bean
        PaymentProvider wechatNative() {
            return unavailable("WECHAT_NATIVE", "微信扫码支付", "QR_CODE");
        }

        @Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "payment.mock-enabled", havingValue = "true")
        PaymentProvider mockProvider() {
            return new PaymentProvider() {
                public MethodView method() {
                    return new MethodView("MOCK", "开发测试支付", true, "仅用于开发环境联调", "MOCK");
                }
                public PayView checkout(PayView p) { return p; }
            };
        }

        private PaymentProvider unavailable(String code, String name, String action) {
            return new PaymentProvider() {
                public MethodView method() {
                    return new MethodView(code, name, false, "待商户账号/API 配置与渠道联调后开放", action);
                }
                public PayView checkout(PayView p) {
                    throw new BizException(ErrorCode.PAY_METHOD_UNAVAILABLE, method().reason());
                }
            };
        }
    }
}
