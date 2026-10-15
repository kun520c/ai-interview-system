# Phase 5 Jev protocol precision patch review

Date: 2026-10-08 (Asia/Shanghai). Scope: offline protocol compatibility only.

**专项补丁与离线验证 PASS；不恢复 Phase 5B，不执行 Phase 5C 或 Phase 6。** 第一轮真实 Jev 拒绝仍是原 epoch 的事实，原事故报告、首调用响应与冻结 38 cases 原样保留。

## 协议依据与不确定性

官方 Python ScoreAnswer 将 score 定义为 rubric 索引的概率加权期望，可为小数；legend 对应 rubric，probabilities 与 legend 使用相同 key，概率和描述为约等于 1。[官方 Python schema](https://github.com/typesafe-ai/typesafe-sdk-python/blob/main/src/typesafe_sdk/_schemas/models.py)。官方 JS ScoreResponse 同样定义期望值以及 score、legend、probabilities 字段。[官方 JS types](https://github.com/typesafe-ai/typesafe-sdk-js/blob/main/src/types.ts)。读取日期为本次执行日，分支 main。

这两份官方源码没有声明所有版本固定输出两位小数，也没有指定显示舍入中点规则，不能声称“两位小数”是通用正式承诺。[独立实测仓库](https://github.com/WallerChen/jev-measured#3-score-is-not-01-and-noul-has-no-confidence-field)报告展示概率重算与 score 可差约 0.02；这是其测量观测和解释，仅作事故背景，不作为官方协议或全模型契约。

本项目捕获的返回模型 jev-1.13.0 的五项 score 和概率都在两位小数数值网格。结合 Commander 授权，这支持受版本和网格约束的局部兼容策略，而不证明供应商内部精度或舍入实现。

## 三种设计比较

| 方案 | false accept / false reject 代价 | 决定 |
|---|---|---|
| 固定绝对误差 0.02 或 0.05 | 不同分布使用同一范围，可能接受不可实现分数，也可能拒绝可实现边界 | 不采用 |
| 按显示精度计算独立误差预算 | 忽略总概率=1和0/1截断，预算偏宽 | 不采用 |
| 原始数值量化区间 + 总概率=1的期望可行范围 | 只接受潜在分布可解释的误差；模型/精度不符合适用条件时保守拒绝 | 采用，限定当前观察版本与数值网格 |

恶意反例：报告 p4=1、其他概率0。独立预算 Σ(i×0.005)+0.005=0.055 会容许 score3.97；但总概率=1使 E≥3.98，而 score3.97区间上界3.975，无法相交。本补丁拒绝，测试确认。

## 适用范围与数值算法

保留原直接期望误差0.001检查。仅当它失败时尝试附加兼容分支：实际 response.model 必须恰好 jev-1.13.0，score及五项概率经去尾零后至多两位小数。jev-latest只是请求别名；新版本、相近版本字符串、未知模型和高精度不一致值都不能借用新预算，其原紧容差内的一致响应继续通过。

网格条件检验 parsed Double 对应的规范十进制值是否是0.01的整数倍，不从 JSON 表示自动推导供应商精度。0、1及带尾零的同值均属于网格。不规范化、clamp、覆写或修正 provider 数据。

对每项 p_i 建立 l_i=max(0,p_i−0.005)、u_i=min(1,p_i+0.005)。确认 Σl_i≤1≤Σu_i；先放下界，再将剩余概率填到上界。低索引优先取得最小 E，高索引优先取得最大 E。若低索引仍有容量，把较高索引的概率移向它只会降低 E，所以贪心得到线性目标极值。可行域是凸的，E形成连续区间。score区间为 [max(0,s−0.005),min(4,s+0.005)]，只在与可行E区间相交时接受。

使用已有 BigDecimal 做精确十进制运算，不引入外部LP依赖，不添加业务epsilon。原概率和/choice argmax浮点容差保留。闭区间按授权建议采用；供应商中点规则未知，闭端点仅表示保守可能性，不能声称每个端点都是实际可生成样本。

事故展示分布的可行E范围为 [3.520,3.560]；score3.56区间为 [3.555,3.565]。显式潜在分布 [0,0,0.016,0.410,0.574] 的总和为1，E=3.558，两位HALF_UP显示恰好得到原概率和3.56。本地数学重放及JUnit见证通过。

Java映射仍直接使用已验收原score进行0..4乘5及HALF_UP，原mapScore方法未改。clarity3.56保留3.56、映射18，其余四维原样通过。本次映射仅作协议测试，不作为质量样本或业务总分。

## 严格检查保持

JevClient的JSON类型、重复字段、尾随JSON、model和question key集合未改。Provider的answer.type、legend完整性及内容、概率key集合、非null/有限/[0,1]、原概率和0.0001约束、choice argmax0.000001约束、score范围、minConfidence、反馈合并与一次fallback语义未改。所有既有相关测试保留。

报告概率和超出原0.0001时，即使量化区间能拼成总和1仍拒绝。因此未来和为0.99/1.01的展示响应、新版本或高精度不一致响应仍可能fallback，须单独审查；本任务不扩大这些约束。

## 文件与验证结果

- 修改 src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java：仅score一致性条件增加受限兼容分支、两个私有数学方法及边界常量。
- 新增 src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevScorePrecisionCompatibilityTest.java：63项离线参数化测试，独立合成Context，captured数据只含数值，不读取gold。
- 新增本报告与 PHASE_5_PROTOCOL_PRECISION_PATCH_TEST_RESULTS.md：记录依据、假设、完整测试明细、原文件哈希及停止点。

最终6套JUnit共201项，失败0、错误0、跳过0，BUILD SUCCESS；新增专项63项。首轮201项中1项新断言因省略字段与record显式null的JSON表示不同而失败，改为完整typed response比较并保留数值/映射断言后，全套复跑通过。没有修改、删、跳过任何既有测试。

独立临时工具用原捕获full response调用生产数值校验，五维/choice全部通过，序列化前后值相同；没有调用evaluate、生成、质量评估或数据库。Maven生产compile/classpath、临时工具javac成功。单元测试与编译不是在线集成证据；未运行全回归。

本阶段 Jev HTTP=0、DeepSeek=0、Embedding=0、MySQL/Milvus/RAG trace写入=0；网页文档阅读不属于Provider推理。只运行拦截HTTP、Mockito和纯数值校验，没有创建datasource或Milvus客户端。

## 完整性与限制

受检原文件408个，仅授权生产文件改变，其他407/407文件字节哈希一致，包含原事故总报告、旧Phase5 outputs、Phase4/RAG、题库、21 source documents及应用配置。已有未提交改动保留。新增报告被既有docs ignore规则忽略，为本地交付；未git add/commit/push。

Frozen benchmark SHA-256: fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a，与Commander给定值相同；完整哈希在测试报告。

两位小数是局部受控假设，官方跨版本精度保证仍未知。该补丁仅证明协议可行性与严格拒绝，不能证明中文面试语义质量、38/38 primary、DeepSeek反馈或真实fallback。原第一轮失败状态不更新，没有重跑旧E02、没有新Phase5B epoch。

交Commander审查版本/精度边界、补丁和测试。只有新的明确授权才能开启新epoch，并保留旧epoch拒绝事件。

```text
PHASE_5_PROTOCOL_PATCH_OFFLINE_PASS: PASS
CAPTURED_RESPONSE_REPLAY_ACCEPTED: PASS
CLEARLY_INCONSISTENT_RESPONSE_REJECTED: PASS
STRICT_NON_SCORE_CHECKS_PRESERVED: PASS
ROUNDING_ASSUMPTION_DOCUMENTED: PASS
TARGETED_UNIT_TESTS_PASS: PASS
FROZEN_BENCHMARK_SHA_UNCHANGED: PASS
FROZEN_PHASE4_RAG_ARTIFACTS_UNCHANGED: PASS
DB_MILVUS_WRITES_0: PASS
JEV_HTTP_CALLS_0: PASS
DEEPSEEK_CALLS_0: PASS
PRODUCTION_MUTATION_SCOPE_LIMITED: PASS
PHASE_5B_RESTART: BLOCKED_PENDING_COMMANDER_PATCH_REVIEW
PHASE_6: BLOCKED
```
