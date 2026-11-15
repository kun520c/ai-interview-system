# M1-B 数据库准入（唯一测试目标已批准并执行）

只申请并获准一个目标：Docker `ai-interview-mac-mysql-1`，宿主 `127.0.0.1:3308` → 容器 `02ccbfd0e120:3306`，精确 Schema `ai_interview_test`。不包含 local、evaluation、新数据库或其他实例。批准前未执行迁移或写入测试；批准后执行记录见下文。

最终 SQL：[20261114_interview_report_task.sql](../../sql/migrations/20261114_interview_report_task.sql)。完整 DDL 仅一个 CREATE TABLE，与 M1-A §8.3 一致；无 USE、自动迁移、DROP、改旧表或数据修复。本测试目标已执行，禁止重复执行；对其他目标仍是待批准文件。迁移文件是唯一新增表定义入口，基础快照不重复收录该表。

2026-11-14 执行前只读核验：MySQL 8.0.45；InnoDB；Session.id BIGINT UNSIGNED 主键，version INT UNSIGNED；现有 user_created/user_status 索引；CHECK ENFORCED=YES；任务表不存在。数据库 session/global 时区 SYSTEM，system=CST，NOW−UTC_TIMESTAMP=08:00:00。实现以 UTC_TIMESTAMP(6) 计算任务期限，不依赖 JVM 时钟或现有连接时区；created_at/updated_at 仅审计，不用于收口。批准后真实建表、约束、受影响行及四条索引查询计划验证已完成，详见专项验收。

迁移账号：容器内 root@localhost（现有全局权限，不增加权限），只执行本文件到上述 Schema；测试账号优先使用现有 ai_interview_test@%（该 Schema ALL，另有 performance_schema.data_locks/data_lock_waits SELECT），不改变授权。所有命令显式指定 Schema；凭据只在进程环境中，不写进文档。

备份：/private/tmp/m1b-ai-interview-test-before.sql，权限 0600，仅该 Schema，mysqldump --single-transaction --skip-lock-tables --no-tablespaces；SHA-256 3e0bdf8184637570853f65ecfee28c0a65f82a5ba9f14fdc7d02e520102dd285。Session/Report/Weakness 精确计数均为 0；其他表信息_schema估计为0，不能据此认定全部数据可销毁。现有数据保留；测试使用 UUID 前缀/生成主键，清理仅本轮创建的行，不 TRUNCATE、不重建 Schema。

申请范围：先一次执行新增表迁移，再运行经审查的本专项 MyBatis/事务/并发/MQ 故障验证；若运行完整 Maven，先审查既有测试的清理范围，不把该批准扩展到不安全的既有清理脚本。所有测试数据限此目标。新增任务 FK 会要求测试先清理自身任务再删除自身 Session。

现有数据影响：增加一张空表及其约束索引；不修改原行。DDL 可能等待元数据锁，执行前再次核验表缺失和目标。若目标发生变化停止。MySQL DDL 隐式提交，不与应用切换原子回滚。

回退：停写、停止派发、排空旧/新执行者，逐项核对 GENERATING/任务/已有报告/FINAL 后才切换应用；保留任务表、历史消息和数据，不 DROP。任何历史修复、备份恢复或额外数据删除需另行批准。备份是恢复依据，不代表可自动覆盖现有数据。

准入：本目标 APPROVED_AND_APPLIED；其他所有数据库 NOT_AUTHORIZED。未批准期间只进行代码、无数据库写入检查。测试名称不是隔离授权证明；本次批准依据是用户明确答复和精确端点/Schema。

## 实际批准与执行

用户在本次聊天明确答复“批准这个唯一目标及本次测试数据写入清理”。执行前再次读取 DATABASE()=ai_interview_test、VERSION()=8.0.45、hostname=02ccbfd0e120、容器端口3306；表存在计数0。随后通过 docker exec 显式指定 ai_interview_test 执行上述迁移，退出0。未执行其他 DDL、未扩权、未访问 evaluation。该批准不适用于其他目标。
