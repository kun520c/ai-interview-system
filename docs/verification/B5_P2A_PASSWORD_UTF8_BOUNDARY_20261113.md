# B5-P2-A：密码 UTF-8 字节边界修复验收

日期：2026-11-13（Asia/Shanghai）。仓库：`/Users/kun/Developer/projects/ai-interview-system`。

**结论：B5-P2-A 已实现、完成改前复现、边界验证、真实隔离 MySQL 集成、完整普通回归与打包；未暂存、提交或推送。** 密码上限统一为 72 UTF-8 字节，超限请求返回受控 400。没有截断、trim、Unicode 归一化或预散列密码；仍使用原 BCrypt 编码器。源码学习保持“待学习”。

## 1. 基线与变更保全

- 已读根 `AGENTS.md`、[B5-0 准入审查](B5_0_SYSTEM_READINESS_REVIEW_20261113.md)、[工程台账](../engineering/IMPROVEMENT_LEDGER.md)、[源码学习清单](../engineering/SOURCE_LEARNING_BACKLOG.md)。本批只处理 B5-P2-01，不处理 Embedding 日志或其他 B5 候选。
- 分支 `main`，本地 HEAD：`01ac774caff491b978dd2268e8b427355a64b97a`。GitHub 连接器 `fetch_commit(main)` 本日实时返回远端 main：`89b77dc082272e38cfbbecb4b57c37edae9ee74d`；本地缓存一致，祖先比较为 0 / 3。本轮没有 fetch 或修改引用。
- B4-A～C 已有本地提交保留；B4-D、B4-E、工程档案及其他已有工作区内容保留。入场以 `git ls-files -m -o --exclude-standard` 对文件正文建立 SHA-256 快照，Git diff 另存；最终除获授权追加的两份工程档案外，原有文件正文均保持不变，`mvnw` 可执行位保留。
- 原验收报告、B5-0 报告和真实测试计划未改写。没有 commit、push、stash、reset、clean 或自动暂存，无本批提交 SHA。远端核验只针对 main，不推断所有分支的发布状态。

原始证据目录：`/private/var/folders/xx/vxww8vxd1hz_f8szvp9th6j40000gn/T/b5-p2a-20261113-p3rjz9s7/`。下文日志、XML、执行器与快照均位于该目录。临时证据可能被操作系统清理，正式结论和可复现测试源码保存在仓库。

## 2. 实际源码链路与改前复现

三个 Controller 参数都有 `@Valid`，原密码字段使用 `@Size`。`@Size` 对 Java String 检查 UTF-16 code unit 数，不能替代 UTF-8 字节检查。原注册/登录及新密码为 8～72 个 code unit；当前密码仅非空白、最多 72 个 code unit。

`PasswordConfig.passwordEncoder()` 实际返回默认 `BCryptPasswordEncoder`。核对打包依赖及本机 `spring-security-crypto-6.5.11-sources.jar`：

- `BCryptPasswordEncoder.encode → BCrypt.hashpw(String, salt)` 使用 `getBytes(StandardCharsets.UTF_8)`；`BCrypt.hashpw(byte[], salt, for_check)` 在 `!for_check && length > 72` 时抛 `IllegalArgumentException("password cannot be more than 72 bytes")`。
- `matches → BCrypt.checkpw → hashpwforcheck` 使用兼容校验路径，不执行这个新密码长度检查。长密码不一定抛异常：不匹配时为 false；前 72 字节匹配时可为 true。
- 注册：`AuthController → AuthService.register → encode → insertUser`。超限发生在 INSERT 之前；异常进入 `GlobalExceptionHandler.handleException`，原返回 500。
- 改密：`UserController → UserService.changePassword → SELECT ... FOR UPDATE → 当前密码 matches → 新旧密码 matches → encode → updatePassword`。合法当前密码、不同的超限新密码在 encode 失败，事务没有更新密码。
- 登录：`AuthController → AuthService.login → matches → 状态检查 → JWT`。错误超限输入原返回既有业务 400；匹配前缀可能返回 200，不能把所有登录路径都写成 500。
- `AuthService` 已有 `@Validated` 及参数 `@Valid`，但手动构造服务可绕过代理；`UserService` 有 `@Validated`，改密 DTO 参数没有级联 `@Valid`。因此仅修改 Controller/DTO 不能覆盖所有直接服务调用。

先只新增测试，尚未修改生产代码，执行两轮改前验证，均为 **16 / 5 / 0 / 0，退出 1**。第一轮确认注册及匹配前缀登录；第二轮用 `assertAll` 保证注册失败后仍继续检查另外三个入口及数据库不变性。不是两组独立用例，不能相加。日志为 `backend-before.log`、`backend-before-all-paths.log`，对应 XML 独立归档。

改前观察（不记录明文测试密码或密码散列）：

- 8 单位 ASCII、72 字节 ASCII、72 字节中文、72 字节 emoji、72 字节组合字符及带首尾空格的有效密码：完整注册、登录、改密成功。
- 73 字节混合密码（字符长度小于 72）、75 字节中文、76 字节 emoji、75 字节组合字符：注册和新密码设置分别为 500；错误密码登录与错误当前密码为 400。共四个参数化测试失败，每项两个 500 断言；注册无新记录，改密散列、`password_changed_at`、`updated_at` 均不变。
- ASCII 73 字节：原 `@Size(max=72)` 已拒绝，四个入口均为 400。null、空串、空白和短密码仍按原参数或错误密码规则拒绝，不能归入新发现的 500。
- 合成已注册的 72 字节中文密码加超长后缀：实际 encoder.matches 为 true，原登录 200；第五个失败断言据此确认历史兼容语义。该测试没有读取正式账号。

## 3. 最小修复与合同

DTO 前后对比：注册/登录/新密码从 `@Size(min=8,max=72)` 改为 `@Size(min=PasswordPolicy.MIN_LENGTH)` 加 `@Utf8ByteSize(max=PasswordPolicy.MAX_UTF8_BYTES)`；当前密码从 `@Size(max=72)` 改为同一字节约束。原 `@NotBlank` 保留。UTF-8 超限使用明确的“不能超过72字节”消息，短密码消息为“不能少于8位”。

复用原 `Utf8ByteSize` 和 `Utf8ByteSizeValidator.isWithinLimit`，未修改通用注解、校验器、异常处理器或编码器。新增 `PasswordPolicy` 只提供密码专用常量及服务防护：

- `validate`：非空白、最多 72 UTF-8 字节、至少 8 个 Java String.length 单位，供注册、登录和新密码使用。
- `validateCurrent`：非空白、最多 72 UTF-8 字节，不新增 8 位下限，以保留原当前密码合同。
- 两个服务在数据库/编码器调用前执行上述检查，直接非代理调用也抛固定文案 `BusinessException`。HTTP DTO 校验通过既有 `MethodArgumentNotValidException` 映射 400，服务防护通过既有业务异常映射 400；没有将所有 `IllegalArgumentException` 泛化为客户端错误。

另核对实际 `hibernate-validator-8.0.3.Final-sources.jar`：`NotBlankValidator.isValid` 使用 `toString().trim().length() > 0`，不是 `String.isBlank()`。Java trim 只排除两端不大于 U+0020 的单位，不能套用 JavaScript trim 的 Unicode 空白集合。服务防护使用同样的判空表达式，前端 `isBlankPassword` 检查是否全为不大于 U+0020 的字符；它们仅做判断，不替换原密码。新增全角空格合法用例和控制字符空白拒绝用例，以保持既有合同。

前端 `authFormRules` 用 `TextEncoder.encode(value).byteLength` 判断 72 字节，最小长度仍为 JavaScript String.length；仅使用空白检查判断是否为空，不改写发送值。登录、注册、当前密码、新密码和确认密码输入框移除字符 `maxlength` 绑定，让超限输入完整保留并报错，避免浏览器在粘贴时静默裁掉尾部。原 `PasswordField` 通用能力未改；`useProfile` 移除无用途的字符上限导出。

密码内容不做截断、规范化或编码后再散列。原最小长度语义没有升级成 Unicode code point 或可见字素计数；例如补充平面字符仍占两个 UTF-16 单位。Java 和前端分别在原始字符串上计 UTF-8 字节。

`UserService.changePassword` 的事务注解、用户行锁、状态检查、当前密码验证、新旧密码不同要求、单条密码/时间 UPDATE、affectedRows 校验全部保留。JWT 与 `credential_version`、同秒失效、数据库状态作为可信来源的机制没有修改。Schema、SQL、依赖和部署配置无本批改动。

## 4. 本批文件清单

新增：

- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/common/validation/PasswordPolicy.java`：密码服务边界规则与常量。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/auth/PasswordSample.java`：合成边界样本，以枚举标签显示测试名，避免输出明文。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/auth/PasswordUtf8BoundaryTest.java`：DTO、非代理服务、实际编码器兼容行为及 DTO toString 测试。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/auth/PasswordUtf8BoundaryIntegrationTest.java`：真实 MySQL、MockMvc、实际 BCrypt、JWT、提交后数据库不变性和捕获日志验证。
- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B5_P2A_PASSWORD_UTF8_BOUNDARY_20261113.md`：本报告。

修改：

- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/auth/dto/RegisterRequest.java`：注册密码字节约束。
- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/auth/dto/LoginRequest.java`：登录密码字节约束。
- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/user/dto/ChangePasswordRequest.java`：当前/新密码字节约束。
- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/auth/service/AuthService.java`：register/login 的直接调用保护。
- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/user/service/UserService.java`：changePassword 的直接调用保护。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/auth/controller/AuthControllerTest.java`：更新长度错误文案断言，保留原测试。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/user/controller/UserControllerIntegrationTest.java`：更新新密码最小长度文案断言，保留原测试。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/auth/validation/authFormRules.ts`：共享 UTF-8 预校验和字节消息。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/auth/views/LoginView.vue`：移除密码字符 maxlength。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/auth/views/RegisterView.vue`：移除密码/确认密码字符 maxlength。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/profile/views/ProfileView.vue`：移除三个密码输入的字符 maxlength。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/profile/composables/useProfile.ts`：移除已不用的 passwordMax 导出，保留调用共享校验器。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/tests/authFormRules.spec.ts`：ASCII、中文、emoji、组合字符、空白和当前密码下限。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/tests/LoginView.spec.ts`：超限拦截、完整保留输入、合法边界原样发送。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/tests/RegisterView.spec.ts`：超限拦截、不裁剪、72 字节注册提交。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/tests/ProfileView.spec.ts`：分别校验当前/新密码，保留短当前密码提交。
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/IMPROVEMENT_LEDGER.md`：追加 B5-P2-A 实际结果。
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/SOURCE_LEARNING_BACKLOG.md`：新增 3 条待学习任务，更新旧候选的后续状态。

## 5. 环境、命令与实际结果

本机实测 macOS 26.7.1 ARM64、Temurin Java 21.0.13、Maven Wrapper 3.9.16、Node 24.22.1。使用本轮新建的 `ai-interview-b5-p2a-20261113-mysql`，MySQL 8.0.45，首轮 127.0.0.1:63375；最终补充兼容性回归重新创建同一本批专用容器，端口为 127.0.0.1:63999，`/var/lib/mysql` 为 tmpfs、时区 +08:00，仅有 `ai_interview_test` 13 张业务表及系统库。DDL 仅在临时内存副本替换库名，没有改仓库 SQL、连接正式 ai_interview 或 evaluation。

`run.py` 清除继承的 `RUN_REAL_*`、`MILVUS_*`、`SPRING_*`、模型配置及旧 DB/JVM/Maven 覆盖，然后只向 Maven 子进程注入临时 MySQL 和本机 Mockito javaagent；`DEEPSEEK_ENABLED=false`。`MILVUS_ENABLED` 与所有真实服务 opt-in 均 unset，`test` Profile 和 `EnvironmentIsolationInitializer` 原样保留。未修改 pom、配置或测试门禁。开始时沙箱内 Docker 只读访问受限，之后在获准环境执行；不是业务测试失败。

T/F/E/S 为测试总数/失败/错误/跳过；总数含跳过，不跨轮相加。每轮 Maven 先归档已有 Surefire，再复制本轮 XML。

1. **改前首次复现**：`python3 <证据目录>/run.py backend-before sh mvnw -B -ntp -Dtest=PasswordUtf8BoundaryIntegrationTest test`，**16/5/0/0，退出 1**。当时只有新测试，生产代码仍为改前版本。
2. **改前全部入口复现**：同命令使用标签 `backend-before-all-paths`，增加独立断言后 **16/5/0/0，退出 1**。四组超限编码各两个 500、匹配前缀登录一个 200，属于预期红测，不是环境错误。
3. **修复后聚焦回归**：标签 `backend-focused`，底层命令见下；**127/0/0/0，12 类，退出 0**。当时边界集成为 16 项；之后补入历史短当前密码真实改密场景，在全量中验证。
4. **完整普通 Maven**：首轮标签 `backend-full` 为 **1775/0/0/19，138 类，退出 0**。最终核对实际 Hibernate Validator 的空白合同并补充 Unicode/控制字符后，标签 `backend-final-focused` 运行 PasswordUtf8BoundaryTest、PasswordUtf8BoundaryIntegrationTest、ConcurrentPasswordChangeIntegrationTest，**49/0/0/0，退出 0**；随后 `python3 <证据目录>/run.py backend-final-full sh mvnw -B -ntp test` 为 **1780/0/0/19，138 类，退出 0**。1761 项非跳过；`backend-final-full-reports/` 与 `backend-final-full-suites.json` 为最终计数依据。
5. **后端打包**：初轮 `backend-package` 和最终 `backend-final-package`，`sh mvnw -B -ntp -DskipTests package`，**退出 0，测试计数不适用**。JAR 检查确认新 PasswordPolicy 与 crypto 6.5.11 存在、无 application-local.yaml 或 .env。
6. **前端聚焦**：`npm run test:unit -- authFormRules.spec.ts LoginView.spec.ts RegisterView.spec.ts ProfileView.spec.ts`，**42/0/0/0，4 文件，退出 0**。
7. **前端全量**：`npm run test:unit`，初轮 **332/0/0/0**；补充不换行空格测试的中间轮 `frontend-final` **335/0/0/0**；与实际 Hibernate Validator 判空完全一致后的最终轮 `frontend-final2` **336/0/0/0**。三轮均36文件、退出0，无未处理异常。
8. **前端静态/构建**：`npm run lint` 与 `npm run build` 均**退出 0**；build 内包含 `vue-tsc -b`，类型检查通过，没有另行重复运行独立 typecheck。首轮及两次前端源码调整后的 lint/build 均通过，对应 `frontend-*`、`frontend-final-*`、`frontend-final2-*` 日志。测试计数不适用。
另有临时 `BlankProbe.java` 诊断 Java String.isBlank 的4项观察，退出0；该方法不等同本项目实际 @NotBlank 实现，未用此探针替代最终DTO/服务测试。

9. **差异与档案检查**：首轮临时检查器把学习模板标题也算作条目，得到58并断言退出1；修正检查器为只计正式编号后为57，未改动正式条目或弱化测试。最终`git diff --check`、`git diff --cached --check`、新增文件尾空白/文档路径检查及原文件保全检查均**退出 0**；不是功能测试。

聚焦底层命令：

```sh
sh mvnw -B -ntp -Dtest=PasswordUtf8BoundaryTest,PasswordUtf8BoundaryIntegrationTest,Utf8ByteSizeValidatorTest,AuthServiceTest,AuthServiceUniquenessTest,AuthControllerTest,UserServiceTest,ChangePasswordRequestTest,UserControllerIntegrationTest,ConcurrentPasswordChangeIntegrationTest,JwtTokenServiceTest,JwtAuthenticationFilterTest test
```

前端执行器使用同一 `run.py` 的 frontend 标签，工作目录为仓库 `frontend/`；完整命令参数、耗时及退出码保存在各 `*-summary.json`。构建文件在 ignored `target/ai-interview-system-0.0.1-SNAPSHOT.jar` 和 `frontend/dist/`，没有部署。

## 6. 验收覆盖与证据边界

- 新增单测 **29/0/0/0**：7 种合法 DTO、10 种不合法 DTO、10 组不经代理的服务调用（断言 Mapper/encoder/JWT 零交互）、实际 BCrypt 行为、DTO toString 不含密码。Mockito 只用于直接服务边界，不能据此宣称 SQL 通过。
- 新增集成 **19/0/0/0**：7 种合法完整流程、10 种非法输入的四个 HTTP 边界、超限匹配前缀拒绝、历史短当前密码迁移。使用真实 BCrypt/安全链/业务服务/MyBatis/MySQL，MockMvc 模拟 HTTP；没有外围测试事务，观察实际提交后的数据库状态。
- 72 字节被接受、73 字节混合输入和更多字节被拒绝；中文、补充平面 emoji、组合字符均覆盖。成功请求校验原字符串可 matches，前端检查传参完全相同，首尾空格不被 trim。未增加密码归一化策略。
- 非法注册无用户记录；失败改密散列、`password_changed_at`、`updated_at` 不变，原 token 仍可访问；成功改密后原 token 401、新登录 token 200，再次改密立即撤销第二张 token。
- 既有 AuthController 14、AuthService 8、UserControllerIntegration 12、JwtAuthenticationFilter 32、JwtTokenService 6、ConcurrentPasswordChangeIntegration 1 均 **F/E/S=0**。错误密码、重复账号/邮箱、禁用账号、相同新旧密码、同秒 JWT 失效和 MySQL 行锁单胜者回归均保留。真实并发测试仍观察 MySQL 锁等待，不依赖新的模拟锁。
- 新集成逐响应检查公开 JSON，捕获 Logback 格式化消息及 Throwable 栈；没有测试明文或散列。改前两轮、初次聚焦/全量、最终聚焦/全量六轮后端日志另做合成样本/BCrypt 模式扫描，命中均为 0，见 `test-log-scan.json`。测试报告使用样本标签，关闭 MockMvc 请求自动打印。此结论限于测试路径，不是整个系统或生产日志的全面审计。
- 前端视图测试使用 jsdom/API mock，验证本地拦截、输入完整性与实际提交参数；没有执行真实浏览器 E2E。
- 19 项门禁跳过：RealEmbeddingSmokeTest 1、RealMilvusVectorStoreSmokeTest 1、KnowledgePersistenceIntegrationTest 10、RealKnowledgeDocumentProcessingSmokeTest 2、RealKnowledgeDocumentProcessingFailureIntegrationTest 2、RealMilvusSchemaValidationSmokeTest 2、AiSystemBaselineEvaluationTest 1。它们不是失败，也不是本批真实第三方集成成功证据。

收尾确认本批 `b5pwd_` 测试用户剩余 0；删除本轮唯一新建的 tmpfs MySQL 容器和临时凭据文件，确认容器不存在。没有删除共享容器或数据卷。结果见 `cleanup.json`；运行开关没有写入持久配置。

## 7. 历史兼容、遗留事项与停止点

当前 crypto 6.5.11 不会新编码超过 72 字节的密码；原注册/改密 500 没有留下这类新散列。但不能据此推断过去所有版本或迁移数据均符合合同：旧 BCrypt 校验允许超长匹配前缀，已用合成样本确认；数据库 BCrypt 散列本身不记录原输入字节数，无法可靠筛出受影响账号。

本次明确将超过 72 字节的登录与当前密码输入统一拒绝为 400。因此若历史系统曾接受这类密码，使用原完整超长输入的用户可能受影响。没有扫描正式用户库、导出散列或批量修改用户密码；也没有新增截断 fallback 或放宽当前密码验证。若以后证实存在受影响用户，需独立设计经授权的账号恢复/重设方案；本轮没有实施密码重置模块。非空白短当前密码兼容仍保留，登录的原有 8 单位下限也未放宽。

B5-0 的 Embedding 日志风险和其他候选维持原状态，本报告不替代全系统准入结论。源码学习新增 L-B5P2A-01～03，全为**待学习（未确认）**；测试通过不代表开发者已掌握。

本批在代码、回归及三份工程档案更新后结束；不继续其他业务改造，不自动暂存、提交、推送。建议下一步由开发者按学习条目复述计数单位、编码/校验差异及事务/JWT 验证证据。
