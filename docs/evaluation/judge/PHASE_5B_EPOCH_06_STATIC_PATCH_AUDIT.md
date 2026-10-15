# Phase 5B E06 独立静态补丁审计

Gate 0 **PASS**。当前磁盘源码/快照/原始HTTP证据已独立核对，JDK21离线定向19套365项，failures0/errors0/skipped0。真实模型API0；此报告不把旧响应回放当E06模型样本。

## 源码范围与历史区分

HEAD=`6076a70f18f3062b8faff4fc179cf21f00a95036`；本轮未修改任何原文件，未暂存/提交/推送。入口Git状态见target/phase5-epoch06/git-before.txt，原dirty工作树逐文件SHA冻结。历史环境隔离源文件、配置及测试未提交改动属于本轮之前；E04 DeepSeek默认值迁移和原协议精度补丁不归因E06。离线加固相对E05入口只涉及下表；其余868个入口快照项无越界变化。完整证据static-source-evidence.json、entry-git.diff与1983文件integrity-before.json。

| 文件 | E05入口SHA | E06入口SHA |
| --- | --- | --- |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java | d79eaa50533ea34be605c453de90dfb2010946440223a874ce2e98835dd28fa4 | 2f6286e3f59337054b9973d32a7cc8e01ce1805c9a09443aa476144cd7c1b4f1 |
| src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java | d85385c5057a03626e9ae0fd52d0e2940296fbe9e69c807acc5f19eb33108e89 | df7b5f99f90f6621a2370e505d88f8ac46ef51c8b3b04643802922d2a5549ae9 |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevScorePrecisionCompatibilityTest.java | ebab2b2ccb5bff0a1e7e3ba921dd3cf63471aa87618660df02e11691db2f1a5d | 12bdac2cabb1727054ce9ec2e46a464c74d27c9af5b2bc0f932436dac6522486 |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProviderTest.java | da5b6b37fd8fc995e953adff2c3bafbb787515cab68ef3618cf350cd52ca2caa | ba5a7037f10260a1d72742934a8b08bf405b452d23d3cff70b3ab833f4a96946 |
| src/test/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilderTest.java | cd2f69620e5f04eb4f8ffd17ca9ee2c6fafcb60b45b23a6e9fd219181da3904a | 853e736fa4c49f2d0e46cdb1c595eb29e9f70b940dfc3090f1c8327d96fb6460 |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevDisplayQuantizationHardeningTest.java | 新增 | 5cbb1d14fd848fc024b7335ac924ba8010722b8a1c5abdaa790e8fe1c51055e0 |
| src/test/java/com/kun/aiinterview/interview/evaluation/prompt/FollowUpOutputCompletenessHardeningTest.java | 新增 | 4234d972dca7f15b06ace7ab1d7530ff0e4558c954dcdb6e63ee82859e061e5e |
| src/test/resources/evaluation/jev-e05-numeric-captures.json | 新增 | 979eb210b0906bf6d5ddc4b84c3d09fb9499e61b8de4c770315fae044a527dff |

## 数值不变量人工源码审查

- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:190`：原严格sum≤0.0001/期望≤0.001通道先行；不一致时仅score调用兼容函数。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:208`：兼容返回模型严格等于jev-1.13.0；未知模型保留严格通道，不获得显示预算。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:241`：cent网格中心或一个nextUp/nextDown才有资格；两邻居和真实高精度矛盾不能借显示预算。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:224`：每个概率精确十进制区间max(0,q-.005)..min(1,q+.005)；ΣL≤1≤ΣU，贪心按权重求守恒分布的期望极值，再与score两位闭区间求交；不会仅看独立误差总预算。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:248`：从lower向upper分配剩余单位质量，极小按0→4、极大按4→0；凸可行域的线性期望值域覆盖两极值之间。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:261`：原始score×5 HALF_UP；原raw score/probabilities/confidence/legend不改，无normalize、猜数或输出补写。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:178`：null/有限性/范围先校验；choice仍严格sum和原argmax；类型/legend/rubric/confidence先通过。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:114`：生产一次fallback保留；本批harness用非联网sentinel截获would-fallback，独立baseline另外真实调用。

## Prompt及业务审查

`src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:28`为jev-judge-v2；FEEDBACK_SYSTEM候选事实来源约束位于43。`src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java:16`为evaluation-prompt-v2，候选归因文字与FOLLOW_UP完整MAIN IDs检查位于121。

实际重新编译PromptAudit：38 USER正文、Jev rubrics/state/questions请求树全部与E05相同；38完整Prompt新SHA全部不同且与批准离线加固快照一致。8个FOLLOW_UP明确完整MAIN IDs，不以target集合替代。validator/score/policy/Java状态决策/RAG topK5不改。H09仍是冻结库存并发假设面试题，未新增订单库存业务模块。

| case | 原完整Prompt SHA | v2完整Prompt SHA |
| --- | --- | --- |
| E02-EXCELLENT | 87fc7ba978e6e866567efef491b432511d3fd57ca8c27a17cf195b45eccf6cee | 6121d1b4bd6e0ccde62cc090db368a833bf8cf436b5dc9cbad2790e73d8752fa |
| E02-PARTIAL | cfec4ed2c683c13eb685a22b1d12008f991dd5997eda45f0a04bfdf8bd4a0e65 | c5d266484ec33fa29b3c5bbff55d6d5a82d8de4eb898a3fb30aa884e6fc8cd5c |
| E02-WRONG | 5622a2c71ace6244a62ff0ef8e5a8b85be9bfc99c4eb7e566d0750564a58a47a | 78cfb6b0271f706f4e2bb8aefdd3fabaea83402508bbba9184d80af89e850dbd |
| E02-UNKNOWN | 0407488ca548077d84752f8bb93bde57539e41d4b160e740009cd8455e159345 | 7871a86d991ca3bc46cbdc88ee09960c7fccf9cd635e5655f646693786afa3b4 |
| E02-OFF_TOPIC | 279fca98eb383df50da7f8e1c3ae740e6700006dc513470323767d5b0f2abe31 | 1bc5b55ad01d4ea54d0807c37bd2c806c6288efa75df91cc51d1f8f8171b945a |
| E02-CONTRADICTORY | cad2c2f43fa7f4bac33d2754360c53c0b7c8ef4b004acd9580e3edb19e5de864 | 9b630557ce8150bba353304bc77205ba3e1d253f74bbfaf7b39094362759414e |
| E02-BOUNDARY | cbbc2d64fb3715c0bfa61be23de9a4386a14d81a4b8e66289e98f1dc99cd86b1 | 2e7f403c22948828e99955d3934a7f924e485caf968b4c3a524fec8bedeea085 |
| M04-EXCELLENT | f6f770c7be07cf6e9cfa3db957b7cc5b216e8b96ffaf7a4b1663ad093e3a4178 | 1b4fd38daec1044538ba5fe003b62cca7ddd659fd51d0d356984913a386e7dba |
| M04-PARTIAL | 3f8eee2c628ec70ed3511d913fe707b15da699880c0eaea365498fcb4cd512bf | ac7244d3b1c3a5cfecd0437daeb525822a6e7c4e2ce411c76014bb62b0ea7bbf |
| M04-WRONG | f2538ac45745ffaf74d06bbd334bd04db88867033d7b9efac0aa35742f6de6b9 | 3a6d0f68dcd21160b6416dbf935d3027bcd20317587469f865f23ccfd65c7f26 |
| M04-UNKNOWN | 955a8ef603b4b26bf6fa1abcced94d23551e7dfa756f3615c8833ca60bf90dd0 | ebe3e10837bf021a7565712d19b1c1797858df5e7cd4cd660fe44dbc46f7245f |
| M04-OFF_TOPIC | 4d85d8dde0afda87e927ee77586c1e3bac350d8b60428f3782e60fff2a183f6f | 4e9d310c5320501468317595f9bd75a0a190832ae6a7d448b1791f8ade418aee |
| M04-CONTRADICTORY | 495cfe3635a1125c8d63105dff7bbc2017ba04a79bd822c469add080f70404ce | 2172792cd70dd17df8ab6075ede079349060e9a5e4f788588d0e79a673260b8b |
| M04-BOUNDARY | e73bee94a50a315ab5d5bb4d03c7610006c8dae905d2e7adae23a6f8ca74c70c | ccb7399257cc0f363184c9bfae0b12b2323aeae75a1108c3fb7d08ba5ea070f0 |
| H05-EXCELLENT | f1274aeeba566a1b6b9b2a2ca059a1b33d52c24a197f27452f7fa9debab9b607 | ab780a37081a231ebc4f49c1309adebeebb55a96f8d89f1d8c9e07f076430ca6 |
| H05-PARTIAL | 780140a031688ae26355f829caca3ca97384f6142d18f1438767e2c5bdc161be | 7fae06b61b8644879203014fb97eb6e76373f1091924dd6e652485d888ddb6d8 |
| H05-WRONG | 02d9c8978fa472ef34b130e11f2531c3149ab23b65cf9973d91340c1fbc3d6f7 | c9848957084e659b28d1838bd853d5ee2365dbab2fcd1f3094988a2cd756d8d6 |
| H05-UNKNOWN | 9925a75e256763ba5b07209f09d32f028b595071984d47b249ee8b50db49caba | 3e965e1692e2077cdf661ad60c1430c68e0a716c2291f6193886c35cf8eb7010 |
| H05-OFF_TOPIC | d0e4731cebea092b144835739ce7f6b00a9e6c024bddf95c6eeb3fe08ed1db05 | 90c04e6c4d9a4a4ce02d87071eda343607d8b6809b26f39bfa4c711708442ad4 |
| H05-CONTRADICTORY | 1d72053db486a720a1da79ed4eb6f0d07ceebb873e08f1f4a835e2dd649d8e53 | 6aecec2cc8144214c3a7e414e68eac78cb0f6483bef946361e1b33c1f75871e0 |
| H05-BOUNDARY | cc88cfd45bdb46219f7bd4acb36777f44dcff2eb3ff38f3d8c8d2316b10031c8 | 2cfe248c9615e1bc0ef56e3d774e7b4a7bc780a2e81daaf1f46760839f36e6d0 |
| H09-EXCELLENT | 4d3e94330aefe2459a05311d1c69f66dec72814be2f60c6e3be7f80164cb3854 | 4ea5778b900faff7698a72a58a0d91ed2ba0e089580f4b40621c7c8723aade07 |
| H09-PARTIAL | 6a31b24334cbee813f7a7d256d509e7f92e96eca6dc8ef3c28274a7d8b61fed9 | f582136cd2f88feed497950f4e192f15ff78d806a36e86f709a8f532285f386b |
| H09-WRONG | 8d8efc3e3851a2b97aa0a07c794cc6309673ce4c425bfd7c50e46757f1435735 | fcbdd94fd01d6ce48edc36ca29d498c7ef2c49bf72e25cc038ef24528d09a1c1 |
| H09-UNKNOWN | 8f8309ec5dafc16ae478770837ad411cac5e4ea4b2b31068e6875e002ae47ca9 | ce0e7ac26c1efde0733fde6407c561af7a294a0b2152bfae3ff802f12a291c83 |
| H09-OFF_TOPIC | 8a1b9148b95f46dcbcd75fc40dc1e520c860f9a1f1ad2d0c95e0a55d76aaf141 | 10eb8f281910ca6c66c8e8eee61dbc59310d9fadc5d7c9dfd4cae4c6d9967956 |
| H09-CONTRADICTORY | 77d0d4e1d481bc3976ee1fdfd95f6244636eb82198c54088871c76246f9fc889 | 1eb18bbac204423fd4f08380bccf19ce32011cd56d0324524163a9fc777b954c |
| H09-BOUNDARY | e41926c94f5963ce24e1da707cd00e89252970fa5a9c77504cf714205c95909d | cf94915bdf0d74adf336f1b286a6ecf69c0a2578e6c6746e29581d3ddcddc7b6 |
| SEC-E02 | bf13ab7927e856765dd3da84d2ce35fdaf39461d35d62537cbacc5abe8d8cf7e | c51fedec8a1cbd7da5699f0fd413c1fd90e26116a4a0dd39f4aa5e31387c1fb8 |
| SEC-H09 | 61e11eeefeb5118ff028fc18a83fc9d9a3751ec4e3430c17053af3279f9dbe9f | 7fa3d83928b86cf50b7159d25facd617bf7cb06d1f46bf6e2cd6056285396c42 |
| E02-REPAIR | 1a28e22e1e1deca68a58e0e52c85aaccd38c62e2b4939e211f598acd3e53578c | febdc9a74cb5600bd911e06f5a1c1072e2aac822f5173580506a28e3930c88ad |
| E02-FAILED_REPAIR | 7016dae2b6a48be982ed15a3ede70eaa95b0efc6656c38ec25fd6b3e8fe4fe92 | 75e25d0ee97536a4fd200ad3321e2fce78f8aa0a1c0d0f92098a50fa72790520 |
| M04-REPAIR | bd7a2fa88e6eee13d70e73056cbf395eb2382c9be3c0e1d11326184be07d33af | 5a2a281bf676c713aa5810b9b4915c8e6d9442df89410b2a52f1a506e33d81d4 |
| M04-FAILED_REPAIR | 6c1f0929402f6969ec37c32350f76f0522b0f9b41949de584050c8bcd6f234f7 | 8cfaf5ee67bcb1f18c953befdca66ee3460b93aeffdefe0e0a58e90de9f17f44 |
| H05-REPAIR | eab6e79fabed2d6a2cdcf656091455769529e3993a9ba64fd1d7d2e3bf8c73dc | e5b390739850ec3a3f10923ac78434e7cd4d35ed55ae05916aa1a34668894df3 |
| H05-FAILED_REPAIR | 8f85303a867ce6d3e0f58f39fdf6abf387653701151ab49c1f9f62cdf2fc3818 | e581e7e9b58094abde70ecd1249fe41a4390fb263764c36e319cebf601ea9522 |
| H09-REPAIR | 43a49649179a0aa11283af7492fb985199e1e759aad0f470b53944c72a0353c8 | b23c2e24c260bff73f7da24f7a6869c5b13dbeaff8a2440b3442939562ed3aee |
| H09-FAILED_REPAIR | 6f755a4207296c0b7d10490e117e9fde7ff23a0f6a239012c46d65d6a94e8eea | 52a634d37b4b3272ebce8c4566d82df60e72fc545b4c4b59b5220bc1d7aa7584 |

## 冻结历史门禁

Benchmark SHA=fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a；38=30 MAIN+8 FOLLOW_UP，24排序、4修复及gold/阈值未改。358个离线加固前冻结历史文件重新全部匹配；E01–E05现存报告、诊断、响应证据以及E02–E05的7个现存once-lock由本轮1983项清单单独冻结，即使Git忽略也保留本地真实证据。原历史响应SHA核查109项全部通过。E05原报告/RESULTS/RUN_EVIDENCE数字交叉核对仍为38 Jev+33 feedback+38 baseline=109，33/38 Jev有效、37/38 baseline、32/38配对，5数值失败、1漏MAIN IDs；历史不重写。

E01原生数值拒绝；E02 Jev503；E03 DeepSeek返回模型漂移；E04内层非法JSON原捕获事故仍留档。离线5/5数值回放不增加E06样本。完整历史Hash清单见integrity-before.json；私有application-local.yaml不复制、不打印或哈希，其中配置将在Gate1内存绑定，仅输出密钥SET/MISSING。

## 真实JUnit复跑

| suite | tests | failures | errors | skipped | XML SHA256 |
| --- | ---: | ---: | ---: | ---: | --- |
| FollowUpPolicyTest | 15 | 0 | 0 | 0 | 679660c3a190b585ff5b16824da1e2c2b7e5568ea3003c0f503bc8b0e18423d0 |
| FollowUpTargetResolverTest | 7 | 0 | 0 | 0 | 01cad7c6b99ac5e9f9f8a79cccb2b6950f956256b1974609596c685183641cad |
| DeepSeekJudgeProviderTest | 1 | 0 | 0 | 0 | f0bd91efa328593348a06fe6a1a0e940d066e83dfa06dc0c932ec6e1e446fcfa |
| JevClientTest | 17 | 0 | 0 | 0 | 2fbd1b6790ed8a19e9d6b3b728ffd8421afc831528fb354a34d45bd6a5865642 |
| JevDisplayQuantizationHardeningTest | 32 | 0 | 0 | 0 | 037d2428f06dae77d0348ebf5d95e9ab170d04ad25be1e38a3e6b16956fd39c0 |
| JevJudgeProviderTest | 47 | 0 | 0 | 0 | 68015eff36b695b37062988a3981a22fa14ec720ab82bf9673202ff866da208d |
| JevScoreMappingTest | 16 | 0 | 0 | 0 | 522add7a297408bc740c5eb54ba6c199099a1d5279709d3429e2b2b27cf717f1 |
| JevScorePrecisionCompatibilityTest | 63 | 0 | 0 | 0 | 8ea642d965da429327502fbb47f2a9d5bbb9890b73d5f11e1809ba2fe5aaa0ef |
| JudgeConfigurationTest | 12 | 0 | 0 | 0 | fb06cd9bd8ff813e2c040921b5a564eb4f45f36563499e4d654092372e89e2ed |
| DeepSeekConfigurationTest | 2 | 0 | 0 | 0 | 4d3d0ef9a157d9b85a180b1295e2484e3ecc2b396d04449c2643468bb2467be3 |
| DeepSeekEvaluationClientTest | 19 | 0 | 0 | 0 | 692c5113014b29183a098dc0b8c6db6afc0c59b3ce1154a805cfe42d3c157e33 |
| DeepSeekPropertiesTest | 19 | 0 | 0 | 0 | 98f84a845052dcb40e3dad669dd2542c4febc42f115c70b2581984755d7e04be |
| EvaluationOrchestrationServiceTest | 23 | 0 | 0 | 0 | 646bcc2315980f956758ad9ff6dbb75573963bb7f5ffc45c7da61f4a7f15a25a |
| EvaluationPromptBuilderTest | 11 | 0 | 0 | 0 | 534d3fd71dba16b9335daa5a7028bcc133d9ad12fa59191e3c7ae1eb358de565 |
| EvaluationPromptTest | 13 | 0 | 0 | 0 | 9efbbed7d36e0b9b7b7e37f047d358824c1ab5f052e1cff3a03b2d0e1835dd43 |
| FollowUpOutputCompletenessHardeningTest | 2 | 0 | 0 | 0 | f6263a73b1b7e6f372415ff1e39c69be2d5e8f3b3fe854cd62e83e9bd219bef5 |
| EvaluationScoreCalculatorTest | 4 | 0 | 0 | 0 | ddd7ded3ebdbc6661b470e81419556a187cd3e281d84c4c05f9709f9f33c4c7d |
| EvaluationStandardTest | 15 | 0 | 0 | 0 | ebe0cea78963ef43e9ab643f459d424d57b815a81fe3aa6372830dc5119c1f42 |
| LlmEvaluationValidatorTest | 47 | 0 | 0 | 0 | 4663c537f51435c63590ed396df0b49ff246204c5e3141bdb6c8a3c0f69bd5f1 |

命令：`target/phase5-epoch06/offline.ps1 -Mode test`，实际Maven `-o -B -ntp -f target/phase5-epoch06/offline-pom.xml -Dtest=<targeted-suites.txt> test`。JDK21.0.11；19/365/0/0/0；tests.log SHA=6aa562438c75a88f47a1009b693ed2f85c9043cacd0a86c2b42215b82d7d1b6b。POM仅改变临时build目录并排除application-local.yaml，保留原依赖/插件；不向单测提供真实API/DB凭证、清除RUN_REAL/MILVUS及JAVA注入选项。原target/classes与surefire-reports保持。

新增32数值硬化、原63精度、Client/Prompt/Validator/评分/追问/Judge全部通过。额外harness9项内存响应安全测试0失败：非法JSON隔离、下一例继续、重复请求前阻断、401/429/503停止、model drift停止、外层格式错误隔离、旧wire取证；额外PromptAudit38例零联网。未运行全量mvn test或真实Smoke，不能称全项目回归/真实集成通过。

**Gate0完成后才允许进入Gate1；任何Gate1异常STOP，零真实模型调用。**

## E06最终门禁结果附注

Gate1实时只读预检PASS；38新Prompt/环境/DB/vector匹配后，完整执行E06真实38例114推理，无重试。Jev native38/38、primary+feedback33/38、baseline38/38、paired33/38，因此质量FAIL、5C NOT_RUN。原源码/配置/Gold/历史1983项SHA全部不变。临时启动shell -D参数解析错误仅发生Java加载前，修正引号后启动真实预检；原错误日志留存，无服务重试。该报告为E06新增报告，本附注不改历史。STOP_WAIT_COMMANDER。

历史归档范围说明：E01本地报告/诊断/RUN_EVIDENCE中的首调用证据已冻结；未找到独立E01 once-lock文件，未补造或替其声明文件SHA。E02–E05现存7个锁全部按入口快照复核。原请求/响应独立字节SHA校验仅对具有原路径及摘要的现存artifact执行，不能为没有独立字节档案的旧轮伪称相同证据强度。
