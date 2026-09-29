package com.demo.order.service;

import com.demo.common.exception.BizException;
import com.demo.common.result.Result;
import com.demo.order.feign.StockFeignClient;
import com.demo.order.model.PressureTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发压测器：一键触发超卖复现
 *
 * <p>原理：用 CountDownLatch 实现「发令枪」效果——</p>
 * <ol>
 *   <li>建立 N 个线程，全部就绪后（readyLatch）等待发令；</li>
 *   <li>发令后所有线程在同一瞬间并发下单（startLatch），最大化请求的重叠窗口；</li>
 *   <li>每个线程循环下单 M 次，总请求数 = N × M；</li>
 *   <li>等全部完成后统计：成功数 / 库存不足数 / 异常数，并核对库存账实。</li>
 * </ol>
 */
@Service
public class PressureTestService {

    private static final Logger log = LoggerFactory.getLogger(PressureTestService.class);

    /** 压测最长等待时间（秒），超时则记录未完成数返回 */
    private static final long MAX_WAIT_SECONDS = 180L;

    private final OrderService orderService;
    private final StockFeignClient stockFeignClient;

    public PressureTestService(OrderService orderService, StockFeignClient stockFeignClient) {
        this.orderService = orderService;
        this.stockFeignClient = stockFeignClient;
    }

    /**
     * 执行并发压测
     *
     * @param productId      商品ID
     * @param threads        并发线程数
     * @param timesPerThread 每线程下单次数
     */
    public PressureTestResult run(Long productId, int threads, int timesPerThread) {
        if (threads <= 0 || threads > 1000) {
            throw new BizException("threads 取值范围为 1~1000");
        }
        if (timesPerThread <= 0 || timesPerThread > 1000) {
            throw new BizException("times 取值范围为 1~1000");
        }

        int totalRequests = threads * timesPerThread;

        // 压测前库存 = 本次可售总量
        int initStock = queryStock(productId);
        log.info("========== 压测开始: productId={}, 初始库存={}, 并发线程={}, 订单总数={} ==========",
                productId, initStock, threads, totalRequests);

        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger noStockCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread t = new Thread(runnable);
            t.setName("pressure-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                readyLatch.countDown();
                try {
                    // 等待发令枪
                    startLatch.await();
                    for (int j = 0; j < timesPerThread; j++) {
                        try {
                            if (orderService.buy(productId, 1)) {
                                successCount.incrementAndGet();
                            } else {
                                noStockCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            log.warn("下单异常: {}", e.getMessage());
                        } finally {
                            doneLatch.countDown();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        long costMs;
        int unfinishedCount = 0;
        try {
            // 等所有线程就绪
            readyLatch.await();
            long startTime = System.currentTimeMillis();
            // 发令！
            startLatch.countDown();
            // 等所有请求完成
            boolean finished = doneLatch.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
            costMs = System.currentTimeMillis() - startTime;
            if (!finished) {
                unfinishedCount = (int) doneLatch.getCount();
                log.warn("压测等待超时({}s)，仍有 {} 个请求未完成", MAX_WAIT_SECONDS, unfinishedCount);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("压测被中断");
        } finally {
            pool.shutdownNow();
        }

        // 压测后库存
        int remainStock = queryStock(productId);
        int soldCount = initStock - remainStock;
        int success = successCount.get();
        int lostUpdateCount = success - soldCount;
        boolean overSold = success > initStock;
        int overSellCount = Math.max(0, success - initStock);

        PressureTestResult result = new PressureTestResult();
        result.setProductId(productId);
        result.setInitStock(initStock);
        result.setThreads(threads);
        result.setTimesPerThread(timesPerThread);
        result.setTotalRequests(totalRequests);
        result.setSuccessCount(success);
        result.setNoStockCount(noStockCount.get());
        result.setErrorCount(errorCount.get());
        result.setUnfinishedCount(unfinishedCount);
        result.setRemainStock(remainStock);
        result.setSoldCount(soldCount);
        result.setLostUpdateCount(lostUpdateCount);
        result.setOverSold(overSold);
        result.setOverSellCount(overSellCount);
        result.setCostMs(costMs);
        result.setConclusion(buildConclusion(result));

        log.info("========== 压测结束: {}", result.getConclusion());
        return result;
    }

    /**
     * 生成结论文案
     */
    private String buildConclusion(PressureTestResult r) {
        if (r.isOverSold()) {
            return String.format("【超卖复现成功】成功下单 %d 单 > 初始库存 %d 件，共超卖 %d 单；"
                            + "库存实际只扣了 %d 件（丢失更新 %d 次），账实严重不符",
                    r.getSuccessCount(), r.getInitStock(), r.getOverSellCount(),
                    r.getSoldCount(), r.getLostUpdateCount());
        }
        if (r.getLostUpdateCount() > 0) {
            return String.format("【发生丢失更新】成功下单 %d 单，但库存实际扣减 %d 件（丢失 %d 次）；"
                            + "本次未突破初始库存 %d 件，属于临界状态，多压几轮或提高并发即可看到超卖",
                    r.getSuccessCount(), r.getSoldCount(), r.getLostUpdateCount(), r.getInitStock());
        }
        if (r.getSuccessCount() == 0 && r.getErrorCount() > 0) {
            return "【压测异常】所有下单均失败且存在异常，请检查库存服务是否正常注册到 Nacos";
        }
        return String.format("【库存正常】成功下单 %d 单，库存实际扣减 %d 件，账实一致，未发生超卖。"
                        + "（提示：当前若只启动了一个库存实例，本地 synchronized 锁是有效的；"
                        + "再启动第二个库存实例后重新压测即可复现超卖）",
                r.getSuccessCount(), r.getSoldCount());
    }

    /**
     * 查询库存（经由 Feign 走库存服务）
     */
    private int queryStock(Long productId) {
        Result<Integer> result = stockFeignClient.getStock(productId);
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new BizException("查询库存失败，请确认库存服务已启动并注册到 Nacos");
        }
        return result.getData();
    }
}
