-- 下单请求与订单在同一事务中持久化，防止客户端重试或 Redis 缓存过期后重复创建订单。
CREATE TABLE IF NOT EXISTS order_trade.order_create_request (
    user_id BIGINT NOT NULL,
    request_token VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, request_token),
    UNIQUE KEY uk_request_order_no (order_no)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS order_trade_0.order_create_request LIKE order_trade.order_create_request;
CREATE TABLE IF NOT EXISTS order_trade_1.order_create_request LIKE order_trade.order_create_request;
