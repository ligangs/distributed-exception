-- =====================================================================
-- 分布式超卖演示 - 数据库初始化脚本
-- 由 docker-compose 中 MySQL 容器首次初始化时自动执行
-- =====================================================================

-- ---------------------------------------------------------------------
-- 库存库（de-stock-service 使用）
-- 关键点：两个库存服务实例（8083 / 8183）共享这同一个数据库！
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `de_stock` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `de_stock`;

DROP TABLE IF EXISTS `t_stock`;
CREATE TABLE `t_stock`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `product_name` VARCHAR(64)  NOT NULL COMMENT '商品名称',
    `stock`        INT          NOT NULL DEFAULT 0 COMMENT '剩余库存',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='商品库存表';

-- 初始库存 100 件
INSERT INTO `t_stock` (`id`, `product_name`, `stock`)
VALUES (1, 'iPhone 17 Pro Max 1TB', 100);

-- ---------------------------------------------------------------------
-- 订单库（de-order-service 使用）
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `de_order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `de_order`;

DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`    VARCHAR(64) NOT NULL COMMENT '订单号',
    `product_id`  BIGINT      NOT NULL COMMENT '商品ID',
    `buy_count`   INT         NOT NULL DEFAULT 1 COMMENT '购买数量',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '订单状态 1=创建成功',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单表';
