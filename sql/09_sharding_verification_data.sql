-- 分片容量验收数据：10000 个用户，每个用户 10 笔订单，共 100000 笔。
-- 数据前缀 V 仅用于验收，可重复执行；INSERT IGNORE 保证脚本幂等。
-- @author heyu
-- @since 2026-09-02

USE order_trade;
SET SESSION cte_max_recursion_depth = 100001;

DROP TEMPORARY TABLE IF EXISTS sharding_verification_order;
CREATE TEMPORARY TABLE sharding_verification_order (
    seq_no INT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL,
    UNIQUE KEY uk_verification_order_no(order_no),
    KEY idx_verification_route(user_id,order_id)
) ENGINE=InnoDB;

INSERT INTO sharding_verification_order(seq_no,order_id,order_no,user_id,create_time)
WITH RECURSIVE sequence_generator AS (
    SELECT 0 AS seq_no
    UNION ALL
    SELECT seq_no + 1 FROM sequence_generator WHERE seq_no < 99999
)
SELECT seq_no,
       92000000000000000 + seq_no * 8
           + MOD(FLOOR(FLOOR(seq_no / 10) / 2) * 10 + MOD(seq_no,10),8),
       CONCAT('V',92000000000000000 + seq_no * 8
           + MOD(FLOOR(FLOOR(seq_no / 10) / 2) * 10 + MOD(seq_no,10),8)),
       30000 + FLOOR(seq_no / 10),
       TIMESTAMPADD(MICROSECOND,MOD(seq_no,86400000) * 1000,'2026-09-02 00:00:00.000')
FROM sequence_generator;

DELIMITER $$
CREATE PROCEDURE order_trade.load_sharding_verification_data()
BEGIN
    DECLARE database_no INT DEFAULT 0;
    DECLARE table_no INT DEFAULT 0;
    WHILE database_no < 2 DO
        SET @clear_route_sql = CONCAT(
            'DELETE FROM order_trade_',database_no,'.order_route WHERE order_no LIKE ''V%''');
        PREPARE clear_route_statement FROM @clear_route_sql;
        EXECUTE clear_route_statement;
        DEALLOCATE PREPARE clear_route_statement;

        SET @clear_outbox_sql = CONCAT(
            'DELETE FROM order_trade_',database_no,'.outbox_event WHERE aggregate_id LIKE ''V%''');
        PREPARE clear_outbox_statement FROM @clear_outbox_sql;
        EXECUTE clear_outbox_statement;
        DEALLOCATE PREPARE clear_outbox_statement;

        SET @clear_reconciliation_sql = CONCAT(
            'DELETE FROM order_trade_',database_no,
            '.reconciliation_exception WHERE biz_no LIKE ''V%''');
        PREPARE clear_reconciliation_statement FROM @clear_reconciliation_sql;
        EXECUTE clear_reconciliation_statement;
        DEALLOCATE PREPARE clear_reconciliation_statement;

        SET @route_sql = CONCAT(
            'INSERT IGNORE INTO order_trade_',database_no,
            '.order_route(order_no,order_id,user_id,db_shard,table_shard,create_time) ',
            'SELECT order_no,order_id,user_id,MOD(user_id,2),MOD(order_id,8),create_time ',
            'FROM sharding_verification_order WHERE MOD(user_id,2)=',database_no);
        PREPARE route_statement FROM @route_sql;
        EXECUTE route_statement;
        DEALLOCATE PREPARE route_statement;

        SET table_no = 0;
        WHILE table_no < 8 DO
            SET @clear_item_sql = CONCAT(
                'DELETE FROM order_trade_',database_no,'.trade_order_item_',table_no,
                ' WHERE order_no LIKE ''V%''');
            PREPARE clear_item_statement FROM @clear_item_sql;
            EXECUTE clear_item_statement;
            DEALLOCATE PREPARE clear_item_statement;

            SET @clear_status_sql = CONCAT(
                'DELETE FROM order_trade_',database_no,'.trade_order_status_log_',table_no,
                ' WHERE order_no LIKE ''V%''');
            PREPARE clear_status_statement FROM @clear_status_sql;
            EXECUTE clear_status_statement;
            DEALLOCATE PREPARE clear_status_statement;

            SET @clear_order_sql = CONCAT(
                'DELETE FROM order_trade_',database_no,'.trade_order_',table_no,
                ' WHERE order_no LIKE ''V%''');
            PREPARE clear_order_statement FROM @clear_order_sql;
            EXECUTE clear_order_statement;
            DEALLOCATE PREPARE clear_order_statement;

            SET @order_sql = CONCAT(
                'INSERT IGNORE INTO order_trade_',database_no,'.trade_order_',table_no,
                '(order_id,order_no,user_id,status,pay_status,total_amount_cent,pay_amount_cent,item_count,',
                'version,create_time,update_time) ',
                'SELECT order_id,order_no,user_id,40,20,9900,9900,1,2,create_time,create_time ',
                'FROM sharding_verification_order WHERE MOD(user_id,2)=',database_no,
                ' AND MOD(order_id,8)=',table_no);
            PREPARE order_statement FROM @order_sql;
            EXECUTE order_statement;
            DEALLOCATE PREPARE order_statement;

            SET @item_sql = CONCAT(
                'INSERT IGNORE INTO order_trade_',database_no,'.trade_order_item_',table_no,
                '(id,order_id,order_no,user_id,spu_id,sku_id,spu_name,sku_name,price_cent,quantity,',
                'total_amount_cent,create_time,update_time) ',
                'SELECT order_id,order_id,order_no,user_id,1000,10001,''Verification Product'',',
                '''Verification SKU'',9900,1,9900,create_time,create_time ',
                'FROM sharding_verification_order WHERE MOD(user_id,2)=',database_no,
                ' AND MOD(order_id,8)=',table_no);
            PREPARE item_statement FROM @item_sql;
            EXECUTE item_statement;
            DEALLOCATE PREPARE item_statement;

            SET @status_sql = CONCAT(
                'INSERT IGNORE INTO order_trade_',database_no,'.trade_order_status_log_',table_no,
                '(id,order_id,order_no,user_id,before_status,after_status,operate_type,operator_id,remark,',
                'create_time) ',
                'SELECT order_id,order_id,order_no,user_id,NULL,40,''IMPORT_VERIFICATION'',user_id,',
                '''sharding verification data'',create_time ',
                'FROM sharding_verification_order WHERE MOD(user_id,2)=',database_no,
                ' AND MOD(order_id,8)=',table_no);
            PREPARE status_statement FROM @status_sql;
            EXECUTE status_statement;
            DEALLOCATE PREPARE status_statement;

            SET table_no = table_no + 1;
        END WHILE;
        SET database_no = database_no + 1;
    END WHILE;
END$$
DELIMITER ;

CALL order_trade.load_sharding_verification_data();
DROP PROCEDURE order_trade.load_sharding_verification_data;
DROP TEMPORARY TABLE sharding_verification_order;
