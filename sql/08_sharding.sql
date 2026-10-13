CREATE DATABASE IF NOT EXISTS order_trade_0 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS order_trade_1 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON order_trade_0.* TO 'order'@'%';
GRANT ALL PRIVILEGES ON order_trade_1.* TO 'order'@'%';

-- 兼容早期单库版本：主订单分片键统一命名为 order_id，使三张绑定表拥有相同分片结构。
SET @base_has_legacy_id = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'order_trade' AND table_name = 'trade_order' AND column_name = 'id'
);
SET @base_migration = IF(@base_has_legacy_id > 0,
    'ALTER TABLE order_trade.trade_order CHANGE COLUMN id order_id BIGINT NOT NULL',
    'SELECT 1');
PREPARE stmt_base_migration FROM @base_migration;
EXECUTE stmt_base_migration;
DEALLOCATE PREPARE stmt_base_migration;

DELIMITER $$
CREATE PROCEDURE order_trade.ensure_user_sharding_column(IN schema_name VARCHAR(64), IN table_name_value VARCHAR(64))
BEGIN
    SET @has_user_id = (
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = schema_name AND table_name = table_name_value AND column_name = 'user_id'
    );
    IF @has_user_id = 0 THEN
        SET @add_user_id = CONCAT('ALTER TABLE ', schema_name, '.', table_name_value,
            ' ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0 AFTER id');
        PREPARE stmt_add_user_id FROM @add_user_id;
        EXECUTE stmt_add_user_id;
        DEALLOCATE PREPARE stmt_add_user_id;
    END IF;
END$$
DELIMITER ;

CALL order_trade.ensure_user_sharding_column('order_trade', 'outbox_event');
CALL order_trade.ensure_user_sharding_column('order_trade', 'mq_consume_log');
CALL order_trade.ensure_user_sharding_column('order_trade', 'reconciliation_exception');

DELIMITER $$
CREATE PROCEDURE order_trade.create_order_shards()
BEGIN
    DECLARE shard_no INT DEFAULT 0;
    DECLARE table_no INT DEFAULT 0;
    WHILE shard_no < 2 DO
        SET table_no = 0;
        WHILE table_no < 8 DO
            SET @ddl_order = CONCAT('CREATE TABLE IF NOT EXISTS order_trade_', shard_no,
                '.trade_order_', table_no, ' LIKE order_trade.trade_order');
            PREPARE stmt_order FROM @ddl_order; EXECUTE stmt_order; DEALLOCATE PREPARE stmt_order;
            SET @has_legacy_id = (
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = CONCAT('order_trade_', shard_no)
                  AND table_name = CONCAT('trade_order_', table_no)
                  AND column_name = 'id'
            );
            IF @has_legacy_id > 0 THEN
                SET @migrate_order = CONCAT('ALTER TABLE order_trade_', shard_no,
                    '.trade_order_', table_no, ' CHANGE COLUMN id order_id BIGINT NOT NULL');
                PREPARE stmt_migrate FROM @migrate_order;
                EXECUTE stmt_migrate;
                DEALLOCATE PREPARE stmt_migrate;
            END IF;
            SET @ddl_item = CONCAT('CREATE TABLE IF NOT EXISTS order_trade_', shard_no,
                '.trade_order_item_', table_no, ' LIKE order_trade.trade_order_item');
            PREPARE stmt_item FROM @ddl_item; EXECUTE stmt_item; DEALLOCATE PREPARE stmt_item;
            SET @ddl_log = CONCAT('CREATE TABLE IF NOT EXISTS order_trade_', shard_no,
                '.trade_order_status_log_', table_no, ' LIKE order_trade.trade_order_status_log');
            PREPARE stmt_log FROM @ddl_log; EXECUTE stmt_log; DEALLOCATE PREPARE stmt_log;
            SET table_no = table_no + 1;
        END WHILE;
        SET shard_no = shard_no + 1;
    END WHILE;
END$$
DELIMITER ;

CALL order_trade.create_order_shards();
DROP PROCEDURE order_trade.create_order_shards;

CREATE TABLE IF NOT EXISTS order_trade_0.outbox_event LIKE order_trade.outbox_event;
CREATE TABLE IF NOT EXISTS order_trade_0.mq_consume_log LIKE order_trade.mq_consume_log;
CREATE TABLE IF NOT EXISTS order_trade_0.order_route LIKE order_trade.order_route;
CREATE TABLE IF NOT EXISTS order_trade_0.reconciliation_exception LIKE order_trade.reconciliation_exception;
CREATE TABLE IF NOT EXISTS order_trade_1.outbox_event LIKE order_trade.outbox_event;
CREATE TABLE IF NOT EXISTS order_trade_1.mq_consume_log LIKE order_trade.mq_consume_log;
CREATE TABLE IF NOT EXISTS order_trade_1.order_route LIKE order_trade.order_route;
CREATE TABLE IF NOT EXISTS order_trade_1.reconciliation_exception LIKE order_trade.reconciliation_exception;

CALL order_trade.ensure_user_sharding_column('order_trade_0', 'outbox_event');
CALL order_trade.ensure_user_sharding_column('order_trade_0', 'mq_consume_log');
CALL order_trade.ensure_user_sharding_column('order_trade_0', 'reconciliation_exception');
CALL order_trade.ensure_user_sharding_column('order_trade_1', 'outbox_event');
CALL order_trade.ensure_user_sharding_column('order_trade_1', 'mq_consume_log');
CALL order_trade.ensure_user_sharding_column('order_trade_1', 'reconciliation_exception');
CALL order_trade.ensure_user_sharding_column('order_inventory', 'mq_consume_log');
CALL order_trade.ensure_user_sharding_column('order_query', 'mq_consume_log');

DROP PROCEDURE order_trade.ensure_user_sharding_column;
