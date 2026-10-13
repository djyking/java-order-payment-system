package com.acme.order.payment;

import com.acme.order.api.payment.PaymentDtos.CreateRequest;
import com.acme.order.api.payment.PaymentDtos.PayView;
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

    @Value("${debug.failure.create-error:false}")
    private boolean createError;

    public PaymentService(PaymentRepository r, OrderMetrics metrics) {
        repo = r;
        this.metrics = metrics;
    }

    @Transactional
    public PayView create(CreateRequest r) {
        if (createError) {
            throw new IllegalStateException("fault injection: payment create error");
        }
        return repo.create(r.orderNo(), r.userId(), r.amountCent());
    }

    @Transactional
    public boolean mockSuccess(String no, String notify) {
        boolean changed = repo.success(no, notify);
        if (changed) {
            metrics.increment("payment_success_total");
        } else {
            metrics.increment("payment_callback_duplicate_total");
        }
        return changed;
    }

    public PayView get(String no) {
        return repo.get(no);
    }
}
