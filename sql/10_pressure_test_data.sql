-- 普通订单压测专用商品与库存。与业务演示 SKU 隔离，避免压测耗尽演示数据。
-- @author heyu
-- @since 2026-09-02

INSERT INTO order_product.product_spu(id,name,status,create_time,update_time)
VALUES(1999,'Pressure Test Product',1,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE name=VALUES(name),status=VALUES(status),update_time=NOW(3);

INSERT INTO order_product.product_sku(id,spu_id,sku_name,price_cent,status,create_time,update_time)
VALUES(19999,1999,'Pressure Test SKU',100,1,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE spu_id=VALUES(spu_id),sku_name=VALUES(sku_name),price_cent=VALUES(price_cent),
                        status=VALUES(status),update_time=NOW(3);

INSERT INTO order_inventory.inventory_stock(
    sku_id,total_stock,available_stock,reserved_stock,sold_stock,version,create_time,update_time)
VALUES(19999,100000,100000,0,0,0,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE update_time=NOW(3);
