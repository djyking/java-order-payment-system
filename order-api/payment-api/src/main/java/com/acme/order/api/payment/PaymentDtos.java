package com.acme.order.api.payment;

/**
 * 支付服务接口使用的数据传输对象集合。
 *
 * @author heyu
 * @since 2026-07-15
 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * 支付单创建请求。
     *
     * @param orderNo 订单号
     * @param userId 用户标识
     * @param amountCent 金额，单位为分
     * @author heyu
     * @since 2026-07-15
     */
    public record CreateRequest(String orderNo, long userId, long amountCent, String methodCode) {
    }

    /**
     * 支付单视图。
     *
     * @param payOrderNo 支付单号
     * @param orderNo 订单号
     * @param amountCent 金额，单位为分
     * @param status 状态
     * @author heyu
     * @since 2026-07-15
     */
    public record PayView(String payOrderNo, String orderNo, long amountCent, String status, String methodCode,
        String actionType, String payUrl, String qrCode, String expiresAt) {
    }

    public record MethodView(String code, String name, boolean available, String reason, String actionType) {
    }
}
