# E07 Docker / Milvus 恢复审计

**Gate A BLOCKED；部署、容器与Collection当前状态UNKNOWN。**

## 已核验

- 安装路径为现有Docker Desktop.exe，实际Get-Item版本4.82.0.233772；未安装、升级或替换。
- docker context show=desktop-linux；inspect为Docker Desktop Linux engine named pipe。未更改context、网络、端口、volume或compose。
- 仓库compose文件搜索未返回匹配；没有推测项目原部署位于何处，不能将诊断提案当作真实部署。
- 初始docker ps --all返回named pipe不存在。按授权只启动该GUI/engine一次，WindowStyle Hidden，命令exit0。Get-Process观察到了Docker Desktop和com.docker.backend进程。
- 随后84次docker version只读poll均未成功取得Server。配置上限180秒，实际181.848秒，收尾计时偏差单独披露，没有第二次启动请求或无限等待。

## 无法核验

Linux engine未响应，未取得容器清单，容器数量不是已证实为0。现有Milvus/minio/etcd名字、状态、compose/project标签、挂载/volume、端口/网络、镜像和真实部署文件均UNKNOWN；因此容器start命令0，禁止猜名称或compose up。

Milvus source=`default/knowledge_chunk_vectors_local`、eval=`default/knowledge_chunk_vectors_evaluation`只作为授权预期名字，没有当前SDK/REST检查；列表、真实field/primary key/autoID/dynamic/dimension/index/metric、entity count、52逐ID metadata及数值均UNKNOWN。未创建/删除/alter或重建任何Collection；未加载Milvus生产Bean。

## 审计证据

docker-installation-observation.json保存安装/context观察；docker-inventory.json保存每次poll UTC/退出码及实际耗时；bootstrap-run-evidence.json保存统计。由于engine不可读，容器inspect未执行，不能导出不存在的mount或label证据。没有捕获Docker credential、Authorization或私有YAML。

Desktop启动后可能有自身正常后台动作，实际镜像/volume/容器状态未知；本工具没有下载/部署/清理命令，不对未知状态宣称PASS。进程启动保留，本轮没有停止其他进程或容器，也没有继续恢复。需要Commander新裁定engine阻断后续范围。
