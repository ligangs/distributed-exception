package com.demo.stock.service;

import com.demo.common.exception.BizException;
import com.demo.stock.dao.StockDao;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 库存服务核心业务
 *
 * <h3>【问题代码】这里就是分布式超卖的发生地</h3>
 *
 * <p>扣减库存采用经典的「读 - 判 - 改」三步：</p>
 * <pre>
 *   1. SELECT stock FROM t_stock WHERE id = ?      -- 读
 *   2. if (stock < count) return false;            -- 判
 *   3. UPDATE t_stock SET stock = ? WHERE id = ?   -- 改（写回"读到的值 - 扣减数"）
 * </pre>
 *
 * <p>作者用 {@code synchronized} 加锁来保证线程安全，这在【单实例】部署时确实有效。</p>
 *
 * <h3>但分布式部署后会超卖，原因：</h3>
 * <ol>
 *   <li>{@code synchronized} 是 JVM 级别的监视器锁，只能锁住【当前 JVM 内】的线程，
 *       两个实例（8083 / 8183）是两个 JVM，各自锁各自的，锁形同虚设；</li>
 *   <li>两个实例共享同一个 MySQL，在并发窗口内可能先后读到【同一个库存值】，
 *       例如都读到 100；</li>
 *   <li>实例 A 写回 99 提交后，实例 B 也基于旧值写回 99，
 *       把 A 的扣减【覆盖丢失】（MySQL RR 隔离级别下 UPDATE 虽是当前读，
 *       但 B 写回的是自己 SELECT 出来的旧值 100-1，而不是当前值 99-1）；</li>
 *   <li>结果：卖出 2 单，库存只少了 1 件 —— 高并发下持续发生，
 *       最终「成功订单数」远超「初始库存」，即超卖。</li>
 * </ol>
 *
 * <h3>修复方向（本项目只复现问题，不做修复，留作自己动手）：</h3>
 * <ul>
 *   <li>方案一：引入 Redis + Redisson 分布式锁，把「读-判-改」放进跨 JVM 的锁内；</li>
 *   <li>方案二：把扣减改成数据库原子操作，如
 *       {@code UPDATE t_stock SET stock = stock - #{count} WHERE id = #{id} AND stock >= #{count}}，
 *       根据 affected rows 判断是否扣减成功（利用 InnoDB 行锁）；</li>
 *   <li>方案三：乐观锁，库存表加 version 字段，UPDATE 时带上 {@code AND version = #{oldVersion}}。</li>
 * </ul>
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final StockDao stockDao;

    /** 编程式事务：把「读-判-改」包裹在同一个事务中（贴近真实业务写法） */
    private final TransactionTemplate transactionTemplate;

    /**
     * 模拟真实业务处理耗时的【随机上限】（毫秒），默认 1000。
     * 每次扣减在 [0, 上限] 内随机休眠：既拉大「读过库存」到「写回库存」的竞态窗口，
     * 又让两个实例的处理节拍不再同步对齐，使超卖呈"偶发、数量波动"的形态。
     * 可在 application.yml 中调整：demo.stock.fake-business-delay-max-ms
     */
    @Value("${demo.stock.fake-business-delay-max-ms:1000}")
    private long fakeBusinessDelayMaxMs;

    /** 当前实例端口（启动时通过 --server.port 覆盖） */
    @Value("${server.port:?}")
    private String serverPort;

    public StockService(StockDao stockDao, TransactionTemplate transactionTemplate) {
        this.stockDao = stockDao;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 扣减库存
     *
     * <p>注意 synchronized 的范围：它包住了整个事务（含提交），
     * 即单实例内同一时刻只有一个线程在执行「读-判-改-提交」，本地锁是"有效"的。
     * 问题恰恰出在：锁的有效范围只有这【一个 JVM】。</p>
     *
     * @param productId 商品ID
     * @param count     扣减数量
     * @return true=扣减成功；false=库存不足
     */
    public boolean decrease(Long productId, int count) {
        // ★ 本地锁：看似安全，实际只能锁住当前 JVM（分布式下失效的根源）
        synchronized (this) {
            Boolean ok = transactionTemplate.execute(status -> doDecreaseInTransaction(productId, count));
            return Boolean.TRUE.equals(ok);
        }
    }

    /**
     * 事务内的「读 - 判 - 改」
     */
    private boolean doDecreaseInTransaction(Long productId, int count) {
        String instancePort = getLocalPort();

        // ---- 第 1 步：【读】查询当前库存 ----
        Integer stock = stockDao.getStock(productId);
        if (stock == null) {
            throw new BizException("商品不存在, productId=" + productId);
        }

        // ---- 第 2 步：【判】库存不足则直接失败 ----
        if (stock < count) {
            log.info("[{}] 库存不足, productId={}, stock={}, count={}", instancePort, productId, stock, count);
            return false;
        }

        // ---- 第 3 步：模拟业务处理耗时（在 [0, 上限] 内随机，打破多实例节拍同步） ----
        long randomDelayMs = fakeBusinessDelayMaxMs > 0
                ? ThreadLocalRandom.current().nextLong(fakeBusinessDelayMaxMs + 1)
                : 0L;
        sleepQuietly(randomDelayMs);

        // ---- 第 4 步：【改】写回「读到的库存 - 扣减数」----
        // ★ 竞态点：另一个实例若在此期间读到了相同的 stock，
        //   它的写回会覆盖掉本次扣减（丢失更新）→ 超卖
        int newStock = stock - count;
        stockDao.updateStock(productId, newStock);

        log.info("[{}] 扣减库存成功, productId={}, {} -> {} (count={}, delayMs={})",
                instancePort, productId, stock, newStock, count, randomDelayMs);
        return true;
    }

    /**
     * 查询当前库存
     */
    public int getStock(Long productId) {
        Integer stock = stockDao.getStock(productId);
        if (stock == null) {
            throw new BizException("商品不存在, productId=" + productId);
        }
        return stock;
    }

    /**
     * 重置库存（仅用于演示时反复复现）
     */
    public int reset(Long productId, int stock) {
        stockDao.resetStock(productId, stock);
        log.info("[{}] 重置库存, productId={}, stock={}", getLocalPort(), productId, stock);
        return stock;
    }

    /**
     * 读取当前实例端口，用于日志中区分请求落到了哪个实例
     */
    private String getLocalPort() {
        return "port:" + serverPort;
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
