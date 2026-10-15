# MySQL 8.0 InnoDB：MVCC、一致性读与 Read View

> Knowledge category: `MYSQL`
> Target question: H06 — REPEATABLE READ 下的 MVCC、Undo Log、事务 ID 与 Read View
> Version scope: MySQL 8.0 Reference Manual；本地版本审计记录为 MySQL 8.0.45。本文只描述 InnoDB 的一致性非锁定读与锁定读边界。
> Primary source: https://dev.mysql.com/doc/refman/8.0/en/innodb-multi-versioning.html

## 1. Core model

InnoDB 是支持多版本的存储引擎。更新行时，InnoDB 保留构造较早行版本所需的信息；这些信息既用于事务回滚，也可用于一致性读。这样，普通 consistent read 在需要读取历史可见版本时，可以依据事务可见性选择合适版本，而不必把所有读取都解释成等待当前写入结束。MVCC 描述的是版本与可见性机制，不表示 InnoDB 在任何场景下都不使用锁。

## 2. Main mechanism

MySQL 8.0 手册说明，InnoDB 聚簇索引记录包含修改该行的事务标识 `DB_TRX_ID`，以及名为 `DB_ROLL_PTR` 的 rollback pointer。该指针指向 rollback segment 中的 undo log record；对于已更新的行，undo 信息保存重建更新前行内容所需的数据。若一致性读不能看到当前记录版本，InnoDB 可以利用 undo 信息取得较早版本。因此 Undo Log 不只与 rollback 有关，也为一致性读重建历史版本提供材料。

consistent nonlocking read 按一个数据库快照判断记录版本是否可见。工程讨论中常把这类可见性快照称为 Read View；这里应把它理解为读操作的事务可见性边界，而不需要臆测手册没有规定的内部字段或判断算法。版本上的事务标识与读操作的可见性边界共同决定当前一致性读可观察哪个版本。若需回溯，undo 链提供构造旧版本的记录信息；这几个角色不可互换：事务 ID 标识修改版本，undo 信息保存历史重建材料，Read View 表达读取可见性。

## 3. Important boundary

在 `REPEATABLE READ` 隔离级别下，同一事务中的 consistent reads 使用该事务第一次 consistent read 建立的 snapshot；因此后续普通、非锁定 `SELECT` 会继续按该快照读取。这里的对象是 consistent read，不是事务中的所有 SQL。`READ COMMITTED` 则不同：同一事务内的每次 consistent read 都建立并读取一个新的 snapshot。回答隔离级别差异时，应说清比较的是一致性读的快照时点。

`SELECT ... FOR UPDATE` 是 locking read，不是普通的历史快照读。MySQL 8.0 手册说明，`SELECT ... FOR UPDATE` 读取最新可用数据，并对读取到的行设置排他锁。因此，普通 consistent nonlocking read 与 locking/current read 在可见数据和加锁行为上不同。MVCC 不代表“没有锁”；需要基于读后写保护时，应区分普通快照查询与 `FOR UPDATE` 等锁定读。

## 4. Common misconceptions

“Undo Log 只用于 rollback”忽略了 update undo log 也服务于一致性读中的旧版本构造。“REPEATABLE READ 下事务的所有 SQL 都读一个历史快照”把 consistent read 扩大到了锁定读和 DML。准确表达应限定为：同一 RR 事务的 consistent reads 通常沿用第一次一致性读建立的快照；locking read 有自己的锁定与最新数据语义。MVCC 能支持版本化读取，但不能据此断言所有读写都无锁或所有并发异常都已自动解决。

## Official references

- Primary: [MySQL 8.0 Reference Manual — InnoDB Multi-Versioning](https://dev.mysql.com/doc/refman/8.0/en/innodb-multi-versioning.html) — 行版本、`DB_TRX_ID`、`DB_ROLL_PTR` 与 undo 信息。
- Secondary: [Consistent Nonlocking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html) — consistent read 与隔离级别下的快照。
- Secondary: [Transaction Isolation Levels](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html) — RR 与 RC 的一致性读快照时点。
- Secondary: [Locking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html) — `SELECT ... FOR UPDATE` 等锁定读。
