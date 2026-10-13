package com.acme.order.common.web;

import com.acme.order.common.core.ApiResponse;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Web 层统一异常处理器。
 *
 * @author heyu
 * @since 2026-07-15
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    ResponseEntity<ApiResponse<Void>> business(BizException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getCode(), ex.getMessage(), MDC.get("traceId")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
        var message = ex.getBindingResult().getFieldErrors().stream().findFirst()
            .map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("参数错误");
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, message, MDC.get("traceId")));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        LOG.error("Unhandled request exception, traceId={}", MDC.get("traceId"), ex);
        return ResponseEntity.status(500)
            .body(ApiResponse.error(ErrorCode.SYSTEM_BUSY, "系统繁忙，请稍后重试", MDC.get("traceId")));
    }
}
