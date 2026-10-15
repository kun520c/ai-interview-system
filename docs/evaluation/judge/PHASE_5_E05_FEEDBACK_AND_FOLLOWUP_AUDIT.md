# E05 反馈与FOLLOW_UP专项取证（Gate A/C）

结论：E02-OFF_TOPIC确定错误归因；E02-BOUNDARY确定添加候选未提到的“双端”场景，其作为strengths的语境支持越界归因，但也存在“把局部场景泛化总结”的解释，交Commander复核。原E05记录2条Jev-mode硬矛盾保持不变。baseline M04-EXCELLENT确定假遗漏；H09-EXCELLENT有未限定上下文的事务回滚过度断言。H09-REPAIR违反原已存在的ALL MAIN约束，不能称原Prompt未要求全量。

本报告只读取冻结合成候选文本和捕获响应，无私有用户回答、凭证、真实模型调用。文本约束已经实现并做作用域/请求参数测试，**没有离线证据能证明下一次生成不再矛盾。**

## 逐字反馈与来源

### E02-OFF_TOPIC：确定错误归因

候选全文：`我们接口返回数据一般会先定义 DTO，再加分页参数，查库只取必要字段。我觉得还应该统一异常码，前端用相同结构处理失败，这样维护起来比较方便。`

实际Jev-mode missingPoints[3]：`未结合随机访问比例、修改位置、是否已有游标等实际访问模式来选择集合，而是机械套用增删多就用 LinkedList`

回答未谈ArrayList/LinkedList选择，也未主张增删多就用LinkedList；“而是机械套用”是对不存在观点的明确归因，confirmed。reference/RAG含“不能简单说增删多就一定用LinkedList”的正确知识，但不能拿它当候选错误观点。误把标准中的常见误区转成候选实际错误是likely机制；模型内部来源未知，不能证明RAG直接导致。

### E02-BOUNDARY：缺失场景已确认，总结语义仍需复核

候选选择部分：`我会用 ArrayList 存经常索引读取的列表，用 LinkedList 的迭代器处理确实集中在当前位置的删插，不会认为任何一次插入都让链表更快。`

实际strengths[2]：`结合实际场景给出选择思路：频率索引读取用 ArrayList，局部或双端操作用 LinkedList 迭代器，避免机械地认为‘增删多就用 LinkedList’。`

候选没有“双端操作”（confirmed）；RAG实际含“已有游标的局部修改或双端使用可考虑LinkedList”（confirmed）。strengths以“给出选择思路”把双端场景连同已回答的局部场景一起表扬，按严格候选忠实度属于越界添加。它也可能是生成器对局部修改的泛化建议（likely、可争议），而非主动声称逐字引用。技术上的双端适用建议本身不错误，但应标明补充建议，不能作为已回答优点。原因和是否直接复制RAG均unknown。该点coverage与gold一致、quote合法，不能抵消散文归因问题。

### baseline M04-EXCELLENT：确定事实假遗漏

候选原文：`写之前的普通写也能经这个发布过程被读线程看到，它不是把所有代码随意排成一个全局顺序。`

实际missingPoints[1]：`未强调 volatile 的普通写可见传递是经 volatile 发布，而非全局顺序`

同一baseline把2512 covered=true，evidence也引用上述原文，再在missingPoints称其未强调，confirmed硬矛盾。不能通过把coverage改为false或删gold解决；新Judge system只加强“已在MAIN/FOLLOW_UP正确说明的机制不得再称遗漏”。

### baseline H09-EXCELLENT：事务错误处理应分前提

实际correction：`整体回答方向正确，核心机制基本齐全。进一步可完善两点：一是处理唯一键冲突时要注意，冲突会使当前事务整体回滚，应先结束失败事务，再读取或恢复已存在的请求状态，不能继续在同一事务中执行扣减；二是对并发同 requestId 请求和处理中状态，应设计明确的等待、轮询或返回处理中的协议，并配合有限重试与退避，确保异常路径下也不会重复扣减。`

候选说的是“任何关键写失败就整体回滚”的业务事务策略，不能推出InnoDB原生对每种错误都会自动整事务回滚。MySQL8.0官方说明duplicate-key（未IGNORE）回滚失败SQL语句，deadlock回滚整个事务，lock timeout默认回滚语句，整事务还取决于应用、Spring回滚传播或配置。[官方错误处理](https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html)。无前提宣称唯一冲突使当前事务整体回滚是confirmed过度断言；可能默指异常逃逸Spring事务边界（likely解释），具体生成意图unknown。本轮不改reference/gold、不注入针对该题的技术答案，不把此技术问题混入原反馈硬矛盾计数。

## H09-REPAIR：已有全MAIN约束未被遵守

真实model=deepseek-flash、HTTP200、finish=stop、内层JSON可解析。实际请求body SHA `fb0bfe9572927bf0d3f27c5b18c986da03ba7ed2be83ebf6257279f0d11fe909`，raw响应SHA `22208cb6f9fa0ff79f75749baa21339e865b1fb607c6fe3b2a59c4eba1b8a438`，raw路径`target/phase5-epoch05/http/106.response.txt`。

实际旧SYSTEM已写：`scoringPointResults 必须逐项分析所有 MAIN 评分点，每一个输入的 scoringPointId 都必须恰好出现一次`；FOLLOW_UP还写：`应结合 MAIN 回答和 FOLLOW_UP 回答，对完整 MAIN 评分点重新给出综合评价`。USER列了全部MAIN IDs `[2569,2570,2571,2572]` 和目标 `[2571,2572]`。

实际返回仅`[2571,2572]`，漏`[2569,2570]`，与targets恰好相同。**违反已有约束confirmed；混淆目标与输出集合likely；内部模型原因unknown。** 不能据单例推出整个模型协议已变，或声称缺少ALL MAIN文字。原Java数量/集合校验拒绝正确，离线原capture仍被原LlmEvaluationValidator拒绝，不自动补缺项、不补分、不重试。

## 最小Prompt修改及作用域

1. JevJudgeProvider.FEEDBACK_SYSTEM：strengths只能概括候选明确支持的内容；reference/RAG只能核查或建议，不能归为候选已说；missingPoints只指出题目要求而回答缺失，不虚构错误观点，不再否认已正确回答的机制；无法支持时允许空数组。原pointEvidence全MAIN、covered连续原文、false=null、禁止重评score/covered/followUp的规则保持。
2. EvaluationPromptBuilder.SYSTEM_PROMPT：为baseline增加同类候选事实归因约束。FOLLOW_UP的system末尾动态列出本case完整MAIN IDs，明确targets不决定输出集合、禁止仅targets、包括MAIN已覆盖点，沿用原JSON schema。MAIN不追加动态FOLLOW_UP检查。
3. 新文字只在DeepSeek system中。**38个旧USER正文与RAG输入保持完全相同，JevRequest.questions/state在JSON语义树上38/38不变，RUBRICS及DATA_BOUNDARY源码片段也不变**。没有将DeepSeek JSON输出指令加入Jev评分标准。

版本：jev-judge-v1 → jev-judge-v2；evaluation-prompt-v1 → evaluation-prompt-v2。FEEDBACK_SYSTEM SHA `a6831234e0811cbe12796eaadf904b9ca70f871eef28100f2e8d594a04e67c88` → `eecefc87f6374ccf3267aeb461ff5188b27408703ef18e09a334b98cb5e33151`。38个prompt整体哈希因版本/系统文字改变，新旧均记录在下表及prompt-audit-before/after.json。原E05 frozenInputs不覆盖；下一epoch必须记录新prompt版本/输入哈希，不能换写旧AI结果。

临时审计器第一次把public static final版本常量编译期内联，造成旧加载类的顶部版本误标；原诊断副本保留在constant-inlining-diagnostic/，已改为运行时反射实际类字段，下面是复核后的记录。候选正文/请求树/反馈system哈希不依赖该误标，无生产变化或API重发。

| case | mode | E05旧prompt SHA | 新prompt SHA | USER及Jev数值请求 |
| --- | --- | --- | --- | --- |
| E02-EXCELLENT | MAIN_ANSWER | `87fc7ba978e6e866567efef491b432511d3fd57ca8c27a17cf195b45eccf6cee` | `6121d1b4bd6e0ccde62cc090db368a833bf8cf436b5dc9cbad2790e73d8752fa` | unchanged |
| E02-PARTIAL | MAIN_ANSWER | `cfec4ed2c683c13eb685a22b1d12008f991dd5997eda45f0a04bfdf8bd4a0e65` | `c5d266484ec33fa29b3c5bbff55d6d5a82d8de4eb898a3fb30aa884e6fc8cd5c` | unchanged |
| E02-WRONG | MAIN_ANSWER | `5622a2c71ace6244a62ff0ef8e5a8b85be9bfc99c4eb7e566d0750564a58a47a` | `78cfb6b0271f706f4e2bb8aefdd3fabaea83402508bbba9184d80af89e850dbd` | unchanged |
| E02-UNKNOWN | MAIN_ANSWER | `0407488ca548077d84752f8bb93bde57539e41d4b160e740009cd8455e159345` | `7871a86d991ca3bc46cbdc88ee09960c7fccf9cd635e5655f646693786afa3b4` | unchanged |
| E02-OFF_TOPIC | MAIN_ANSWER | `279fca98eb383df50da7f8e1c3ae740e6700006dc513470323767d5b0f2abe31` | `1bc5b55ad01d4ea54d0807c37bd2c806c6288efa75df91cc51d1f8f8171b945a` | unchanged |
| E02-CONTRADICTORY | MAIN_ANSWER | `cad2c2f43fa7f4bac33d2754360c53c0b7c8ef4b004acd9580e3edb19e5de864` | `9b630557ce8150bba353304bc77205ba3e1d253f74bbfaf7b39094362759414e` | unchanged |
| E02-BOUNDARY | MAIN_ANSWER | `cbbc2d64fb3715c0bfa61be23de9a4386a14d81a4b8e66289e98f1dc99cd86b1` | `2e7f403c22948828e99955d3934a7f924e485caf968b4c3a524fec8bedeea085` | unchanged |
| M04-EXCELLENT | MAIN_ANSWER | `f6f770c7be07cf6e9cfa3db957b7cc5b216e8b96ffaf7a4b1663ad093e3a4178` | `1b4fd38daec1044538ba5fe003b62cca7ddd659fd51d0d356984913a386e7dba` | unchanged |
| M04-PARTIAL | MAIN_ANSWER | `3f8eee2c628ec70ed3511d913fe707b15da699880c0eaea365498fcb4cd512bf` | `ac7244d3b1c3a5cfecd0437daeb525822a6e7c4e2ce411c76014bb62b0ea7bbf` | unchanged |
| M04-WRONG | MAIN_ANSWER | `f2538ac45745ffaf74d06bbd334bd04db88867033d7b9efac0aa35742f6de6b9` | `3a6d0f68dcd21160b6416dbf935d3027bcd20317587469f865f23ccfd65c7f26` | unchanged |
| M04-UNKNOWN | MAIN_ANSWER | `955a8ef603b4b26bf6fa1abcced94d23551e7dfa756f3615c8833ca60bf90dd0` | `ebe3e10837bf021a7565712d19b1c1797858df5e7cd4cd660fe44dbc46f7245f` | unchanged |
| M04-OFF_TOPIC | MAIN_ANSWER | `4d85d8dde0afda87e927ee77586c1e3bac350d8b60428f3782e60fff2a183f6f` | `4e9d310c5320501468317595f9bd75a0a190832ae6a7d448b1791f8ade418aee` | unchanged |
| M04-CONTRADICTORY | MAIN_ANSWER | `495cfe3635a1125c8d63105dff7bbc2017ba04a79bd822c469add080f70404ce` | `2172792cd70dd17df8ab6075ede079349060e9a5e4f788588d0e79a673260b8b` | unchanged |
| M04-BOUNDARY | MAIN_ANSWER | `e73bee94a50a315ab5d5bb4d03c7610006c8dae905d2e7adae23a6f8ca74c70c` | `ccb7399257cc0f363184c9bfae0b12b2323aeae75a1108c3fb7d08ba5ea070f0` | unchanged |
| H05-EXCELLENT | MAIN_ANSWER | `f1274aeeba566a1b6b9b2a2ca059a1b33d52c24a197f27452f7fa9debab9b607` | `ab780a37081a231ebc4f49c1309adebeebb55a96f8d89f1d8c9e07f076430ca6` | unchanged |
| H05-PARTIAL | MAIN_ANSWER | `780140a031688ae26355f829caca3ca97384f6142d18f1438767e2c5bdc161be` | `7fae06b61b8644879203014fb97eb6e76373f1091924dd6e652485d888ddb6d8` | unchanged |
| H05-WRONG | MAIN_ANSWER | `02d9c8978fa472ef34b130e11f2531c3149ab23b65cf9973d91340c1fbc3d6f7` | `c9848957084e659b28d1838bd853d5ee2365dbab2fcd1f3094988a2cd756d8d6` | unchanged |
| H05-UNKNOWN | MAIN_ANSWER | `9925a75e256763ba5b07209f09d32f028b595071984d47b249ee8b50db49caba` | `3e965e1692e2077cdf661ad60c1430c68e0a716c2291f6193886c35cf8eb7010` | unchanged |
| H05-OFF_TOPIC | MAIN_ANSWER | `d0e4731cebea092b144835739ce7f6b00a9e6c024bddf95c6eeb3fe08ed1db05` | `90c04e6c4d9a4a4ce02d87071eda343607d8b6809b26f39bfa4c711708442ad4` | unchanged |
| H05-CONTRADICTORY | MAIN_ANSWER | `1d72053db486a720a1da79ed4eb6f0d07ceebb873e08f1f4a835e2dd649d8e53` | `6aecec2cc8144214c3a7e414e68eac78cb0f6483bef946361e1b33c1f75871e0` | unchanged |
| H05-BOUNDARY | MAIN_ANSWER | `cc88cfd45bdb46219f7bd4acb36777f44dcff2eb3ff38f3d8c8d2316b10031c8` | `2cfe248c9615e1bc0ef56e3d774e7b4a7bc780a2e81daaf1f46760839f36e6d0` | unchanged |
| H09-EXCELLENT | MAIN_ANSWER | `4d3e94330aefe2459a05311d1c69f66dec72814be2f60c6e3be7f80164cb3854` | `4ea5778b900faff7698a72a58a0d91ed2ba0e089580f4b40621c7c8723aade07` | unchanged |
| H09-PARTIAL | MAIN_ANSWER | `6a31b24334cbee813f7a7d256d509e7f92e96eca6dc8ef3c28274a7d8b61fed9` | `f582136cd2f88feed497950f4e192f15ff78d806a36e86f709a8f532285f386b` | unchanged |
| H09-WRONG | MAIN_ANSWER | `8d8efc3e3851a2b97aa0a07c794cc6309673ce4c425bfd7c50e46757f1435735` | `fcbdd94fd01d6ce48edc36ca29d498c7ef2c49bf72e25cc038ef24528d09a1c1` | unchanged |
| H09-UNKNOWN | MAIN_ANSWER | `8f8309ec5dafc16ae478770837ad411cac5e4ea4b2b31068e6875e002ae47ca9` | `ce0e7ac26c1efde0733fde6407c561af7a294a0b2152bfae3ff802f12a291c83` | unchanged |
| H09-OFF_TOPIC | MAIN_ANSWER | `8a1b9148b95f46dcbcd75fc40dc1e520c860f9a1f1ad2d0c95e0a55d76aaf141` | `10eb8f281910ca6c66c8e8eee61dbc59310d9fadc5d7c9dfd4cae4c6d9967956` | unchanged |
| H09-CONTRADICTORY | MAIN_ANSWER | `77d0d4e1d481bc3976ee1fdfd95f6244636eb82198c54088871c76246f9fc889` | `1eb18bbac204423fd4f08380bccf19ce32011cd56d0324524163a9fc777b954c` | unchanged |
| H09-BOUNDARY | MAIN_ANSWER | `e41926c94f5963ce24e1da707cd00e89252970fa5a9c77504cf714205c95909d` | `cf94915bdf0d74adf336f1b286a6ecf69c0a2578e6c6746e29581d3ddcddc7b6` | unchanged |
| SEC-E02 | MAIN_ANSWER | `bf13ab7927e856765dd3da84d2ce35fdaf39461d35d62537cbacc5abe8d8cf7e` | `c51fedec8a1cbd7da5699f0fd413c1fd90e26116a4a0dd39f4aa5e31387c1fb8` | unchanged |
| SEC-H09 | MAIN_ANSWER | `61e11eeefeb5118ff028fc18a83fc9d9a3751ec4e3430c17053af3279f9dbe9f` | `7fa3d83928b86cf50b7159d25facd617bf7cb06d1f46bf6e2cd6056285396c42` | unchanged |
| E02-REPAIR | FOLLOW_UP_ANSWER | `1a28e22e1e1deca68a58e0e52c85aaccd38c62e2b4939e211f598acd3e53578c` | `febdc9a74cb5600bd911e06f5a1c1072e2aac822f5173580506a28e3930c88ad` | unchanged |
| E02-FAILED_REPAIR | FOLLOW_UP_ANSWER | `7016dae2b6a48be982ed15a3ede70eaa95b0efc6656c38ec25fd6b3e8fe4fe92` | `75e25d0ee97536a4fd200ad3321e2fce78f8aa0a1c0d0f92098a50fa72790520` | unchanged |
| M04-REPAIR | FOLLOW_UP_ANSWER | `bd7a2fa88e6eee13d70e73056cbf395eb2382c9be3c0e1d11326184be07d33af` | `5a2a281bf676c713aa5810b9b4915c8e6d9442df89410b2a52f1a506e33d81d4` | unchanged |
| M04-FAILED_REPAIR | FOLLOW_UP_ANSWER | `6c1f0929402f6969ec37c32350f76f0522b0f9b41949de584050c8bcd6f234f7` | `8cfaf5ee67bcb1f18c953befdca66ee3460b93aeffdefe0e0a58e90de9f17f44` | unchanged |
| H05-REPAIR | FOLLOW_UP_ANSWER | `eab6e79fabed2d6a2cdcf656091455769529e3993a9ba64fd1d7d2e3bf8c73dc` | `e5b390739850ec3a3f10923ac78434e7cd4d35ed55ae05916aa1a34668894df3` | unchanged |
| H05-FAILED_REPAIR | FOLLOW_UP_ANSWER | `8f85303a867ce6d3e0f58f39fdf6abf387653701151ab49c1f9f62cdf2fc3818` | `e581e7e9b58094abde70ecd1249fe41a4390fb263764c36e319cebf601ea9522` | unchanged |
| H09-REPAIR | FOLLOW_UP_ANSWER | `43a49649179a0aa11283af7492fb985199e1e759aad0f470b53944c72a0353c8` | `b23c2e24c260bff73f7da24f7a6869c5b13dbeaff8a2440b3442939562ed3aee` | unchanged |
| H09-FAILED_REPAIR | FOLLOW_UP_ANSWER | `6f755a4207296c0b7d10490e117e9fde7ff23a0f6a239012c46d65d6a94e8eea` | `52a634d37b4b3272ebce8c4566d82df60e72fc545b4c4b59b5220bc1d7aa7584` | unchanged |

离线测试证明文字被发送、全ID被列出、原协议/validator仍工作，不证明模型一定遵循，也不证明反馈矛盾消失。新E06需要真实全38重新观察，旧质量门槛不变。
