package com.demo.common.result;

/**
 * 统一状态码常量
 */
public final class ResultCode {

    private ResultCode() {
    }

    /** 成功 */
    public static final int SUCCESS = 200;

    /** 业务异常（如：库存不足） */
    public static final int BIZ_ERROR = 501;

    /** 系统异常（如：数据库错误、远程调用失败） */
    public static final int SYSTEM_ERROR = 500;
}
