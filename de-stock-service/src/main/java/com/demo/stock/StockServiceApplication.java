package com.demo.stock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 库存服务启动类
 *
 * <p>演示要点：本服务会被启动【两个实例】（如 8083 / 8183），注册到 Nacos 后
 * 由 order-service 通过 Feign 负载均衡调用，两个实例共享同一个 MySQL 库存库。</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class StockServiceApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(StockServiceApplication.class, args);
        String port = context.getEnvironment().getProperty("local.server.port", "?");
        System.out.println("======================================================================");
        System.out.println("  de-stock-service 启动成功，实例端口: " + port);
        System.out.println("  Nacos 控制台: http://127.0.0.1:8848/nacos");
        System.out.println("======================================================================");
    }
}
