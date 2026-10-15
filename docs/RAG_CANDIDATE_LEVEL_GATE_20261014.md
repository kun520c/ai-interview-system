# 候选级语义门禁离线优化交付（2026-10-14）

已实现独立实验策略并通过定向离线验证。新增准入候选来自冻结校验器原本已经严格校验通过的集合；候选级版本诊断不再触发其他有效候选的全局清空。未接入生产。

决策原则：先完整执行原 P4.2 严格校验，不修改原响应；仅将明确 NO_SUPPORT、supports 为空的版本 UNCERTAIN 隔离为候选级诊断。待准入候选仍须 CORE_SUPPORT、版本确认、12～400 字符的连续真实正文引用和至少一个 PRIMARY_MECHANISM 的 DIRECT_MECHANISM 支持。辅助角色不能由模型改成主要角色；部分机制支持仍保留，并显式报告缺失机制。

UNCERTAIN verdict/relation、无法隔离的版本不确定、非法结构/身份/引用和同一 requirement 下的显式 DIRECT_MECHANISM/CONDITION_CONFLICT 矛盾继续全局拒绝。新策略修补了实验路径中“支持关系 UNCERTAIN 被忽略”和“直接支持与条件冲突同时存在仍可能准入”的安全缺口；P4.2 冻结源代码不变。全局异常均有明确原因，实际准入及覆盖诊断按清空后的结果生成。

H42-01 使用同一保存的原题契约、MySQL hydration 快照、Rerank 响应、Gate 请求和原始 Gate 响应，只读重建同一 Top2：

| 比较项 | 冻结 Gate / 原 R02 | 新策略离线决定 |
| --- | --- | --- |
| Gate 状态 | ADMITTED_WITH_UNCERTAIN_CANDIDATE | ADMITTED，globalRejected=false |
| Gate 内部严格校验通过 | Top1 | Top1 |
| 实际准入 | R02 因 GATE_UNCERTAIN_STOP 为空 | Top1 |
| 实际拒绝 | Top1 被全局清空，Top2 无支持 | Top2 仍拒绝 |
| 不确定性来源 | Top2 版本 UNCERTAIN 导致执行层全局停止 | Top2 VERSION_UNCERTAIN，仅 CANDIDATE 作用域 |
| Top1 严格证据校验 | CORE_SUPPORT / MATCH，三段真实引用支持 r1、r2 | 原引用、UTF-16 偏移、角色与覆盖诊断完全相同 |
| 整题充分性 | NOT_INDEPENDENTLY_VERIFIED | NOT_INDEPENDENTLY_VERIFIED |

Top1：`cbeb0d56-683a-41e7-b6c9-b0b9ec703597`；Top2：`504ae501-0222-4f8c-abce-7d3fec11d284`。这些 ID 只记录比较结果，实验准入代码没有题目、文档、候选 ID 或模型标签特判。

验证命令为 `python -I -B -X utf8 tools/rag-candidate-gate/run_offline.py`，本次完成 **89 个检查，0 failures、0 errors、0 skipped**：

| 验证 | 检查数 | 结果 |
| --- | ---: | --- |
| 独立候选安全 Mock | 54 | PASS：版本隔离、候选自身版本不确定、verdict/relation 不确定、缺失/拼接/标题/参考/URL 引用、缺失主要机制、跨候选及候选内部矛盾、非法结构/身份、全无支持、顺序及诊断不可变性 |
| H42-01 相同历史输入回放 | 10 | PASS：重建 Top2、绑定原请求、复验冻结决定、保留 Top1、拒绝 Top2、证据校验不变 |
| 未修改的 P4.2 离线回归 | 25 | PASS；另重放 8 个既有旧协议响应 |

编译完成；以上为本地 Java 校验、Mock 和保存响应回放，不是新增真实服务验证。未运行会重写冻结编译目录或访问数据库的 Maven 全套测试。

执行证据目录：`target/rag-candidate-gate/offline-3be9332b5de148798d8c9a42bdab39c3/`。`comparison.json` 包含完整并列决策和历史请求/响应 SHA；`tests.json` 包含结果与检查名称；`preservation-before.json` 与 `preservation-after.json` 逐文件一致。**11,767 个保护文件字节不变**，覆盖冻结工具、P4.2 历史结果与永久锁、现有源码/配置和 SQL；原 R02 artifact seal 再校验通过。

新增文件及职责：

- `tools/rag-candidate-gate/java/com/kun/aiinterview/knowledge/admission/experimental/CandidateLevelSemanticGate.java`：候选/全局作用域与 Java 最终决策。
- `tools/rag-candidate-gate/CandidateLevelGateOfflineTest.java`：独立安全 Mock 回归。
- `tools/rag-candidate-gate/SavedCandidateGateReplay.java`：同一原始历史输入并列回放。
- `tools/rag-candidate-gate/run_offline.py`：独立编译、受控离线运行、原回归隔离副本与文件保全校验。
- `tools/rag-candidate-gate/README.md`：使用方式和实验边界。
- 本报告：结果和验证范围。

本轮没有修改已有文件，没有新增模型调用，没有 MySQL/Milvus 访问或修改，没有 Git 暂存、提交或 push。生产默认 Dense TopK5、Rerank、Embedding、知识库和 P4.2 均保持原状。原 R02 仍是 STOPPED，原质量状态未改判；H42-02～08 没有新执行证据。

剩余边界：只有一条真实历史响应的局部保留行为得到验证，不能宣布整体语义质量提升。自然语言蕴含与未在响应中声明的隐含矛盾没有新增独立证明。

建议本次离线优化收口，保留实验实现及证据，后续回到前端、演示、文档与冻结工作。

```text
CANDIDATE_LEVEL_GATE=IMPLEMENTED
H42_01_OFFLINE_REPLAY=PASS_TOP1_ONLY
VALID_EVIDENCE_PRESERVED=TOP1
UNSUPPORTED_EVIDENCE_REJECTED=TOP2
SAFETY_REGRESSION=PASS_OFFLINE
OFFLINE_TESTS=89; FAILURES=0; ERRORS=0; SKIPPED=0
PRODUCTION_INTEGRATION=NOT_ENABLED
NEW_MODEL_CALLS=0
NEXT_ACTION=CLOSE_OFFLINE_OPTIMIZATION
```
