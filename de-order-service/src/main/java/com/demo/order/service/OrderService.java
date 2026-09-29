package com.demo.order.service;

import com.demo.common.result.Result;
import com.demo.order.dao.OrderDao;
import com.demo.order.feign.StockFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单业务
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final StockFeignClient stockFeignClient;
    private final OrderDao orderDao;

    public OrderService(StockFeignClient stockFeignClient, OrderDao orderDao) {
        this.stockFeignClient = stockFeignClient;
        this.orderDao = orderDao;
    }

    /**
     * 下单：先远程扣减库存，成功后才落订单
     *
     * @return true=下单成功；false=库存不足
     */
    public boolean buy(Long productId, int count) {
        // 1. Feign 远程调用库存服务（经 Nacos + LoadBalancer，可能落到任一 stock 实例）
        Result<Boolean> result = stockFeignClient.decrease(productId, count);

        // 2. 库存不足，下单失败
        if (result == null || !result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
            return false;
        }

        // 3. 扣减成功，落订单
        String orderNo = generateOrderNo();
        orderDao.insertOrder(orderNo, productId, count);
        log.debug("下单成功, orderNo={}, productId={}", orderNo, productId);
        return true;
    }

    /**
     * 生成订单号（时间戳 + 4 位随机数，演示够用）
     */
    private String generateOrderNo() {
        return System.currentTimeMillis() + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /**
     * 统计成功订单数
     */
    public int countSuccessOrders() {
        return orderDao.countSuccessOrders();
    }

    /**
     * 清空订单（演示用）
     */
    public int clearOrders() {
        return orderDao.clear();
    }
}
