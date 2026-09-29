package com.demo.order.model;

/**
 * 并发压测结果
 *
 * <p>核心判定指标：</p>
 * <ul>
 *   <li>successCount > initStock  → 【超卖】：卖出的订单比库存还多</li>
 *   <li>lostUpdateCount > 0       → 【丢失更新】：成功下单数 > 库存实际扣减量，
 *       说明存在「卖出 2 单、库存只减 1 件」的情况（超卖的直接成因）</li>
 * </ul>
 */
public class PressureTestResult {

    /** 商品ID */
    private Long productId;

    /** 压测前库存（本次压测可售总量） */
    private int initStock;

    /** 并发线程数 */
    private int threads;

    /** 每线程下单次数 */
    private int timesPerThread;

    /** 总下单次数 */
    private int totalRequests;

    /** 下单成功次数 */
    private int successCount;

    /** 库存不足次数 */
    private int noStockCount;

    /** 异常次数（网络/系统错误） */
    private int errorCount;

    /** 超时未完成的请求数（正常为 0） */
    private int unfinishedCount;

    /** 压测后剩余库存 */
    private int remainStock;

    /** 库存实际扣减量 = 初始库存 - 剩余库存 */
    private int soldCount;

    /** 丢失更新次数 = 成功下单数 - 库存实际扣减量（>0 即为账实不符） */
    private int lostUpdateCount;

    /** 是否超卖（成功下单数 > 初始库存） */
    private boolean overSold;

    /** 超卖单数 = 成功下单数 - 初始库存 */
    private int overSellCount;

    /** 压测耗时(ms) */
    private long costMs;

    /** 结论 */
    private String conclusion;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getInitStock() {
        return initStock;
    }

    public void setInitStock(int initStock) {
        this.initStock = initStock;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getTimesPerThread() {
        return timesPerThread;
    }

    public void setTimesPerThread(int timesPerThread) {
        this.timesPerThread = timesPerThread;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getNoStockCount() {
        return noStockCount;
    }

    public void setNoStockCount(int noStockCount) {
        this.noStockCount = noStockCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public int getUnfinishedCount() {
        return unfinishedCount;
    }

    public void setUnfinishedCount(int unfinishedCount) {
        this.unfinishedCount = unfinishedCount;
    }

    public int getRemainStock() {
        return remainStock;
    }

    public void setRemainStock(int remainStock) {
        this.remainStock = remainStock;
    }

    public int getSoldCount() {
        return soldCount;
    }

    public void setSoldCount(int soldCount) {
        this.soldCount = soldCount;
    }

    public int getLostUpdateCount() {
        return lostUpdateCount;
    }

    public void setLostUpdateCount(int lostUpdateCount) {
        this.lostUpdateCount = lostUpdateCount;
    }

    public boolean isOverSold() {
        return overSold;
    }

    public void setOverSold(boolean overSold) {
        this.overSold = overSold;
    }

    public int getOverSellCount() {
        return overSellCount;
    }

    public void setOverSellCount(int overSellCount) {
        this.overSellCount = overSellCount;
    }

    public long getCostMs() {
        return costMs;
    }

    public void setCostMs(long costMs) {
        this.costMs = costMs;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }
}
