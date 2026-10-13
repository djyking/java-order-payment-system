package com.acme.order.payment;

import java.util.UUID;

import com.acme.order.api.payment.PaymentDtos.PayView;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import com.acme.order.common.core.Ids;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 支付单、支付通知和支付事件的数据访问组件。
 *
 * @author heyu
 * @since 2026-08-20
 */
@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate j) {
        jdbc = j;
    }

    public PayView create(String order, long user, long amount, String method) {
        try {
            long id = Ids.next();
            String no = Ids.payNo(id);
            jdbc.update(
                "INSERT INTO pay_order(id,pay_order_no,biz_order_no,user_id,amount_cent,status,channel_code,version,"
                    + "create_time,update_time) VALUES(?,?,?,?,?,10,?,0,NOW(3),NOW(3))",
                id, no, order, user, amount, method);
            return view(no, order, amount, 10, method);
        } catch (DuplicateKeyException e) {
            var existing = byOrder(order, user);
            if (existing.amountCent() != amount) {
                throw new BizException(ErrorCode.PAY_AMOUNT_MISMATCH, "支付单金额与订单金额不一致");
            }
            if (!existing.methodCode().equals(method)) {
                throw new BizException(ErrorCode.PAY_METHOD_CONFLICT, "订单已有其他方式的支付单，请继续使用原支付方式");
            }
            return existing;
        }
    }

    public PayView get(String no, long user) {
        return jdbc.query("SELECT pay_order_no,biz_order_no,amount_cent,status,channel_code FROM pay_order WHERE pay_order_no=? AND user_id=?",
            r -> {
                if (!r.next()) {
                    throw new BizException(ErrorCode.PAY_ORDER_NOT_FOUND, "支付单不存在");
                }
                return view(r.getString(1), r.getString(2), r.getLong(3), r.getInt(4), r.getString(5));
            }, no, user);
    }

    public PayView byOrder(String order, long user) {
        return jdbc.query("SELECT pay_order_no,biz_order_no,amount_cent,status,channel_code FROM pay_order WHERE biz_order_no=? AND user_id=?",
            r -> {
                if (!r.next()) {
                    throw new BizException(ErrorCode.PAY_ORDER_NOT_FOUND, "支付单不存在");
                }
                return view(r.getString(1), r.getString(2), r.getLong(3), r.getInt(4), r.getString(5));
            }, order, user);
    }

    public boolean success(String no, long userId, String notifyId) {
        var p = get(no, userId);
        if (!"MOCK".equals(p.methodCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅开发测试支付单支持此操作");
        }
        try {
            jdbc.update(
                "INSERT INTO pay_notify_log(id,notify_id,pay_order_no,channel_code,notify_payload,process_status,"
                    + "create_time,update_time) VALUES(?,?,?,'MOCK','{}',0,NOW(3),NOW(3))",
                Ids.next(), notifyId, no);
        } catch (DuplicateKeyException e) {
            return false;
        }
        int n = jdbc.update(
            "UPDATE pay_order SET status=20,pay_time=NOW(3),version=version+1,update_time=NOW(3) "
                + "WHERE pay_order_no=? AND user_id=? AND channel_code='MOCK' AND status=10",
            no, userId);
        if (n == 1) {
            String event = UUID.randomUUID().toString();
            String payload = "{\"orderNo\":\"" + p.orderNo() + "\",\"payOrderNo\":\"" + no + "\",\"userId\":" + userId
                + "}";
            jdbc.update(
                "INSERT INTO outbox_event(id,event_id,event_type,aggregate_type,aggregate_id,payload,event_status,"
                    + "retry_count,next_retry_time,create_time,update_time) "
                    + "VALUES(?,?,'PaymentSucceeded','PAYMENT',?,?,0,0,NOW(3),NOW(3),NOW(3))",
                Ids.next(), event, no, payload);
            jdbc.update("UPDATE pay_notify_log SET process_status=1,update_time=NOW(3) WHERE notify_id=?", notifyId);
            return true;
        }
        return false;
    }

    private PayView view(String no, String order, long amount, int status, String method) {
        String action = switch (method) {
            case "ALIPAY_PAGE" -> "REDIRECT";
            case "WECHAT_NATIVE" -> "QR_CODE";
            case "MOCK" -> "MOCK";
            default -> "NONE";
        };
        return new PayView(no, order, amount, status == 20 ? "PAID" : "UNPAID", method, action, null, null, null);
    }
}
