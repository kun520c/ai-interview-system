# 手动清理 Milvus 孤立向量

入口：com.kun.aiinterview.knowledge.maintenance.OrphanVectorMaintenance。
它不启动 Spring，不进入 HTTP/后台任务路径，不创建或修改 Collection，不修改 MySQL。

## 必须先建立维护窗口

1. 阻断所有相关文档处理入口、重试、发布和自动重启；确认操作针对同一组 MySQL/Milvus 数据。
2. 等待所有相关写入任务、在途 RPC 和补偿完成。停止所有可能继续写入的实例，包括旧版实例、失联实例和旧进程；需要运维从进程/部署平台确认，不能只看数据库。
3. 数据库没有 PROCESSING 只是附加检查。工具无法跨进程证明排空；两个显式参数是操作者对上述事实的确认。无法确认时不要执行，强一致读取也无法替代排空。
4. 记录维护证据（实例清单、排空/停止时间、负责人、目标地址）并持续禁止写入直到结束。工具每批检查 PROCESSING；发现非零就停止。崩溃遗留 PROCESSING 也需要按现有恢复流程处理并再次排空，不能为运行工具直接清状态。

即使 dry-run 也要求两个确认参数。dry-run 的候选不能在恢复写入后直接用来删除。

## 配置及执行

使用 Java 21。连接变量只放在当前进程，凭据来自运维的秘密管理，不提交到 Git：

- ORPHAN_MYSQL_URL、ORPHAN_MYSQL_USERNAME、ORPHAN_MYSQL_PASSWORD
- ORPHAN_MILVUS_URI、ORPHAN_MILVUS_DATABASE、ORPHAN_MILVUS_COLLECTION、可选 ORPHAN_MILVUS_TOKEN
- ORPHAN_EXPECTED_TARGET：MySQL_SCHEMA/Milvus_DATABASE/Collection，例如 ai_interview/default/knowledge_chunk_vectors_local。工具从 MySQL SELECT DATABASE() 核对；操作者另须核对两个连接地址。
- ORPHAN_JOURNAL：本次全新 TSV 文件的路径，父目录必须存在。既有文件绝不覆盖。
- 可选 ORPHAN_BATCH_SIZE：1..1000，默认 500。

先编译并构建运行 classpath（PowerShell）：

    .\mvnw.cmd -B -ntp -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target/maintenance-classpath.txt"
    $maintenanceClasspath = "target/classes;" + (Get-Content target/maintenance-classpath.txt -Raw).Trim()
    & "$env:JAVA_HOME/bin/java.exe" -cp $maintenanceClasspath com.kun.aiinterview.knowledge.maintenance.OrphanVectorMaintenance --writers-drained --old-instances-stopped

默认只打印统计、待删除 ID，并写本地日志。确认扫描结果后，在同一维护窗口设置一个新的 ORPHAN_JOURNAL，重新执行同一命令并额外传 --delete。工具会重新完整扫描，绝不直接信任历史候选文件。

## 边界与失败恢复

- SDK 固定为项目已有的 2.6.20。已用本地 JAR 的 javap 和编译确认 QueryIteratorReq.consistencyLevel(STRONG)、limit(-1)、batchSize 以及 QueryIterator.next()/close()；另查验 QueryIterator 的字节码，确认其将 consistencyLevel 和 ignoreGrowing 传入底层 QueryReq；[官方接口](https://milvus.io/api-reference/java/v2.6.x/v2/Vector/queryIterator.md)。
- 只读取 vector_id，包括 growing segments；使用迭代器避免 OFFSET 深分页上限。MySQL 每批查所有 Chunk，不按 ACTIVE、READY、模型或版本过滤。存在任何对应 Chunk 都保留。SQL 返回候选拼写，遵循数据库排序规则，避免仅按 Java 大小写比较误删。
- 扫描全部完成才开始删除；迭代器始终关闭。内存只保留一批，候选写入本地文件。删除前再次查 MySQL。沿用现有 Vector ID 参数模板删除约定，显式指定 databaseName 和 Collection；不使用 documentId、清空或 drop。
- 日志每行 flush + fsync。CANDIDATE 在任何删除前保存；成功后以 STRONG query 验证指定 ID 已不存在，再记 DELETED_VERIFIED。
- 超时、异常或强一致读取未确认时记 UNCONFIRMED 并停止。日志保留已完成、未确认和尚未尝试的候选 ID。进程在删除后记账前崩溃也可安全恢复：保持维护窗口，重新扫描到新日志；已消失 ID 不再被扫描，仍存在 ID 重新核对 MySQL。不要手动重放旧 ID。
- 重复运行安全，但运维排空前提每次都必须成立。日志失败/磁盘耗尽时停止；不提供定时任务、分布式锁或跨存储原子性承诺。

## RAG 轨迹取舍

成功准备的新评价通过 EvaluationOrchestrationResult.pendingTrace 将不可变检索结果交给现有 Workflow 短事务。锁 Answer 并验证领取时的 Attempt 后，轨迹、评价、Answer 状态、Question/Session 推进共同提交；CAS 或 SQL 失败共同回滚。历史评价不携带待写轨迹，重放不再插入。

原 F4 设计明确要求保留 Prompt/模型/校验等失败后的检索诊断。本次保留该需求：准备阶段异常时，在独立短事务内再次锁 Answer、校验原 Attempt 后保存诊断，原异常继续抛出，诊断写失败作为 suppressed 异常。诊断没有有效评价关联；旧 Attempt 不能写入。既有诊断不删除。外部调用仍不在事务内。

为满足正式提交原子性，Session CAS 或正式写库失败时本次待提交轨迹随整个事务回滚；不再事后单独保存这一批次。进程在准备后崩溃但未进入提交/异常处理时的检索诊断无法保留。未增加字段，无法给失败诊断新增结构化状态或 Attempt 标记；其用途沿用“没有对应评价的历史批次”。rag_hit_log.chunk_id -> knowledge_chunk.id 的 ON DELETE RESTRICT 保持不变；真实外键冲突会回滚整个正式提交，绝不删除旧审计数据来迁就提交。

## 本轮验证（2026-10-15）

- Java 21 编译及 mvnw.cmd -B -ntp -DskipTests package：BUILD SUCCESS。
- 最终聚焦：136 tests，0 failures，0 errors，0 skipped。
- 最终普通全量：1653 tests，0 failures，0 errors，19 guarded skips；最终 XML 再次汇总一致。
- 使用 ai_interview_test 的真实 MySQL/MyBatis/Spring 事务验证 A/B 接管、轨迹/评价原子提交、Session CAS 回滚、外键失效回滚、历史重放及维护查询对 INACTIVE/大小写等价 Vector ID 的保留。
- 维护工具的分页/20000 行分批扫描、默认 dry-run、按 ID 删除、删除前复查、部分失败日志与再次扫描均为 SDK Mock 验证。
- 当前 127.0.0.1:19530 不可达；未运行真实 Milvus Collection 测试，未调用真实模型，未对正式库/集合执行清理。
