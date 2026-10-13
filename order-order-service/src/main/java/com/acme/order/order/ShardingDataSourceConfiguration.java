package com.acme.order.order;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.algorithm.core.config.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableReferenceRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * 订单服务的 2 库 × 8 表 ShardingSphere-JDBC 运行配置。
 *
 * @author heyu
 * @since 2026-09-02
 */
@Configuration
@Profile("sharding")
public class ShardingDataSourceConfiguration {

    @Bean(name = "orderShard0DataSource", destroyMethod = "close")
    HikariDataSource orderShard0DataSource(@Value("${order.sharding.ds0-url}") String url,
        @Value("${order.sharding.username}") String username,
        @Value("${order.sharding.password}") String password) {
        return hikari(url, username, password, "order-shard-0");
    }

    @Bean(name = "orderShard1DataSource", destroyMethod = "close")
    HikariDataSource orderShard1DataSource(@Value("${order.sharding.ds1-url}") String url,
        @Value("${order.sharding.username}") String username,
        @Value("${order.sharding.password}") String password) {
        return hikari(url, username, password, "order-shard-1");
    }

    @Bean(destroyMethod = "close")
    @Primary
    DataSource dataSource(@Qualifier("orderShard0DataSource") DataSource ds0,
        @Qualifier("orderShard1DataSource") DataSource ds1,
        @Value("${SHARDING_SQL_SHOW:false}") boolean sqlShow) throws SQLException {
        Map<String, DataSource> dataSources = new LinkedHashMap<>();
        dataSources.put("ds0", ds0);
        dataSources.put("ds1", ds1);

        ShardingRuleConfiguration sharding = new ShardingRuleConfiguration();
        sharding.getTables().add(table("trade_order", "trade-order-inline"));
        sharding.getTables().add(table("trade_order_item", "trade-order-item-inline"));
        sharding.getTables().add(table("trade_order_status_log", "trade-order-status-log-inline"));
        sharding.getTables().add(databaseShardedTable("outbox_event"));
        sharding.getTables().add(databaseShardedTable("mq_consume_log"));
        sharding.getTables().add(databaseShardedTable("order_route"));
        sharding.getTables().add(databaseShardedTable("reconciliation_exception"));
        sharding.getBindingTableGroups().add(new ShardingTableReferenceRuleConfiguration(
            "order-tables", "trade_order,trade_order_item,trade_order_status_log"));
        sharding.setDefaultDatabaseShardingStrategy(
            new StandardShardingStrategyConfiguration("user_id", "database-inline"));
        sharding.getShardingAlgorithms().put("database-inline",
            new AlgorithmConfiguration("INLINE", expression("ds${user_id % 2}")));
        sharding.getShardingAlgorithms().put("trade-order-inline",
            new AlgorithmConfiguration("INLINE", expression("trade_order_${order_id % 8}")));
        sharding.getShardingAlgorithms().put("trade-order-item-inline",
            new AlgorithmConfiguration("INLINE", expression("trade_order_item_${order_id % 8}")));
        sharding.getShardingAlgorithms().put("trade-order-status-log-inline",
            new AlgorithmConfiguration("INLINE", expression("trade_order_status_log_${order_id % 8}")));

        Properties props = new Properties();
        props.setProperty("sql-show", Boolean.toString(sqlShow));
        Collection<RuleConfiguration> rules = List.of(sharding);
        return ShardingSphereDataSourceFactory.createDataSource(dataSources, rules, props);
    }

    private ShardingTableRuleConfiguration table(String table, String algorithmName) {
        ShardingTableRuleConfiguration result = new ShardingTableRuleConfiguration(table,
            "ds${0..1}." + table + "_${0..7}");
        result.setTableShardingStrategy(new StandardShardingStrategyConfiguration("order_id", algorithmName));
        return result;
    }

    private ShardingTableRuleConfiguration databaseShardedTable(String table) {
        return new ShardingTableRuleConfiguration(table, "ds${0..1}." + table);
    }

    private Properties expression(String expression) {
        Properties result = new Properties();
        result.setProperty("algorithm-expression", expression);
        return result;
    }

    private HikariDataSource hikari(String url, String username, String password, String poolName) {
        HikariDataSource result = new HikariDataSource();
        result.setDriverClassName("com.mysql.cj.jdbc.Driver");
        result.setJdbcUrl(url);
        result.setUsername(username);
        result.setPassword(password);
        result.setPoolName(poolName);
        result.setMaximumPoolSize(10);
        result.setMinimumIdle(1);
        return result;
    }
}
