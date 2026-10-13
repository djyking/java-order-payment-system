package com.acme.order.api.product;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 商品服务接口使用的数据传输对象集合。
 *
 * @author heyu
 * @since 2026-07-15
 */
public final class ProductDtos {

    private ProductDtos() {
    }

    /**
     * SKU 商品视图。
     *
     * @param skuId SKU 标识
     * @param spuId SPU 标识
     * @param spuName SPU 名称
     * @param skuName SKU 名称
     * @param priceCent 价格，单位为分
     * @param onShelf 是否上架
     * @author heyu
     * @since 2026-07-15
     */
    public record SkuView(@JsonFormat(shape = JsonFormat.Shape.STRING) long skuId, @JsonFormat(shape = JsonFormat.Shape.STRING) long spuId, String spuName, String skuName, long priceCent, boolean onShelf) {
    }
}
