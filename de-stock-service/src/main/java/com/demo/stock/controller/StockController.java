package com.demo.stock.controller;

import com.demo.common.result.Result;
import com.demo.stock.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存服务接口
 */
@RestController
@RequestMapping("/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * 查询库存
     * GET /stock/1
     */
    @GetMapping("/{productId}")
    public Result<Integer> getStock(@PathVariable("productId") Long productId) {
        return Result.success(stockService.getStock(productId));
    }

    /**
     * 扣减库存（order-service 通过 Feign 调用此接口）
     * POST /stock/decrease?productId=1&count=1
     *
     * @return data=true 扣减成功；data=false 库存不足
     */
    @PostMapping("/decrease")
    public Result<Boolean> decrease(@RequestParam("productId") Long productId,
                                    @RequestParam(value = "count", defaultValue = "1") Integer count) {
        boolean ok = stockService.decrease(productId, count);
        return Result.success(ok);
    }

    /**
     * 重置库存（演示用）
     * POST /stock/reset?productId=1&stock=100
     */
    @PostMapping("/reset")
    public Result<Integer> reset(@RequestParam(value = "productId", defaultValue = "1") Long productId,
                                 @RequestParam(value = "stock", defaultValue = "100") Integer stock) {
        return Result.success(stockService.reset(productId, stock));
    }
}
