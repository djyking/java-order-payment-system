USE order_inventory;
CREATE TABLE IF NOT EXISTS inventory_admin_audit (
    id BIGINT PRIMARY KEY,
    sku_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    before_total INT NOT NULL,
    after_total INT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_inventory_admin_sku_time (sku_id,create_time)
) ENGINE=InnoDB;
