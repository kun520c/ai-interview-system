# 本机演示运行与恢复

本手册只针对本机已有的 `ai_interview` 演示目标。它不是公网部署方案，也不授权迁移、覆盖恢复或清理数据。执行前重新核对容器、端口、任务与 PID；不要操作 evaluation 或其他项目。当前实现维持评分 v4、报告 v1，以及 RocketMQ + Transactional Outbox。

## 身份与持久化

- 候选源码 `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`；运行 JAR `backups/MQ_CANDIDATE_20261115_soe3tev4/candidate-0509cd6.jar`，SHA-256 `3346146f49fca7c7c604b2d70c3ebddc134d7d49a97467a07d80fe35c2fa07e0`。`backend.py` 启动前验证哈希，不将新构建误当作正在运行的包。
- MySQL：`ai-interview-mac-mysql-1`，`127.0.0.1:3308/ai_interview`，`ai-interview-mac_ai-mysql-data` 挂载 `/var/lib/mysql`。当前任务表存在；不执行任何初始化 SQL 或迁移。
- Milvus：`ai-interview-e0r2-milvus`，`127.0.0.1:19532/default/knowledge_chunk_vectors_local`，`ai-interview-e0r2-milvus-data` 挂载 `/var/lib/milvus`。同一服务还可能承载其他隔离目标，故本手册不停止它、不更改 Collection。
- RocketMQ：`ai-interview-local-demo-namesrv` 监听 `127.0.0.1:19877`；`ai-interview-local-demo-broker` 通告 `127.0.0.1:20912`。Topic `ai-interview-local-demo-report`、Group `ai-interview-local-demo-report-consumer`。现有独立命名卷 `ai-interview-local-demo-mq-store-9lpoxk7u` 和 `ai-interview-local-demo-mq-logs-9lpoxk7u` 分别挂载 store 和 logs。两容器设为 `unless-stopped`；`SYNC_FLUSH` 和卷不等于机器故障零丢失。
- [compose.rocketmq.yaml](compose.rocketmq.yaml) 是现有目标的配置快照，两个卷均为 external；[broker.conf](broker.conf) 是当前实际 bind 配置的内容副本。**禁止对这份快照运行 `docker compose up` 或 `down`**：现有容器已有消息、偏移及特定 bind 源，重建会改变资源。只对它运行 `docker compose -f tools/local-deployment/compose.rocketmq.yaml config --quiet` 做语法校验。
- 前端是项目 `frontend/` 中的 Vue 源码和既有依赖，Vite 仅绑定回环地址；不是公网静态托管。后端和前端日志、当前逻辑备份在 Git 忽略的 `backups/LOCAL_RUNTIME_20261116/`，可能含敏感内容，不要上传。

## 启动前的只读检查

在项目根目录执行；输出只检查本机演示目标。服务启动后可能立即处理已存在的 `QUEUED/RUNNING` 任务，不能把 `accepting=false` 当成停止旧任务处理。

```sh
docker inspect ai-interview-mac-mysql-1 ai-interview-e0r2-milvus \
  ai-interview-local-demo-namesrv ai-interview-local-demo-broker \
  --format '{{.Name}} {{.State.Status}} restart={{.HostConfig.RestartPolicy.Name}}'
curl --noproxy '*' -fsS http://127.0.0.1:9093/healthz
docker exec ai-interview-local-demo-broker sh mqadmin clusterList -n namesrv:9876
docker exec ai-interview-local-demo-broker sh mqadmin consumerProgress \
  -n namesrv:9876 -g ai-interview-local-demo-report-consumer
docker exec ai-interview-mac-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -D ai_interview -e "SELECT task_state,delivery_state,failure_code,COUNT(*) FROM interview_report_task GROUP BY task_state,delivery_state,failure_code; SELECT COUNT(*) AS evaluating FROM interview_answer WHERE status=\"EVALUATING\"; SELECT COUNT(*) AS generating_without_active_task FROM interview_session s WHERE s.report_status=\"GENERATING\" AND NOT EXISTS (SELECT 1 FROM interview_report_task t WHERE t.session_id=s.id AND t.task_state IN (\"QUEUED\",\"RUNNING\"));"'
python3 tools/local-deployment/backend.py --check
```

`mqadmin` 在路由未就绪时可能返回进程码 0：必须检查输出中确有演示 Broker，且消费者的 Diff/Inflight 与任务状态相符。`/api/users/me` 的 401 只证明鉴权入口可达，不证明 MQ、模型或数据库端到端成功。若启动器发现活跃任务会拒绝；核对具体任务和影响后，才可显式加 `--allow-pending`。不要为了通过检查修改数据库。

## 本机启动

1. 确认 Docker Desktop、现有 MySQL/Milvus 健康；先启动已存在的 NameServer，再启动同一 Broker。以下命令只适用于这两个项目专用容器；已有运行容器无需重复启动。

   ```sh
   docker start ai-interview-local-demo-namesrv
   docker start ai-interview-local-demo-broker
   docker update --restart unless-stopped ai-interview-local-demo-namesrv ai-interview-local-demo-broker
   docker exec ai-interview-local-demo-broker sh mqadmin clusterList -n namesrv:9876
   ```

   等待实际出现 `127.0.0.1:20912` 的 Broker 和预期 Topic 路由，再启动应用；Docker 自动启动并不保证二者按顺序就绪。不要创建或重置 Topic/Group。

2. 在一个终端前台启动后端：

   ```sh
   cd /Users/kun/Developer/projects/ai-interview-system
   python3 tools/local-deployment/backend.py --check
   python3 tools/local-deployment/backend.py
   ```

   启动器只读取项目 `.env` 中的本地非 root MySQL/JWT 配置；默认使用当前受限的 `127.0.0.1:28891` 演示模型转发器和其 0600 令牌文件，不读取进程环境或 evaluation 数据库配置。转发器仍是一次性程序：停止或电脑重启后不会自动重建，本轮没有复位其额度。**若转发器不在，默认启动会拒绝**。可另行准备只含 `DEEPSEEK_BASE_URL`、`DEEPSEEK_API_KEY`、`EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY` 的本机专用 0600 文件，再用 `--model-config /绝对路径/本机配置.env`；不要将密钥作为命令行参数或放入 Git。该直接配置路径本轮未用真实供应商验证，也不具备现有转发器的逐次额度账本。

3. 在另一个终端启动前端：

   ```sh
   cd /Users/kun/Developer/projects/ai-interview-system/frontend
   AI_INTERVIEW_BACKEND_URL=http://127.0.0.1:8081 npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
   ```

   在 <http://127.0.0.1:5173> 打开本机演示。不要把 Vite `preview` 当作已配置 `/api` 反向代理的生产服务器。恢复服务后核查认证、已有报告、MQ 消费者、任务和弱项；已有 READY 读取不应触发模型。

## 停止、重启与故障

- 停新请求，核对上面的答案、活跃任务、异常 `GENERATING`、MQ 积压和在途均已妥善处理；还需确认外部模型请求已经结束。无法排空时保留现场。前台启动的前端和后端分别在其终端 `Ctrl-C`；后台进程必须先用 `lsof`、`ps`、JAR 路径和启动记录核验 PID/进程组，再只对对应演示进程发送 `TERM`，不要使用 `pkill java`。
- 演示 Broker 的镜像启动命令有 shell 包装层。空载时先 `docker update --restart=no ai-interview-local-demo-broker`，再执行 `docker exec ai-interview-local-demo-broker sh mqshutdown broker`，等待 `docker inspect` 显示退出；直接 `docker stop` 的历史尝试曾超时并以 137 退出。需要恢复时 `docker start ai-interview-local-demo-broker`，确认注册后重新 `docker update --restart=unless-stopped ai-interview-local-demo-broker`。停机期间保持策略 `no`，否则退出可能立即触发重启。
- 单独停止 NameServer 时也先关闭其重启策略，使用 `docker exec ai-interview-local-demo-namesrv sh mqshutdown namesrv`；恢复后等待 Broker 重新注册，再恢复 `unless-stopped`。先确保没有依赖者和在途任务。MySQL/Milvus 被其他本机目标共用，不能随此演示一起停止。
- `QUEUED/PENDING` 或租约过期任务可由正常扫描恢复派发；核查期限、发送次数、路由、积压及失败码。**已领取的 `RUNNING` 任务进程崩溃后，可能超时变为 FAILED，需要显式重新申请，且可能再次调用模型**；不要承诺自动 READY 或修改状态强行重放。`SUCCEEDED` 的重复消息和 READY 重读应由业务幂等处理。失败、DLQ、长期堆积或数据库状态异常时先保留任务 ID、状态、时间和日志，查明原因后再按既有契约人工处置。
- 观察：按任务状态/失败码分组；看 `consumerProgress` 的 `Diff`/`Inflight`、Broker 注册和本地磁盘 `docker exec ai-interview-local-demo-broker df -h /home/rocketmq/store`；检查受保护的应用日志和 Docker 日志。72 小时消息保留设置不是备份或故障恢复保证；没有自动告警、日志轮转和容量阈值管理。

## 备份与恢复边界

2026-11-16 对本机 `ai_interview` 以非 root 账号、`--single-transaction`、`--skip-lock-tables` 和 `--no-tablespaces` 生成过一份 0600 逻辑备份；本轮确认 14 张基础表均为 InnoDB。具体路径、散列和时点见 [本轮验收记录](../../docs/engineering/2026-11-16-本机部署收口与工程档案.md)。这份备份含私人业务数据，不入 Git；早于迁移的 13 表历史备份不能替代它。

此前部署验收时尚未在独立目标执行恢复；2026-11-16 后续收口已在新建无网络、无端口、无现有卷的 MySQL 8.0.45 临时实例恢复该快照，14 张 InnoDB 表的行数、`CHECK TABLE` 和重新导出的逐表数据指纹均一致。演练使用临时内存存储并在停止后消失，原备份及容器记录保留；结果见[收口记录](../../docs/engineering/2026-11-16-本机部署收口与工程档案.md#最终交付冻结与恢复演练)。这只是 MySQL 单库恢复，不证明应用级读取或 MySQL、Milvus、MQ 同一业务时点的一致恢复。

实际恢复前仍须明确并批准**独立**实例、空 Schema/Collection、版本、恢复点和数据来源，校验备份散列与权限，再在目标验证表、报告、向量标识和业务读取。禁止把备份直接导入现有库、覆盖现有卷或向 evaluation 恢复。MySQL 导入会执行 DDL，不因已有备份或本次临时演练而自动获得其他目标的迁移授权。机器重启、磁盘损坏和离机恢复尚未实测，不得把单机容器重启等同灾备通过。
