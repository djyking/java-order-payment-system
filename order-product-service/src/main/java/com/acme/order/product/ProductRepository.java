package com.acme.order.product;

import com.acme.order.api.product.ProductDtos.SkuView;
import com.acme.order.common.core.BizException;
import com.acme.order.common.core.ErrorCode;
import com.acme.order.common.core.Ids;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductRepository {
    private static final String SELECT = "SELECT s.id,s.spu_id,p.name,s.sku_name,s.price_cent,s.status,p.status AS spu_status "
        + "FROM product_sku s JOIN product_spu p ON p.id=s.spu_id ";
    private static final RowMapper<SkuView> MAPPER = (rs, n) -> new SkuView(rs.getLong("id"), rs.getLong("spu_id"),
        rs.getString("name"), rs.getString("sku_name"), rs.getLong("price_cent"),
        rs.getInt("status") == 1 && rs.getInt("spu_status") == 1);
    private final JdbcTemplate jdbc;
    public ProductRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public SkuView sku(long id) {
        return jdbc.query(SELECT + "WHERE s.id=?", MAPPER, id).stream().findFirst()
            .orElseThrow(() -> new BizException(ErrorCode.SKU_NOT_FOUND, "SKU不存在"));
    }
    public List<SkuView> list(int page, int size, String keyword, boolean includeOffShelf) {
        if (page < 0 || page > 100_000 || size < 1 || size > 100 || keyword.length() > 128) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "分页或检索参数无效");
        }
        // Use LOCATE so user-supplied % and _ are ordinary characters, not wildcards.
        return jdbc.query(SELECT + "WHERE (LOCATE(?,p.name)>0 OR LOCATE(?,s.sku_name)>0) "
                + (includeOffShelf ? "" : "AND s.status=1 AND p.status=1 ")
                + "ORDER BY s.id DESC LIMIT ? OFFSET ?", MAPPER, keyword, keyword, size, (long) page * size);
    }
    @Transactional
    public SkuView create(String name, String skuName, long priceCent, boolean onShelf) {
        long spuId = Ids.next();
        long skuId = Ids.next();
        jdbc.update("INSERT INTO product_spu(id,name,status) VALUES(?,?,1)", spuId, name.trim());
        jdbc.update("INSERT INTO product_sku(id,spu_id,sku_name,price_cent,status) VALUES(?,?,?,?,?)",
            skuId, spuId, skuName.trim(), priceCent, onShelf ? 1 : 0);
        return sku(skuId);
    }
    @Transactional
    public SkuView update(long id, String name, String skuName, long priceCent, boolean onShelf) {
        var current = sku(id);
        jdbc.update("UPDATE product_spu SET name=? WHERE id=?", name.trim(), current.spuId());
        jdbc.update("UPDATE product_sku SET sku_name=?,price_cent=?,status=? WHERE id=?",
            skuName.trim(), priceCent, onShelf ? 1 : 0, id);
        return sku(id);
    }
}
