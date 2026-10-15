# MySQL 8.0 InnoDB 事务与 ACID

> Title: MySQL 8.0 InnoDB 事务与 ACID
> Bank ID: E04
> Knowledge category: MYSQL
> Knowledge point: transaction_acid
> Difficulty: EASY
> Target question: MySQL 事务的 ACID 分别指什么？为什么数据库需要事务？
> Version scope: MySQL 8.0 InnoDB 事务；业务不变量需应用配合，持久性有配置与硬件边界。
> Primary official source: [ACID]
> Secondary official sources: COMMIT, ISO（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

事务把多个相关数据库操作组成一个逻辑工作单元，避免业务步骤只完成一部分。ACID 分别是 Atomicity 原子性、Consistency 一致性、Isolation 隔离性、Durability 持久性；这四项从失败恢复、数据规则、并发相互影响、提交后保存等角度描述事务要求，不能相互替代。[ACID][COMMIT]

## 2. Main mechanism

原子性要求事务内受事务保护的操作整体成功或整体回滚。例如两账户转账时，扣款和入账应放在同一个 InnoDB 事务中，应用发现任一步失败就回滚完整业务单元，而不是提交已经完成的前半部分。COMMIT 确认事务，ROLLBACK 撤销事务内尚未提交的变更。[ACID][COMMIT]

一致性要求执行前后仍满足数据库约束和业务不变量。数据库能执行已定义的约束，但不会从业务描述中自动识别“转账总额不变”等规则；应用必须写对事务内操作、检查结果并选择提交或回滚。这是基于事务机制的工程责任划分，不能把 ACID 当成任意业务代码都正确的保证。[ACID][COMMIT]

隔离性控制并发事务之间的相互影响。InnoDB 支持 READ UNCOMMITTED、READ COMMITTED、REPEATABLE READ、SERIALIZABLE，不同级别的读取与锁定保证不同，不能简单说所有事务都像没有并发一样执行。[ACID][ISO]

## 3. Important boundary

持久性要求成功提交的结果被可靠保存。InnoDB 相关保证还依赖日志刷新设置、是否使用二进制日志及相应同步配置，以及操作系统、存储设备是否兑现写入持久化要求。不能从“已调用 COMMIT”推导在任意错误配置或任意硬件损坏下数据都绝不丢失。[ACID]

原子性说明一个事务的整体性，隔离性说明事务之间的影响；数据库并不为了原子性而禁止所有并发。以下仅为未执行的事务结构示意，失败路径应选择 ROLLBACK，不能在中途提交后还指望撤销全部步骤：

```sql
START TRANSACTION;
-- 同一事务性资源内的扣款、入账与结果检查
-- 全部符合业务条件时 COMMIT；否则 ROLLBACK
```


## 4. Common misconceptions

“事务自动保证所有业务一致性”遗漏应用逻辑；“任意表的写入都能回滚”遗漏非事务表边界。本文的工作单元限定 InnoDB 事务性操作，不把可能隐式提交的 DDL 或外部调用纳入同一回滚保证。需要解释为何使用事务时，应指出部分成功会破坏哪个具体业务不变量。[ACID][COMMIT]

## 5. Official references

- [ACID] MySQL 8.0，§17.2 InnoDB and the ACID Model，四项性质及持久性条件
- [COMMIT] MySQL 8.0，§15.3.1 START TRANSACTION/COMMIT/ROLLBACK，事务边界
- [ISO] MySQL 8.0，§17.7.2.1 Transaction Isolation Levels

[ACID]: https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html
[COMMIT]: https://dev.mysql.com/doc/refman/8.0/en/commit.html
[ISO]: https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html
