-- Safe to re-run for existing installations. Plaintext seed credentials are retired.
USE order_user;
UPDATE sys_user SET status=0,password_hash='{disabled}' WHERE password_hash LIKE '{noop}%';

-- Product administration uses the existing product_spu/product_sku schema.
-- No fixed administrator or password is seeded; configure BOOTSTRAP_ADMIN_* on first start.
