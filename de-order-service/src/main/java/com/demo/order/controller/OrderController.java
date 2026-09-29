package com.demo.order.controller;

import com.demo.common.result.Result;
import com.demo.order.model.PressureTestResult;
import com.demo.order.service.OrderService;
import com.demo.order.service.PressureTestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单服务接口
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;
    private final PressureTestService pressureTestService;

    public OrderController(OrderService orderService, PressureTestService pressureTestService) {
        this.orderService = orderService;
        this.pressureTestService = pressureTestService;
    }

    /**
     * 单次下单（手动感受链路）
     * POST /order/buy?productId=1&count=1
     *
     * @return data=true 下单成功；data=false 库存不足
     */
    @PostMapping("/buy")
    public Result<Boolean> buy(@RequestParam(value = "productId", defaultValue = "1") Long productId,
                               @RequestParam(value = "count", defaultValue = "1") Integer count) {
        return Result.success(orderService.buy(productId, count));
    }

    /**
     * ★ 并发压测：一键触发超卖复现
     * POST /order/pressure-test?productId=1&threads=100&times=2
     *
     * @param threads 并发线程数（默认 100）
     * @param times   每线程下单次数（默认 2，即总下单 200 次）
     */
    @PostMapping("/pressure-test")
    public Result<PressureTestResult> pressureTest(
            @RequestParam(value = "productId", defaultValue = "1") Long productId,
            @RequestParam(value = "threads", defaultValue = "100") Integer threads,
            @RequestParam(value = "times", defaultValue = "2") Integer times) {
        return Result.success(pressureTestService.run(productId, threads, times));
    }

    /**
     * 查询成功订单数（演示用）
     */
    @GetMapping("/count")
    public Result<Integer> count() {
        return Result.success(orderService.countSuccessOrders());
    }

    /**
     * 清空订单（演示用，配合库存重置反复复现）
     */
    @PostMapping("/clear")
    public Result<Integer> clear() {
        return Result.success(orderService.clearOrders());
    }
}
