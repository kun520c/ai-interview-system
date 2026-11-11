# 已退役：Jev 网络实验

2026-11-11 J1 完全移除 Jev。`run.ps1` 现在直接拒绝执行，不读取凭据、不发网络请求。
原 `JevScoreOnce`、`JevNetworkDiagnostic`、`JevHistoricalReplay` 和专属离线测试依赖已删除的生产类，随本轮退役。
旧源码可在 Git 基线 `afbd1c5ab010f3a0a296e15e25c016f81eb2b74f` 查阅；不要恢复到活跃 classpath。

历史报告、原始证据和锁文件保留，成功、失败、BLOCKED 等结论不变。
业务历史评价兼容由现行 MySQL 查询/重放测试验证，不需要恢复旧 SDK、密钥或模型请求。
