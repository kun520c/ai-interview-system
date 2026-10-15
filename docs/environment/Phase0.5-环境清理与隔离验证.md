# PHASE 0.5 ENVIRONMENT CLEANUP

日期：2026-10-05，Asia/Shanghai（UTC+08:00）。最终数据复核：2026-10-05T01:35:58.995461+08:00

**PHASE_0_5_OVERALL: PASS。** 已精确清除 HIGH confidence 的 baseline/P9C 数据，正常 local、test 与 evaluation 已隔离。未进入 Phase 1。

本阶段依据 [DATA_ENVIRONMENT_AUDIT.md](数据环境审计.md) 与 [DATA_ENVIRONMENT_AUDIT_CLOSURE.md](数据环境审计-闭环验证.md)，两份原报告均未改写。

**Milvus 计数必须区分：local 当前可见 entity_count=0（Strong count(*)/完整标量枚举/8 UUID精确查询），但 get_stats.rowCount=8。** 后者仍计入删除的 segment 行，未将它伪报为0，也未为改变统计执行 compact/flush/rebuild/drop local。实体清理结论以真实可见数据为准，详见第3/10节。

## 1. Cleanup Manifest Summary

业务 DELETE 前重新 SELECT 全表、真实 FK 与 Milvus 标量实体，生成 [PHASE_0_5_CLEANUP_MANIFEST.md](PHASE_0_5_CLEANUP_MANIFEST.md)。精确 PK 与完整行摘要在 [PHASE_0_5_CLEANUP_SNAPSHOT.json](PHASE_0_5_CLEANUP_SNAPSHOT.json)，没有存储正文或密码。

- MySQL：13 张核心表共495行；baseline + P9C，全为原审计 HIGH 项。
- Milvus：8 UUID；7 baseline + 1旧P9C；初始完整可见集合恰好为这8个。
- 15条真实FK全部RESTRICT；无未纳入清单的子行指向目标父行。
- 4个 frontend_e2e 用户（MEDIUM）与非核心 users 表5行（LOW/UNKNOWN）排除，全部保留。

清单 canonical snapshot SHA-256：`a2f0a829eb0a6cf35a79ec7fa53fefd21ce89130ce0b095f77dbdebef2a9c398`。实际 DELETE 使用明确枚举的 PK IN 集合；ID仅辅助核对，不使用ID区间、account/source通配符批量删除。

归因根：baseline用户具有 evalcorpus/evalexcellent/evalmedium/evalpoor 账号与 Baseline 用户名；文档542–547为 source=baseline-evaluation-20261004 且含EVAL_BASELINE标题。P9C账号逐项精确确认；题库knowledge_point含P9C_E2E；document485为source=phase-9c-local-fixture。所有子表由真实user_id/session_id/question_id/answer_id/document_id/retrieval_batch_id关系重新定位。

## 2. Actual MySQL Deletions and Before/After Counts

连接：localhost:3306/ai_interview；MySQL8.0.45；root@localhost；server_uuid=6ccac806-1bb1-11f1-b45f-107c61ba59a5。密码：***REDACTED***。

| Table | Before | Actual deleted | After | Primary keys / relation |
|---|---:|---:|---:|---|
| answer_evaluation | 48 | 48 | 0 | baseline535–576；P9C469–474；answer_id/main question |
| interview_answer | 48 | 48 | 0 | baseline1376–1417；P9C1218–1223；interview_question_id |
| interview_question | 56 | 56 | 0 | baseline2515–2560；P9C2241–2250；session_id |
| interview_report | 6 | 6 | 0 | baseline101–105；P9C88；session_id |
| interview_session | 8 | 8 | 0 | 2463–2468 baseline；2185/2186 P9C；synthetic user_id |
| knowledge_chunk | 8 | 8 | 0 | 484–490 baseline；443 P9C；document_id+8 UUID |
| knowledge_document | 7 | 7 | 0 | 542–547 baseline；485 P9C；source/title |
| question | 6 | 6 | 0 | 6778–6783；P9C_E2E marker |
| question_scoring_point | 13 | 13 | 0 | 2203–2212、2215–2217；P9C question_id |
| rag_hit_log | 221 | 221 | 0 | baseline472–686（215）；P9C422–427（6）；answer/batch/chunk FK |
| rag_retrieval_batch | 49 | 49 | 0 | 49个UUID，逐项见Manifest；实际answer_id关联 |
| user | 14 | 10 | 4 | 10888–10894 baseline7；10177–10179 P9C3；精确账号复核 |
| user_weakness | 15 | 15 | 0 | 412–426；合成user_id |
| users | 5 | 0 | 5 | 未列入HIGH清单；101–105全部保留 |

### Transaction and FK verification

COMMIT时间：2026-10-05T01:09:43.383125+08:00。在同一mysql.exe连接执行START TRANSACTION，完整SELECT FOR UPDATE后全部行摘要/metadata必须与Manifest一致。随后按真实FK子→父顺序删除：

`answer_evaluation → interview_report → question_scoring_point → rag_hit_log → user_weakness → knowledge_chunk → rag_retrieval_batch → interview_answer → knowledge_document → interview_question → interview_session → question → user`

interview_question的parent_question_id自引用另按叶节点分组删除，先FOLLOW_UP后MAIN。未关闭FOREIGN_KEY_CHECKS；未使用TRUNCATE、重建local schema/table、重置local AUTO_INCREMENT。

COMMIT前完整SELECT验证全部目标PK消失、所有保留行摘要完全相同、全部物理FK无dangling，并核实current_interview_question_id、answer_evaluation.retrieval_batch_id逻辑引用；验证通过后才COMMIT。COMMIT后再次SELECT一致。

本次Manifest与实际数据一致，没有执行ROLLBACK。执行器遇到行/标记/数量/FK/摘要差异会ROLLBACK并停止，不会扩大删除范围。

最终最小测试之后再次全表复核：baseline/P9C用户、文档、切片、题库/评分点、8 session及答案/评价/报告/RAG/弱项均无残留，15条FK的dangling数为0。

## 3. Actual Milvus UUID Deletions

仅向default/knowledge_chunk_vectors_local发出一次精确`vector_id in [8 UUID]`删除；没有按document_id或其他宽filter删除。删除前再次复核完整实体集合、document/index/version及collectionID/schema/index与Manifest相同。

| UUID | MySQL chunk | document_id | chunk_index | Provenance | Current exact query |
|---|---:|---:|---:|---|---|
| 197d0156-662d-4037-8327-8867ac554eb7 | 488 | 545 | 1 | baseline HIGH | ABSENT |
| 24a1e5a2-7dfe-42a0-9045-ba7f06fa584b | 484 | 542 | 1 | baseline HIGH | ABSENT |
| 2dcbdde0-023a-46e9-a179-6a04b5fe83a8 | 485 | 542 | 2 | baseline HIGH | ABSENT |
| 40d1c663-d60b-4fa5-8c67-511c7d5e95ee | 490 | 547 | 1 | baseline HIGH | ABSENT |
| 5da54916-1636-4d40-a866-8a96d1bd5917 | 489 | 546 | 1 | baseline HIGH | ABSENT |
| 98b7ec72-d5fc-4d4c-8172-50b10e9d1838 | 443 | 485 | 1 | P9C HIGH | ABSENT |
| a19ebc36-0eee-4726-8dd6-2501d5cded4c | 486 | 543 | 1 | baseline HIGH | ABSENT |
| a38aeb91-3156-44a2-98b3-0f6b2f897790 | 487 | 544 | 1 | baseline HIGH | ABSENT |

| Collection | ID | Live before | Live after | get_stats after | Schema/index |
|---|---|---:|---:|---:|---|
| knowledge_chunk_vectors_local | 468137482006301501 | 8 | 0 | 8 | 5字段、1024维、vector_id PK、AUTOINDEX/COSINE |
| knowledge_chunk_vectors_evaluation | 469534467320352808 | new empty | 0 | 0 | 5字段、1024维、vector_id PK、AUTOINDEX/COSINE |
| knowledge_chunk_vectors_test | 469534467320352840 | new empty | 0 | 0 | 5字段、1024维、vector_id PK、AUTOINDEX/COSINE |

local collection ID仍为468137482006301501；5字段、autoID=false、dynamic field=false、max_length、维度和vector索引定义均保持原状。未drop/recreate local collection，未重建索引。

删除后Strong scalar count(*)=0、完整标识枚举=[]、8 UUID精确查询=[]；因此可见orphan=0、残留UUID=0。当前MySQL chunk亦为0，missing映射=0；没有相同document_id/chunk_index多PK。未读取embedding浮点数组，未执行similarity search或重新embedding。

最初执行器误把get_stats也归零设为完成条件，等待50秒后退出1。此时逻辑删除已经成功。随后查证统计API语义，改用只读Strong查询完成验证，**没有重发DELETE**。原API删除响应payload未另存；通过执行器成功通过HTTP200/code0检查及后续空集合核实操作完成，不伪造deleteCount返回值。

官方说明统计行数可能仍包含删除行；本次原样保留rowCount=8，不宣称物理segment已回收。参考[Milvus statistics API](https://milvus.io/api-reference/java/v2.5.x/v1/Collection/getCollectionStatistics.md)、[Milvus维护者说明](https://github.com/milvus-io/milvus/discussions/35631)。Strong请求字段按[Milvus v2.6.20服务端定义](https://github.com/milvus-io/milvus/blob/v2.6.20/internal/distributed/proxy/httpserver/request_v2.go)核对。

## 4. Normal Local Final Configuration

```text
Run Configuration: AiInterview-Local-Real
Profile: local
MySQL: localhost:3306/ai_interview
Milvus: http://127.0.0.1:19530
Milvus database: default
Milvus collection: knowledge_chunk_vectors_local
Dimension: 1024
Credentials: ***REDACTED***
```

修改保存IDEA的MILVUS_COLLECTION_NAME；local YAML原本已指向canonical local collection，保持原样。IDEA DB_URL尾随空格已在此次授权配置整理中移除。未启动正常应用来验证；采用保存配置、既有Boot绑定证据及当前metadata交叉核实目标。

local的题库、评分点、知识文档/切片、会话/答案/评价/报告/RAG/弱项表全部为空。保留4个MEDIUM用户和5行非核心LOW/UNKNOWN数据；“EMPTY_AND_READY”指题库、知识库和面试数据域为空，不声称整个schema每张表都是0行。

## 5. Smoke Collection Retirement

删除前确认default/knowledge_chunk_vectors_smoke_20260803的collectionID=468122425520555425、Strong可见entity=0、stats=0；src/main、src/test、.idea和.mvn均无该名称的运行引用，且无项目Java进程。

已先将IDEA正常目标改为local，再单次DROP这个空smoke collection；retiredAt=2026-10-05T01:22:42.634469+08:00。最终default清单为local/evaluation/test三个collection，不含smoke。

历史审计、Manifest和一次性清理执行器保留旧名称作为取证/清理对象记录；它们不是应用或测试运行目标。没有仍在运行的真实引用，SMOKE_COLLECTION_RETIRED=PASS。

## 6. Evaluation Final Configuration and Fail-fast

```text
Profile: evaluation
MySQL: localhost:3306/ai_interview_evaluation
Milvus: http://127.0.0.1:19530
Milvus database: default
Milvus collection: knowledge_chunk_vectors_evaluation
Dimension: 1024
Credentials: ***REDACTED***
```

新增application-evaluation.yaml。新schema为当前local真实13张核心表的schema-only复制，15条FK一致，没有复制用户、题库、文档、会话或其他业务行。[ISOLATED_SCHEMAS.sql](ISOLATED_SCHEMAS.sql)记录DDL。新collection仅创建相同5字段及AUTOINDEX/COSINE索引，0 entity。未修改local表结构。

AiSystemBaselineEvaluationTest改为@ActiveProfiles("evaluation")，原EnabledIfEnvironmentVariable与Assumptions两层保护保留；执行入口还检查实际SELECT DATABASE()与collection名称。runner明确选择evaluation目标；普通regression/focused则选择test目标。

EnvironmentIsolationInitializer在DataSource/Milvus beans与CollectionInitializer之前检查最终绑定值；evaluation禁止与local/test混用，并严格要求ai_interview_evaluation / knowledge_chunk_vectors_evaluation。指向ai_interview或knowledge_chunk_vectors_local及其他冲突目标立即FAIL FAST。

另检查Hikari JDBC URL/catalog/schema覆盖，拒绝可切换target的connection-init-sql。错误不打印可能含密码的JDBC URL。runner提供ValidateEnvironmentOnly，先校验再读取AI provider凭据/启动Maven；进程变量在finally恢复，不永久修改Windows用户/系统变量。

配置级正向evaluation/test两项及拒绝local evaluation DB、local evaluation collection、local test DB三项共5个runner用例均PASS，未启动AI baseline。

evaluation/test可分别提供EVALUATION_DB_USERNAME/PASSWORD、TEST_DB_USERNAME/PASSWORD，缺省沿用已有DB_USERNAME/PASSWORD。本阶段隔离schema/collection及启动配置，没有新增数据库账号或宣称MySQL GRANT权限隔离。所有真实密钥仍在原有本地环境，不写入新profile或报告。

历史golden/corpus/recovery以旧合成ID为基础，不是新空evaluation环境的已准备业务数据；本阶段未重建dataset、未重新评测、未复制合成数据到local。

## 7. Test Isolation

| Test type | Profiles | MySQL | Milvus |
|---|---|---|---|
| Ordinary Mapper/transaction/API tests | test或现有local,test | ai_interview_test | knowledge_chunk_vectors_test；默认disabled |
| Guarded real-service tests | local,test-real | ai_interview_test | knowledge_chunk_vectors_test；原opt-in保护 |
| AI baseline | evaluation | ai_interview_evaluation | knowledge_chunk_vectors_evaluation；原opt-in保护 |

application-test.yaml现在独立datasource，TEST_DB_URL缺省使用ai_interview_test，不再依赖generic DB_URL的local目标；原test JWT/embedding配置与milvus/deepseek disabled保持。application-test-real.yaml只覆盖真实测试的数据落点，local提供原有真实服务凭据。

6个guarded smoke/integration测试改用local,test-real；3个非Boot ContextConfiguration显式在ConfigData加载后调用同一guard。测试classpath marker拒绝仅local或无隔离profile的Boot启动。审计未发现绕过配置的直接硬编码生产数据库写入客户端。

IDEA两项既有真实JUnit配置改用local,test-real，移除保存的RUN_REAL_*开关；并未去掉条件或Assumptions保护。新test schema含13核心表、新test collection为空。最小Mapper测试实际在test库写入并自行清理；随后test表仍全部为0，local保留行摘要不变。

## 8. Retained MEDIUM / LOW / UNKNOWN Data

| Table | PK | Account / provenance | Confidence | Action |
|---|---:|---|---|---|
| user | 10173 | frontend_e2e_994321 | MEDIUM | 保留，完整行摘要未变 |
| user | 10174 | frontend_e2e_579774 | MEDIUM | 保留，完整行摘要未变 |
| user | 10175 | frontend_e2e_b39281 | MEDIUM | 保留，完整行摘要未变 |
| user | 10176 | frontend_e2e_iso_bad04b | MEDIUM | 保留，完整行摘要未变 |
| users（非核心） | 101–105（5行） | 缺少可靠自动化运行归因 | LOW/UNKNOWN | 全部保留，完整行摘要未变 |

未因frontend_e2e标记升级为HIGH，也未因核心业务表已空就顺手清除非核心users表。未删除任何MEDIUM/LOW/UNKNOWN行。

## 9. Files Created or Modified

| File | Change / purpose |
|---|---|
| src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java（新增） | 解析目标guard；初始化基础设施之前fail fast |
| src/main/resources/META-INF/spring.factories（新增） | 注册启动guard |
| src/main/resources/application-evaluation.yaml（新增） | evaluation独立目标；仅凭据占位符 |
| src/test/resources/application-test.yaml（修改） | 独立test datasource/collection；保留原测试服务隔离 |
| src/test/resources/application-test-real.yaml（新增） | guarded真服务测试的数据隔离 |
| src/test/resources/META-INF/ai-interview-test-runtime（新增） | 测试classpath要求隔离profile |
| src/test/java/com/kun/aiinterview/config/EnvironmentIsolationInitializerTest.java（新增） | 正确配置、危险覆盖和真实YAML解析保护用例 |
| src/test/java/com/kun/aiinterview/config/TestProfileExternalServiceIsolationTest.java（修改） | 验证实际SELECT DATABASE()=ai_interview_test及test collection |
| src/test/java/com/kun/aiinterview/evaluation/AiSystemBaselineEvaluationTest.java（修改） | 独立evaluation profile与实际目标断言 |
| RealEmbeddingSmokeTest.java、RealMilvusVectorStoreSmokeTest.java、RealMilvusSchemaValidationSmokeTest.java、KnowledgePersistenceIntegrationTest.java、RealKnowledgeDocumentProcessingSmokeTest.java、RealKnowledgeDocumentProcessingFailureIntegrationTest.java（src/test/java下，修改） | local,test-real；非Boot context补guard；双层opt-in保持 |
| .idea/workspace.xml（ignored，修改） | canonical local collection；JUnit隔离；删除保存opt-in开关；修正URL尾随空格；不复制凭据 |
| docs/evaluation/scripts/run-baseline.ps1（修改） | 独立评测/test runner，配置dry-run及冲突拒绝；恢复临时环境 |
| docs/evaluation/README.md、AGENTS.md（修改） | 将当前运行规则与隔离目标一致；历史验证记录保留 |
| docs/environment/README.md（新增） | 环境使用规则与边界 |
| docs/environment/scripts/phase05_data_cleanup.py（新增） | 精确清单、事务执行和只读删除后验证；防重放 |
| docs/environment/PHASE_0_5_CLEANUP_MANIFEST.md、PHASE_0_5_CLEANUP_SNAPSHOT.json、PHASE_0_5_DATA_RESULTS.json、ISOLATED_SCHEMAS.sql（新增） | 删除前清单、摘要证据、实际结果与新隔离DDL |
| docs/environment/PHASE_0_5_MILVUS_POST_DELETE_OBSERVATION.json、PHASE_0_5_RUNNER_VALIDATION.json、PHASE_0_5_TEST_RESULTS.json、PHASE_0_5_FINAL_VERIFICATION.json（新增） | 脱敏metadata/配置/测试/最终数据证据 |
| PHASE_0_5_ENVIRONMENT_CLEANUP.md（新增） | 本最终报告 |

未修改src/main/resources/application.yaml、ignored application-local.yaml、现有业务Java实现、Mapper XML或local表结构。既有其他工作区改动通过原SHA-256复核未被本阶段改写。本agent未执行git add/commit/push；当前新增/修改项仍需owner自行审阅。

原审计报告 SHA-256：

```text
DATA_ENVIRONMENT_AUDIT.md: 5716abc5a1a16bdd39c79c3a425c66651f202cd414916dfe879a20e621641503
DATA_ENVIRONMENT_AUDIT_CLOSURE.md: cf79c939158bccd8e6f2263ceb76253fcf0b1c234d8fd660817022e2e13395b8
```

## 10. Verification Results and Gate

| Verification | Tests/checks | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| 首次最小Maven/Surefire：guard、配置隔离、DB连接、UserMapper和7个guarded类 | 49 | 0 | 0 | 19 | BUILD SUCCESS；30项实际执行 |
| 补充Hikari schema/init SQL覆盖后的最终guard测试 | 23 | 0 | 0 | 0 | BUILD SUCCESS；只读配置/unit |
| runner配置dry-run/危险目标拒绝 | 5 | 0 | 0 | 0 | PASS；无Maven/AI调用 |
| 最终MySQL三个schema实际FK检查 | 45条FK关系 | 0 dangling | 0 | — | PASS |
| local保留行完整摘要 | 9行 | 0差异 | 0 | — | PASS |
| Milvus三个collection Strong完整scalar/count | 3 | 0可见entity | 0 | — | PASS；local raw stats仍8 |
| smoke运行引用/当前inventory | 0运行引用、0目标collection | 0 | 0 | — | PASS |

最小测试首次命令使用mvnw -B -ntp -Dtest=EnvironmentIsolationInitializerTest,DatabaseConnectionTest,TestProfileExternalServiceIsolationTest,UserMapperTest及7个guarded类 test；补充仅-Dtest=EnvironmentIsolationInitializerTest。没有执行全量回归、AI baseline、embedding、DeepSeek、Jev、RAG效果测试、E2E或前端测试。guarded skipped是未执行真实AI/真服务测试的证据，不宣称其真实业务验证通过。

真实验证：MySQL清理事务、test datasource/Mapper、Milvus精确清理与metadata、隔离schema/collection和最终完整标量数据；编译通过。未创建新的正式题库、知识文档或interview session。

关键环境/目标清理状态没有剩余UNKNOWN。仍未验证正常应用的完整业务启动/E2E（本阶段禁止）；没有物理回收Milvus tombstoned segment；未补齐旧LOW/MEDIUM记录来源、历史未留存delete响应payload或完整事务轨迹。这些均不伪装为已验证事实。

```text
PHASE_0_5

MYSQL_SYNTHETIC_DATA_REMOVED: PASS
MILVUS_SYNTHETIC_DATA_REMOVED: PASS
LOCAL_CANONICAL_ENV_READY: PASS
SMOKE_COLLECTION_RETIRED: PASS
EVALUATION_ENV_ISOLATED: PASS
TEST_ENV_PROTECTED: PASS
REAL_DATA_ENV_EMPTY_AND_READY: PASS

PHASE_0_5_OVERALL: PASS
```

MILVUS_SYNTHETIC_DATA_REMOVED与REAL_DATA_ENV_EMPTY_AND_READY依据当前可见业务实体为0，不把get_stats.rowCount=8当作删除后的真实entity总数；原始统计差异完整披露。Local核心题库/RAG/面试域为空，保留MEDIUM/UNKNOWN用户数据符合本阶段边界。

**到此停止，等待Commander人工裁决。不进入Phase 1。**
