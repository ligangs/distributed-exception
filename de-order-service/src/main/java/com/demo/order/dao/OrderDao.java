package com.demo.order.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 订单表数据访问层
 */
@Repository
public class OrderDao {

    private final JdbcTemplate jdbcTemplate;

    public OrderDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入一条成功订单
     */
    public int insertOrder(String orderNo, Long productId, int count) {
        return jdbcTemplate.update(
                "INSERT INTO t_order(order_no, product_id, buy_count, status) VALUES (?, ?, ?, 1)",
                orderNo, productId, count);
    }

    /**
     * 统计成功订单数（用于压测后与库存核对）
     */
    public int countSuccessOrders() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_order WHERE status = 1", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 清空订单（演示用）
     */
    public int clear() {
        return jdbcTemplate.update("DELETE FROM t_order");
    }
}
