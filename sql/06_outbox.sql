USE order_trade;
CREATE TABLE IF NOT EXISTS outbox_event(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL DEFAULT 0,event_id VARCHAR(64) NOT NULL,event_type VARCHAR(64) NOT NULL,aggregate_type VARCHAR(64) NOT NULL,aggregate_id VARCHAR(64) NOT NULL,payload JSON NOT NULL,event_status TINYINT NOT NULL,retry_count INT NOT NULL DEFAULT 0,next_retry_time DATETIME(3),published_time DATETIME(3),last_error VARCHAR(512),create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),UNIQUE KEY uk_event_id(event_id),KEY idx_user_status_retry(user_id,event_status,next_retry_time),KEY idx_status_retry(event_status,next_retry_time)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS mq_consume_log(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL DEFAULT 0,consumer_group VARCHAR(64) NOT NULL,event_id VARCHAR(64) NOT NULL,consume_status TINYINT NOT NULL,error_message VARCHAR(512),create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),UNIQUE KEY uk_group_event(consumer_group,event_id),KEY idx_user_event(user_id,event_id)) ENGINE=InnoDB;
USE order_payment;
CREATE TABLE IF NOT EXISTS outbox_event LIKE order_trade.outbox_event;
USE order_inventory;
CREATE TABLE IF NOT EXISTS mq_consume_log LIKE order_trade.mq_consume_log;
