package com.demo.stock.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 库存表数据访问层
 */
@Repository
public class StockDao {

    private final JdbcTemplate jdbcTemplate;

    public StockDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询当前库存
     */
    public Integer getStock(Long productId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM t_stock WHERE id = ?", Integer.class, productId);
    }

    /**
     * 查询商品名称
     */
    public String getProductName(Long productId) {
        return jdbcTemplate.queryForObject("SELECT product_name FROM t_stock WHERE id = ?", String.class, productId);
    }

    /**
     * 把库存【覆盖写】为指定值
     *
     * <p>注意：这里写入的是调用方计算好的新值（读到的库存 - 扣减数），
     * 而不是 SQL 层的原子自减（stock = stock - N），这为并发丢失更新留下了空间。</p>
     */
    public int updateStock(Long productId, int newStock) {
        return jdbcTemplate.update("UPDATE t_stock SET stock = ? WHERE id = ?", newStock, productId);
    }

    /**
     * 重置库存（仅用于演示时反复复现）
     */
    public int resetStock(Long productId, int stock) {
        return jdbcTemplate.update("UPDATE t_stock SET stock = ? WHERE id = ?", stock, productId);
    }
}
