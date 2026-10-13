package com.acme.order.payment;

import com.acme.order.api.payment.PaymentDtos.MethodView;
import com.acme.order.api.payment.PaymentDtos.PayView;

/** 每种支付方式由独立适配器提供能力与收银动作；渠道交易及验签在适配器中实现。 */
public interface PaymentProvider {
    MethodView method();

    PayView checkout(PayView payment);
}
