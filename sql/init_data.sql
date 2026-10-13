USE order_product; INSERT IGNORE INTO product_spu VALUES(1000,'企业级订单系统演示商品',1,NOW(3),NOW(3)); INSERT IGNORE INTO product_sku VALUES(10001,1000,'标准版',9900,1,NOW(3),NOW(3));
USE order_inventory; INSERT IGNORE INTO inventory_stock VALUES(10001,100,100,0,0,0,NOW(3),NOW(3));
