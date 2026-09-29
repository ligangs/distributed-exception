package com.demo.order.config;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;

// 给 de-stock-service 单独使用上面的负载均衡配置
@LoadBalancerClient(value = "de-stock-service", configuration = DeStockServiceServiceLbConfig.class)
public class LoadBalancerClientMarker {
}