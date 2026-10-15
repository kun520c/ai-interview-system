# 当前发布范围与验证边界

当前应用包含 Java 后端和 `frontend/` Vue 前端。前端覆盖认证、个人资料、创建与进行面试、历史、详情、报告，以及管理员题库和知识文档操作。

主问题来自固定题库。RAG 只为回答评价提供知识证据，Java 控制评分校验、总分、追问、状态、事务与权限。当前配置默认 DeepSeek 模型为 `deepseek-flash`；COSINE 阈值开关默认关闭，独立 Semantic Gate/Rerank 实验没有生产注册。

普通数据库测试使用 `ai_interview_test`；真实模型、Embedding、Milvus和AI baseline测试保留显式环境变量开关，默认跳过。测试数据源凭据由执行环境提供，不能将本机配置或秘密加入仓库。

公开离线 RAG 回归使用[合成夹具说明](../src/test/resources/rag-p1/README.md)。实验工具的可复现入口和历史档案依赖见[工具说明](../tools/README.md)。这些测试验证业务合同，不代表真实检索或 Judge 质量获得普遍提升。

历史实验锁、失败结果、冻结协议、原始请求响应、数据库快照、截图归档及本机运行身份继续保留本地。文档中的历史验收日期和 FAIL/STOPPED/PENDING 结论不因发布改写。原始证据的本机链接可能不随公开源码发布。
