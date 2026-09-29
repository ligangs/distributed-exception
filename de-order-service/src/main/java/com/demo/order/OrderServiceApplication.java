package com.demo.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 订单服务启动类
 *
 * <p>职责：接收下单请求 → Feign 负载均衡调用 de-stock-service 扣减库存 → 库存扣减成功后落订单。
 * 内置并发压测器（PressureTestService）用于一键触发超卖复现。</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(OrderServiceApplication.class, args);
        String port = context.getEnvironment().getProperty("local.server.port", "?");
        System.out.println("======================================================================");
        System.out.println("  de-order-service 启动成功，实例端口: " + port);
        System.out.println("  并发压测入口: POST http://127.0.0.1:" + port + "/order/pressure-test");
        System.out.println("  Nacos 控制台: http://127.0.0.1:8848/nacos");
        System.out.println("======================================================================");
    }
}
