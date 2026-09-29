package com.demo.order.feign;

import com.demo.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 库存服务 Feign 客户端
 *
 * <p>声明式远程调用：通过服务名 de-stock-service 从 Nacos 获取实例列表，
 * 由 Spring Cloud LoadBalancer 做客户端负载均衡（默认轮询）。
 * 当库存服务部署了两个实例（8083 / 8183）时，请求会交替分发到两个实例，
 * 这正是触发「本地锁失效」的前提条件。</p>
 */
@FeignClient(name = "de-stock-service")
public interface StockFeignClient {

    /**
     * 查询库存
     */
    @GetMapping("/stock/{productId}")
    Result<Integer> getStock(@PathVariable("productId") Long productId);

    /**
     * 扣减库存
     *
     * @return data=true 扣减成功；data=false 库存不足
     */
    @PostMapping("/stock/decrease")
    Result<Boolean> decrease(@RequestParam("productId") Long productId,
                             @RequestParam("count") Integer count);
}
