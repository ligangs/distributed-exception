# distributed-exception

基于 **Spring Cloud Alibaba** 构建的分布式系统演示工程：复现 **分布式线程安全问题 —— 库存超卖**。

> 本项目只用来「复现问题」，不提供修复代码；修复思路见文末，留作自己动手练习。

---

## 1. 问题背景

扣减库存的经典写法是「读 - 判 - 改」三步：

```java
synchronized (this) {                     // 本地锁：看似安全，实际只能锁住当前 JVM
    Integer stock = query("SELECT stock FROM t_stock WHERE id = ?");    // 1. 读
    if (stock < count) return false;                                    // 2. 判
    update("UPDATE t_stock SET stock = ? WHERE id = ?", stock - count); // 3. 改（写回旧值-1）
}
```

- **单实例部署**：`synchronized` 生效，同一时刻只有一个线程执行「读-判-改」，库存扣减正确；
- **多实例部署**：两个实例是两个 JVM，各自锁各自的，锁形同虚设。两个实例在竞态窗口内读到
  同一个库存值，各自写回「读到值 - 1」，后写的一方把先写的一方的扣减**覆盖丢失**（丢失更新）。
  高并发下持续发生，最终**成功订单数远超初始库存** —— 即超卖。

## 2. 系统架构

```
                         POST /order/pressure-test
                         （发令枪式并发 200 次下单）
                                   │
                                   ▼
                     ┌──────────────────────────┐
                     │    de-order-service      │
                     │         :8082            │
                     │  ┌────────────────────┐  │
                     │  │ PressureTestService│  │  CountDownLatch 发令枪
                     │  └─────────┬──────────┘  │
                     └────────────┼─────────────┘
                                  │ Feign + Spring Cloud LoadBalancer
                                  │ （经 Nacos 服务发现，轮询分发）
                  ┌───────────────┴───────────────┐
                  ▼                               ▼
      ┌──────────────────────┐        ┌──────────────────────┐
      │ de-stock-service A   │        │ de-stock-service B   │
      │       :8083          │        │       :8183          │
      │  synchronized 本地锁  │        │  synchronized 本地锁  │  ← 两个 JVM，锁互相不认识
      └──────────┬───────────┘        └───────────┬──────────┘
                 │      SELECT / UPDATE stock     │
                 └───────────────┬────────────────┘
                                 ▼
                   ┌────────────────────────────┐
                   │     MySQL（共享、核心）        │
                   │  de_stock.t_stock          │
                   │  de_order.t_order          │
                   └────────────────────────────┘
```

## 3. 模块与版本

| 模块 | 端口 | 说明 |
|---|---|---|
| `de-common` | - | 公共返回体 `Result` / 错误码 / 业务异常 |
| `de-order-service` | 8082 | 订单服务：下单 + 内置并发压测器（`/order/pressure-test`） |
| `de-stock-service` | 8083 / 8183 | 库存服务：**核心问题代码**（`StockService`），演示时启动两个实例 |
| `docker-compose.yml` | - | 一键启动 Nacos(8848) + MySQL(3306) + Redis(6379) |

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 1.8 | 必须 |
| Spring Boot | 2.6.13 | SCA 2021.0.6.0 严格对应版本 |
| Spring Cloud | 2021.0.5 | 同上 |
| Spring Cloud Alibaba | 2021.0.6.0 | Nacos Client 2.2.0 |
| Nacos Server | v2.3.2 | Docker 单机模式（2.x 客户端协议兼容；v2.2.3 镜像内 JDK 在新内核 cgroup v2 下启动会 NPE，故用 2.3.2） |
| MySQL | 8.0 | Docker，首次启动自动执行 `docker/mysql/init/01-init.sql` |

## 4. 快速开始

### 4.1 启动中间件（Docker）

```powershell
cd C:\workspace\distributed-exception
docker compose up -d
```

> 如果 Docker Hub 拉取超时，可先用国内源拉取再打标签（示例用 DaoCloud 源）：
> ```powershell
> docker pull docker.m.daocloud.io/library/mysql:8.0
> docker tag  docker.m.daocloud.io/library/mysql:8.0 mysql:8.0
> docker pull docker.m.daocloud.io/nacos/nacos-server:v2.3.2
> docker tag  docker.m.daocloud.io/nacos/nacos-server:v2.3.2 nacos/nacos-server:v2.3.2
> docker pull docker.m.daocloud.io/library/redis:7.2-alpine
> docker tag  docker.m.daocloud.io/library/redis:7.2-alpine redis:7.2-alpine
> docker compose up -d
> ```

验证：
- MySQL 初始化成功：`docker exec de-mysql mysql -uroot -proot123456 -e "select * from de_stock.t_stock"`
- Nacos 控制台：http://127.0.0.1:8848/nacos （用户名/密码：nacos/nacos）

### 4.2 构建

```powershell
cd C:\workspace\distributed-exception
mvn clean package -DskipTests
```

### 4.3 启动服务

**实例 A（基础环境）**

- 启动 `StockServiceApplication`（默认 8083）
- 启动 `OrderServiceApplication`（默认 8082）

**实例 B（复现超卖的关键）**

再启动一个 `StockServiceApplication`，用启动参数覆盖端口：

- IDEA：Run/Debug Configurations 复制一份，`Program arguments` 填 `--server.port=8183`
- 命令行：
  ```powershell
  java -jar de-stock-service\target\de-stock-service-1.0.0.jar --server.port=8183
  ```

启动后在 Nacos 控制台 → 服务列表 → `de-stock-service` 应看到 **2 个实例**（8083、8183）。

## 5. 复现实验

### 实验一：单实例（本地锁有效，不超卖）

只启动 `de-stock-service` 的 8083 实例，执行：

```powershell
# 1. 重置库存为 100、清空订单
curl.exe -X POST "http://127.0.0.1:8083/stock/reset?productId=1&stock=100"
curl.exe -X POST "http://127.0.0.1:8082/order/clear"

# 2. 并发压测：100 线程 × 2 次 = 200 次下单，抢 100 件库存
curl.exe -X POST "http://127.0.0.1:8082/order/pressure-test?productId=1&threads=100&times=2"
```

预期结果（结论字段）：

> **【库存正常】** 成功下单 100 单，库存实际扣减 100 件，账实一致，未发生超卖。

### 实验二：双实例（本地锁失效，超卖复现）

再启动 8183 实例（Nacos 中确认 `de-stock-service` 有 2 个健康实例），重复上述压测步骤。

预期结果（结论字段）：

> **【超卖复现成功】** 成功下单 1xx 单 > 初始库存 100 件，共超卖 xx 单；库存实际只扣了 xx 件（丢失更新 xx 次），账实严重不符

同时可以核对数据库（订单数 > 实际库存消耗）：

```sql
SELECT (SELECT COUNT(*) FROM de_order.t_order WHERE status = 1) AS 成功订单数,
       (SELECT stock FROM de_stock.t_stock WHERE id = 1)        AS 剩余库存;
```

> 提示：库存服务每次扣减会随机休眠 `[0, 1000]ms`（`demo.stock.fake-business-delay-max-ms` 可调）
> 模拟真实业务耗时，既放大竞态窗口又打破两侧节拍——实测表明它并不降低丢失率（见第 6 节实测表）。

### 实测结果对照（本机复现记录）

同一台机器、同样的压测参数（100 线程 × 2 次 = 200 次下单，初始库存 100），
唯一变量是库存服务实例数：

| 指标 | 单实例（仅 8083） | 双实例（8083 + 8183） |
|---|---|---|
| 成功下单 | 100 单 | **200 单** |
| 库存不足失败 | 100 单 | 0 单 |
| 库存实际扣减 | 100 件 | 100 件 |
| 丢失更新次数 | 0 | **100 次** |
| 是否超卖 | 否 | **是，超卖 100 单** |
| 结论 | ✅ 库存正常 | ❌ 账实严重不符 |

- 单实例结论：`【库存正常】成功下单 100 单，库存实际扣减 100 件，账实一致，未发生超卖`
- 双实例结论：`【超卖复现成功】成功下单 200 单 > 初始库存 100 件，共超卖 100 单；库存实际只扣了 100 件（丢失更新 100 次），账实严重不符`
- 数据库核对（双实例压测后）：`t_order` 成功订单 **200** 条，`t_stock` 剩余库存 **0**，
  即卖出 200 件、实际只有 100 件库存

## 6. 超卖原理（时序图）

```
时刻   实例A(:8083)             实例B(:8183)               MySQL
 t0    读 stock=100                                      stock=100
 t1                             读 stock=100             ← 两个 JVM 读到同一个值
 t2    判 100>=1 ✔
 t3                             判 100>=1 ✔             ← 两个请求都判定"库存充足"
 t4    写 stock=99 ───────────────────────────────▶      stock=99   （A 扣掉 1 件）
 t5                             写 stock=99 ───────▶      stock=99   （B 用旧值覆盖！）
        ───────────────────────────────────────────────────────────
        结果：卖出 2 单，库存只减 1 件 → 丢失一次更新 → 超卖
```

三个关键点：
1. `synchronized` 是 **JVM 级** 监视器锁，跨不了进程；
2. 两个实例共享同一个 MySQL，存在「同时读到同一个值」的时间窗口——
   代码里每次扣减会随机 `Thread.sleep([0, 1000]ms)` 模拟真实业务耗时（风控、算价等），
   把这个窗口放大，让问题可以稳定复现；
3. UPDATE 写回的是 **Java 侧算好的旧值**（`stock - count`），不是数据库原子自减
   （`stock = stock - 1`），所以后写者会**覆盖**前写者的结果。

### 为什么每次超卖量都"恰好"等于库存数？（不是巧合）

压测期间两侧实例都**背靠背连续处理**（处理完一单立即读下一单），各自的「读 → 写」窗口
首尾相连、铺满时间轴；**对方持续发来的"读"必然落进己方某个窗口内部** → 读到同一个库存值 →
双方各自写回「同一个新值」，后写覆盖先写 → **每 2 单就丢 1 件**。

这个结论**与两侧节拍是否同步无关**：把业务耗时从固定 20ms 改成 `[0, 1000]ms` 随机延迟后
（实测 4 轮），两侧读值序列依然逐单重合（100→99, 99→98, ...），丢失率依旧稳定 ≈ 50%。
**随机化只改变"何时丢"，不改变"丢多少"**——决定丢失率的是"两侧是否持续都有在途请求"。

于是：丢失率稳定 ≈ 50% → 库存消耗 ≈ 成功数 ÷ 2 → **100 件库存最多支撑 200 单成功**。
压测恰好发 200 单 → 全部成功 → 超卖 = 200 - 100 = **100（正好等于库存数，即上限）**。
请求发 300 单也一样：库存在 200 单成功时耗尽，后 100 单失败，超卖仍是 100。

实测证据（同一环境，改变并发模式 / 业务耗时分布）：

| 实验条件 | 在途请求 | 丢失更新 | 成功单 | 超卖 |
|---|---|---|---|---|
| 单线程背靠背 | 1 | 0 | 100 | 0（正常） |
| 4 线程 × 50 次 | 4 | 100 | 200 | 100 |
| 10 线程 × 20 次 | 10 | 100 | 200 | 100 |
| 100 线程 × 2 次 | 100 | 100 | 200 | 100 |
| 100 线程 + 两侧耗时不同步(20ms/50ms) | 100 | 99 | 199 | 99 |
| 100 线程 + 业务耗时随机 [0,1000]ms | 100 | 100 | 200 | 100 |

结论：
- 只要两侧存在**并发在途请求**（持续背靠背处理），窗口重叠就是结构性的 → 丢失率 ≈ 50%，
  超卖被系统性放大到上限（= 库存数）；**随机耗时不改变丢失率**；
- 只有**完全串行**（任意时刻仅 1 个在途请求）才回归正常；
- 真实流量下"偶发少量超卖"的真正原因是**请求到达稀疏**（间隔远大于竞态窗口，窗口早已关闭），
  而不是耗时随机——本 demo 用发令枪让请求成批同时到达，把"偶发"变成"100% 必现"，便于教学。

## 7. 修复思路（留作练习）

| 方案 | 思路 | 关键点 |
|---|---|---|
| Redis 分布式锁 | Redisson `RLock` 锁住商品维度，跨 JVM 互斥 | `lock.lock()` 要包住完整事务；考虑锁超时、锁续期（看门狗） |
| 数据库原子扣减 | `UPDATE t_stock SET stock = stock - ? WHERE id = ? AND stock >= ?` | 根据 affected rows 判断成败，利用 InnoDB 行锁天然互斥 |
| 乐观锁 | 表加 `version` 字段，`UPDATE ... AND version = ?` | 失败重试；高冲突场景性能一般 |

> docker-compose 中的 Redis 已为你准备好了，可直接用于 Redisson 方案。

## 8. 接口清单

| 服务 | 方法 | 路径 | 说明 |
|---|---|---|---|
| order | POST | `/order/buy?productId=1&count=1` | 单次下单 |
| order | POST | `/order/pressure-test?productId=1&threads=100&times=2` | ★ 并发压测（复现入口） |
| order | GET | `/order/count` | 查询成功订单数 |
| order | POST | `/order/clear` | 清空订单（演示用） |
| stock | GET | `/stock/{productId}` | 查询库存 |
| stock | POST | `/stock/decrease?productId=1&count=1` | 扣减库存（Feign 调用入口） |
| stock | POST | `/stock/reset?productId=1&stock=100` | 重置库存（演示用） |

## 9. 压测结果字段说明

| 字段 | 含义 |
|---|---|
| `initStock` / `remainStock` | 压测前 / 压测后库存 |
| `successCount` | 下单成功次数（= 卖出订单数） |
| `soldCount` | 库存实际扣减量 = initStock - remainStock |
| `lostUpdateCount` | 丢失更新次数 = successCount - soldCount（>0 即账实不符） |
| `overSold` / `overSellCount` | 是否超卖 / 超卖单数 = successCount - initStock |
| `conclusion` | 一句话结论（正常 / 丢失更新 / 超卖） |

## 10. 常见问题

**Q1：压测返回「压测异常，请检查库存服务是否注册成功」？**
检查 Nacos 控制台是否有 `de-stock-service` 实例，以及 MySQL 容器是否健康（`docker ps`）。

**Q2：双实例压测有时超卖不多或不超卖？**
- 确认 Nacos 中库存服务确实是 2 个实例；
- 调大 `demo.stock.fake-business-delay-max-ms`（如 2000）扩大随机休眠上限；
- 提高压测并发（如 `threads=200&times=2`）。

**Q3：MySQL 容器启动后查不到表？**
数据卷只在**首次创建**时执行初始化脚本。若需重新初始化：
```powershell
docker compose down -v
docker compose up -d
```

**Q4：`docker compose up -d` 拉镜像超时？**
见 4.1 节「国内源拉取再打标签」的方式。

**Q5：端口冲突？**
MySQL(3306)/Nacos(8848、9848)/Redis(6379) 如与本机软件冲突，修改 `docker-compose.yml` 的
端口映射，同时同步修改两个服务的 `application.yml` 连接地址。
