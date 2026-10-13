package com.acme.order.common.core;

/**
 * 系统统一错误码。
 *
 * @author heyu
 * @since 2026-07-15
 */
public enum ErrorCode {
    /**
     * 订单不存在。
     */
    ORDER_NOT_FOUND,
    /**
     * 订单状态不允许执行当前操作。
     */
    ORDER_STATUS_INVALID,
    /**
     * 订单重复提交。
     */
    ORDER_DUPLICATE_SUBMIT,
    /**
     * 订单已经支付。
     */
    ORDER_ALREADY_PAID,
    /**
     * 订单已经取消。
     */
    ORDER_ALREADY_CANCELED,
    /**
     * 商品不存在。
     */
    PRODUCT_NOT_FOUND,
    /**
     * SKU 不存在。
     */
    SKU_NOT_FOUND,
    /**
     * SKU 已下架。
     */
    SKU_OFF_SHELF,
    /**
     * 库存不足。
     */
    INVENTORY_NOT_ENOUGH,
    /**
     * 库存预占记录不存在。
     */
    INVENTORY_RESERVATION_NOT_FOUND,
    /**
     * 库存已经释放。
     */
    INVENTORY_ALREADY_RELEASED,
    /**
     * 支付单不存在。
     */
    PAY_ORDER_NOT_FOUND,
    /**
     * 支付金额不匹配。
     */
    PAY_AMOUNT_MISMATCH,
    /**
     * 支付通知重复。
     */
    PAY_NOTIFY_DUPLICATED,
    PAY_METHOD_UNAVAILABLE,
    PAY_METHOD_CONFLICT,
    /**
     * 消息处理失败。
     */
    MQ_PROCESS_FAILED,
    /**
     * 系统繁忙。
     */
    SYSTEM_BUSY,
    /**
     * 请求触发限流。
     */
    RATE_LIMITED,
    /**
     * 请求未认证。
     */
    UNAUTHORIZED,
    /**
     * 请求无访问权限。
     */
    FORBIDDEN,
    /**
     * 请求参数校验失败。
     */
    VALIDATION_FAILED
}
