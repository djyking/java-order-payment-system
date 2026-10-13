package com.acme.order.payment;

import com.acme.order.api.payment.PaymentDtos.CreateRequest;
import com.acme.order.api.payment.PaymentDtos.PayView;
import com.acme.order.api.payment.PaymentDtos.MethodView;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import com.acme.order.common.observability.OrderMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

/**
 * 编排支付单创建、支付成功和支付查询业务。
 *
 * @author heyu
 * @since 2026-08-20
 */
@Service
public class PaymentService {

    private final PaymentRepository repo;
    private final OrderMetrics metrics;
    private final PaymentProviders providers;

    @Value("${debug.failure.create-error:false}")
    private boolean createError;

    public PaymentService(PaymentRepository r, OrderMetrics metrics, PaymentProviders providers) {
        repo = r;
        this.metrics = metrics;
        this.providers = providers;
    }

    @Transactional
    public PayView create(CreateRequest r) {
        if (createError) {
            throw new IllegalStateException("fault injection: payment create error");
        }
        if (r.orderNo() == null || !r.orderNo().matches("[A-Za-z0-9_-]{1,64}") || r.userId() <= 0 || r.amountCent() <= 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "订单号、用户或支付金额不合法");
        }
        var provider = providers.requireAvailable(r.methodCode());
        return provider.checkout(repo.create(r.orderNo(), r.userId(), r.amountCent(), r.methodCode()));
    }

    @Transactional
    public boolean mockSuccess(String no, long user, String notify) {
        providers.requireAvailable("MOCK");
        if (notify == null || notify.isBlank() || notify.length() > 64) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "通知标识应为 1 到 64 个字符");
        }
        boolean changed = repo.success(no, user, notify);
        if (changed) {
            metrics.increment("payment_success_total");
        } else {
            metrics.increment("payment_callback_duplicate_total");
        }
        return changed;
    }

    public PayView get(String no, long user) {
        return repo.get(no, user);
    }

    public java.util.List<MethodView> methods() {
        return providers.methods();
    }
}
