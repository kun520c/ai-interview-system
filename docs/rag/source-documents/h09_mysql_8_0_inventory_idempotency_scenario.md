# MySQL 库存与幂等：假设场景与项目事实边界

> Title: MySQL 库存与幂等：假设场景与项目事实边界
> Bank ID: H09
> Knowledge category: PROJECT_SCENARIO
> Knowledge point: concurrent_inventory_idempotency
> Difficulty: HARD
> Target question: 假设库存最终以 MySQL 为事实来源，当前库存只剩 1 个，同时来了很多个扣减请求，而且客户端还可能因为超时重复请求。你会如何设计，保证不超卖、不重复扣减，并能够正确处理失败和重试？
> Version scope: MySQL 8.0 InnoDB + RFC 9293；仓库只作类比，不是库存实现。
> Primary official source: [LOCK]
> Secondary official sources: UPDATE, UNIQUE, COMMIT, ACID, DEADLOCK, ERROR, TCP（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

这是假设 MySQL 库存设计；当前仓库没有库存、商品、订单模块。表、状态与恢复流程仅为设计示意。

## 2. Main mechanism

数量 q 必须为正，id 唯一定位一行，用同一次条件更新检查并扣减，检查影响行数为 1 才算成功；0 表示没有完成扣减，可能库存不足或记录不存在。也可在事务内 FOR UPDATE 锁定后检查和扣减，锁持续到提交/回滚；不能无锁先查后单独写。[LOCK][UPDATE]

```sql
UPDATE inventory SET stock = stock - :q
WHERE id = :id AND stock >= :q;
```

上例为参数化 SQL 示意。使用稳定非空 requestId，在业务范围内定义 NOT NULL UNIQUE 幂等键并保存结果；UNIQUE 允许多个 NULL，不能漏掉非空条件。先取得唯一请求记录处理权再扣减；重复键不得再扣，应等待或读取持久状态，重放完成结果。[UNIQUE]

## 3. Important boundary

库存变更、幂等状态及必须共同提交的同一 MySQL 事务性资源记录处于同一事务；失败整体回滚，结果与扣减一并提交。唯一冲突路径先结束本次失败事务，再恢复原请求状态，不能 catch 后继续扣减。此方案不覆盖任意远程副作用。[COMMIT][ACID]

PROJECT ANALOGY ONLY / NOT INVENTORY IMPLEMENTATION EVIDENCE：项目面试提交的 requestId 仅作幂等类比，不是库存或库存恢复证据。

## 4. Common misconceptions

死锁使被选中的事务整体回滚；锁等待超时默认可能只回滚失败语句，启用 innodb_rollback_on_timeout 才改变该范围。本设计锁等待失败时回滚整个业务事务后，对可重试失败有限重试并退避，复用同一业务键。[DEADLOCK][ERROR]

客户端/网络超时不证明数据库失败：可能已提交而响应未收到，这是工程推理。结果不明时先查询/恢复持久化幂等状态；已完成则返回原结果，仍处理中则等待或恢复，暂未查到也不能直接断定失败。后续尝试仍先竞争同一唯一键，由事务与唯一约束协调，不能盲目再扣一次。[TCP][UNIQUE]

## 5. Official references

- [LOCK] MySQL 8.0，§17.7.2.4 Locking Reads
- [UPDATE] MySQL 8.0，§15.2.17 UPDATE，条件/行数
- [UNIQUE] MySQL 8.0，§15.1.20 CREATE TABLE，UNIQUE
- [COMMIT] MySQL 8.0，§15.3.1 COMMIT/ROLLBACK
- [ACID] MySQL 8.0，§17.2 InnoDB ACID
- [DEADLOCK] MySQL 8.0，§17.7.5.3 死锁事务重试
- [ERROR] MySQL 8.0，§17.21.5 InnoDB Error Handling
- [TCP] RFC 9293，§3.8.3 连接失败

[LOCK]: https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html
[UPDATE]: https://dev.mysql.com/doc/refman/8.0/en/update.html
[UNIQUE]: https://dev.mysql.com/doc/refman/8.0/en/create-table.html
[COMMIT]: https://dev.mysql.com/doc/refman/8.0/en/commit.html
[ACID]: https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html
[DEADLOCK]: https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlocks-handling.html
[ERROR]: https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html
[TCP]: https://www.rfc-editor.org/rfc/rfc9293.html

项目类比：InterviewService.java、隔离 schema；非库存证据，路径见内容审查。
