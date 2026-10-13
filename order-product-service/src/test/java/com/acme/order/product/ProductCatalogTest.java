package com.acme.order.product;

import java.time.Duration;
import com.acme.order.common.security.JwtService;
import com.acme.order.common.web.GlobalExceptionHandler;
import com.acme.order.common.web.ServiceAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductCatalogTest {
    private ProductRepository repository;
    private MockMvc mvc;
    private final String secret = "catalog-test-jwt-secret-more-than-32-bytes";
    private final JwtService jwt = new JwtService(secret, Duration.ofMinutes(10));
    @BeforeEach void setup() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE product_spu(id BIGINT PRIMARY KEY,name VARCHAR(128),status INT)");
        jdbc.execute("CREATE TABLE product_sku(id BIGINT PRIMARY KEY,spu_id BIGINT,sku_name VARCHAR(128),price_cent BIGINT,status INT)");
        repository = new ProductRepository(jdbc);
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(repository))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new ServiceAuthenticationFilter(secret, "catalog-test-internal-token-at-least-32-bytes", new ObjectMapper())).build();
    }
    @Test void publicCatalogOnlyListsOnShelfAndSupportsLiteralSearchAndPaging() throws Exception {
        var visible = repository.create("咨询服务 100%", "标准", 19900, true);
        repository.create("测试草稿", "标准", 9900, false);
        repository.create("另一个服务", "扩展", 29900, true);
        assertEquals(2, repository.list(0, 20, "", false).size());
        assertEquals(3, repository.list(0, 20, "", true).size());
        assertEquals(1, repository.list(0, 20, "%", false).size());
        assertEquals(1, repository.list(1, 1, "", false).size());
        mvc.perform(get("/api/products").param("keyword", "100%"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].skuId").value(Long.toString(visible.skuId())))
            .andExpect(jsonPath("$.data[0].priceCent").value(19900));
    }
    @Test void onlyAdminCanCreateAndUpdateProductsAndOffShelfSkuCannotBeOrdered() throws Exception {
        String body = "{\"name\":\"课程\",\"skuName\":\"标准版\",\"priceCent\":9900,\"onShelf\":true}";
        mvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(body)
            .header("Authorization", "Bearer " + jwt.issue(9, "USER")))
            .andExpect(status().isForbidden());
        var result = mvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(body)
            .header("Authorization", "Bearer " + jwt.issue(10, "ADMIN"))).andExpect(status().isOk()).andReturn();
        var node = new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(node.get("skuId").isTextual());
        String id = node.get("skuId").asText();
        mvc.perform(put("/api/admin/products/" + id).contentType(MediaType.APPLICATION_JSON).content(body.replace("true", "false"))
            .header("Authorization", "Bearer " + jwt.issue(10, "ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/skus/" + id)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SKU_OFF_SHELF"));
    }
    @Test void invalidPricesAndPaginationDoNotWriteOrLeakInternalErrors() throws Exception {
        mvc.perform(post("/api/admin/products").header("Authorization", "Bearer " + jwt.issue(10, "ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"课程\",\"skuName\":\"标准版\",\"priceCent\":0,\"onShelf\":true}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("size", "101")).andExpect(status().isBadRequest());
        assertTrue(repository.list(0, 20, "", true).isEmpty());
    }
}
