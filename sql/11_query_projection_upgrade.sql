-- main 旧版查询投影升级：只补充缺失列，可重复执行，保留已有订单和投影数据。
-- 使用拥有 order_query ALTER 权限的账号，在启动新版 Query 服务前执行。
-- 新建环境由 07_query.sql 创建完整表；此脚本在新建环境中不会重复添加列。
USE order_query;

SET @projection_upgrade = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema='order_query' AND table_name='order_query_projection' AND column_name='pay_amount_cent')=0,
    'ALTER TABLE order_query_projection ADD COLUMN pay_amount_cent BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE projection_upgrade_stmt FROM @projection_upgrade;
EXECUTE projection_upgrade_stmt;
DEALLOCATE PREPARE projection_upgrade_stmt;

SET @projection_upgrade = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema='order_query' AND table_name='order_query_projection' AND column_name='item_count')=0,
    'ALTER TABLE order_query_projection ADD COLUMN item_count INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE projection_upgrade_stmt FROM @projection_upgrade;
EXECUTE projection_upgrade_stmt;
DEALLOCATE PREPARE projection_upgrade_stmt;

SET @projection_upgrade = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema='order_query' AND table_name='order_query_projection' AND column_name='sku_count')=0,
    'ALTER TABLE order_query_projection ADD COLUMN sku_count INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE projection_upgrade_stmt FROM @projection_upgrade;
EXECUTE projection_upgrade_stmt;
DEALLOCATE PREPARE projection_upgrade_stmt;

SET @projection_upgrade = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema='order_query' AND table_name='order_query_projection' AND column_name='first_item_name')=0,
    'ALTER TABLE order_query_projection ADD COLUMN first_item_name VARCHAR(128)', 'SELECT 1');
PREPARE projection_upgrade_stmt FROM @projection_upgrade;
EXECUTE projection_upgrade_stmt;
DEALLOCATE PREPARE projection_upgrade_stmt;

SET @projection_upgrade = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema='order_query' AND table_name='order_query_projection' AND column_name='pay_time')=0,
    'ALTER TABLE order_query_projection ADD COLUMN pay_time DATETIME(3)', 'SELECT 1');
PREPARE projection_upgrade_stmt FROM @projection_upgrade;
EXECUTE projection_upgrade_stmt;
DEALLOCATE PREPARE projection_upgrade_stmt;

SET @projection_upgrade = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema='order_query' AND table_name='order_query_projection' AND column_name='cancel_time')=0,
    'ALTER TABLE order_query_projection ADD COLUMN cancel_time DATETIME(3)', 'SELECT 1');
PREPARE projection_upgrade_stmt FROM @projection_upgrade;
EXECUTE projection_upgrade_stmt;
DEALLOCATE PREPARE projection_upgrade_stmt;
