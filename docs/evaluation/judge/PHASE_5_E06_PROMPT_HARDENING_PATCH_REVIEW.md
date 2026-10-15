# E06 Prompt文字加固补丁审查

本轮为 Commander 授权的离线取证与文字加固。真实模型/Embedding API 0，数据库与Milvus客户端初始化0、写入0；E07未运行、5C NOT_RUN、Phase6 BLOCKED。E06 原严格门禁 FAIL，38案例/114推理/零重试、native38/38、baseline38/38、联合反馈及配对33/38保持不变。

**MODIFIED / OFFLINE_VERIFIED_ONLY**：本轮修改的是既有system常量及v2→v3；未修改Java算法。语义根因证据充分到可以缩窄表达，但不证明供应商会服从。原确认及争议分类保持，历史epoch没有重标有效。

## 相对入场快照的完整范围

| 文件 | 用途 | 入口SHA256 | 当前SHA256 |
| --- | --- | --- | --- |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java | FEEDBACK_SYSTEM+jev-judge-v3 | 2f6286e3f59337054b9973d32a7cc8e01ce1805c9a09443aa476144cd7c1b4f1 | af2c5182e157e64dae2bc36005d25c0a41b2ff28fd6ee079030418e29cc73615 |
| src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java | SYSTEM_PROMPT+evaluation-prompt-v3 | df7b5f99f90f6621a2370e505d88f8ac46ef51c8b3b04643802922d2a5549ae9 | 98bfd63ed31a175d0de0615bfe4f1233e3b48a89d5e8f8244716f105a15a3878 |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProviderTest.java | 仅将既有版本断言改v3 | ba5a7037f10260a1d72742934a8b08bf405b452d23d3cff70b3ab833f4a96946 | c7309a4c2035d89d5dd7b029bf87d4aab736b416e3b9e12300f4721cbbb4846a |
| src/test/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilderTest.java | 仅将既有版本断言改v3 | 853e736fa4c49f2d0e46cdb1c595eb29e9f70b940dfc3090f1c8327d96fb6460 | 570ccc02938eaaac16517026aa488b8d51414c7a28beb470ce550a8d9eb1e470 |

| 新增测试文件 | 用途 | SHA256 |
| --- | --- | --- |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevFeedbackPromptHardeningTest.java | 五份旧响应+合规/Unicode/JSON/全MAIN ID回归 | c9956174fcaa1354320fb7935b5fe2c2d3ddd090aa934a15933a30a4ddce345a |
| src/test/resources/evaluation/jev-e06-feedback-captures.json | 五份原native/context/retrieval/content及raw SHA；新增fixture，不覆盖旧响应 | 00932631b2df2827f41b3d8f074cdb35372a148067df74477b4e2162ad69f10d |

既有两测试文件只改变版本字符串断言，原测试方法/名字和365项全部保留。两个生产类去除指定文字常量和版本后与入场字节完全相同；原换行形式（含既有混合换行）原位保留，没有重排import/空白/其它代码。RAG、Embedding、score/choice、Java决定、完整性validator、输出schema、异常/fallback、DeepSeek/Jev参数、模型/温度/maxTokens及所有非授权配置没有改动。

问题到措辞映射：E02-EXCELLENT→禁止拼接/省略/标点改写；E02-REPAIR和H09-FAILED_REPAIR→covered真非空且MAIN已覆盖点仍引用MAIN；两处坏JSON→合法对象/双引号键/冒号/字符串转义；H05-PARTIAL确认假遗漏与混合子项争议→只写尚缺子项，correction不将已表述内容称需补充。没有植入案例答案、gold、question ID或新技术评分标准。FOLLOW_UP完整所有MAIN IDs的动态补充原字节不变。

## 完整入场diff

Diff文件 `target/phase5-e06-offline-forensics/entry-relative-patch.diff`，SHA `08e4b87e4796e74302cd0fac4fab01b08d0f310feb477f93470e22277ae963b2`。不能用相对Git HEAD的diff替代，因为HEAD之后的已有数值兼容/环境隔离等变更属于本轮前。

```diff
--- src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java (entry)
+++ src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java (current)
@@ -26,7 +26,7 @@
 
 /** Local model composition. No persistence, state transitions or total-score calculation. */
 public class JevJudgeProvider implements JudgeProvider {
-    public static final String PROMPT_VERSION = "jev-judge-v2";
+    public static final String PROMPT_VERSION = "jev-judge-v3";
     private static final String TWO_DECIMAL_COMPATIBLE_MODEL = "jev-1.13.0";
     private static final BigDecimal DISPLAY_HALF_STEP = new BigDecimal("0.005");
     private static final Logger log = LoggerFactory.getLogger(JevJudgeProvider.class);
@@ -45,11 +45,14 @@
             missingPoints只能指出题目要求而回答尚未覆盖的知识；不能把未出现的错误观点归给候选人。
             MAIN或FOLLOW_UP已经正确说明的机制不能再称为遗漏，correction不能否定候选人已正确说明的事实。
             无法从候选回答提供依据的优点或错误归因不要输出；必要时strengths或missingPoints输出空数组。
-            pointEvidence逐项包含所有MAIN评分点，每个scoringPointId恰好一次。
-            covered=true的evidence必须直接摘录MAIN或FOLLOW_UP回答中的连续原文，不能引用RAG或参考答案；
-            covered=false时evidence为null。
+            pointEvidence逐项包含所有MAIN评分点，每个scoringPointId恰好一次，包括已在MAIN回答覆盖的点。
+            FOLLOW_UP只补充回答，不能只为追问目标提供证据；已由MAIN覆盖的点仍从MAIN摘录。
+            covered=true的evidence必须是MAIN或FOLLOW_UP其中一份回答里的一个非空连续原文片段。
+            逐字复制，不改字词、空白或标点；不拼接不同位置或两份回答，不加省略号，不改写或概括。
+            covered=true不能输出空串、纯空白或null；不能引用RAG或参考答案；covered=false时evidence为null。
             只有followUpRecommended=true才生成一个具体候选追问，否则suggestedFollowUp为null。
             只返回JSON，不返回分数、covered、followUpRecommended、totalScore或业务状态。
+            必须输出一个合法JSON对象，键名使用双引号并以冒号连接值；字符串内的双引号、反斜杠和换行须按JSON规则转义。
             格式：{"strengths":[],"missingPoints":[],"correction":null,
             "pointEvidence":[{"scoringPointId":1,"evidence":"回答原文"}],"suggestedFollowUp":null}
             """;
--- src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java (entry)
+++ src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java (current)
@@ -13,7 +13,7 @@
 public class EvaluationPromptBuilder {

 

     public static final String PROMPT_VERSION =

-            "evaluation-prompt-v2";
+            "evaluation-prompt-v3";
 

     private static final String SYSTEM_PROMPT = """

             你是 Java 后端模拟面试系统中的回答评价助手。

@@ -51,6 +51,9 @@
                 或RAG中的机制、场景归为用户已说过的内容。
             13. missingPoints不能列出MAIN或FOLLOW_UP已经正确说明的机制，
                 不能将未出现的错误观点归给用户；correction不能否定已正确说明的事实。
+                输出前逐项核对MAIN和FOLLOW_UP的实际内容；若评分点只有部分内容尚缺，
+                missingPoints只写尚缺的具体子项，不把已说明的子项和未说明的子项合并称为遗漏。
+                correction中的补充建议也不能把已明确表述的内容称为尚需补充；无法确认未提及时不要断言遗漏。
 

             MAIN_ANSWER：

             - 可以设置 followUpRecommended=true。

--- src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProviderTest.java (entry)
+++ src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProviderTest.java (current)
@@ -74,7 +74,7 @@
         stubFeedback(validFeedback("CAS保护状态推进", "请解释并发冲突"));
         var result = provider.evaluate(context, prompt);
         assertThat(result.model()).isEqualTo("jev-1.13.0");
-        assertThat(result.promptVersion()).isEqualTo("jev-judge-v2");
+        assertThat(result.promptVersion()).isEqualTo("jev-judge-v3");
         var validated = validator.validate(context, result.suggestion());
         var score = new EvaluationScoreCalculator().calculate(validated);
         assertThat(score.totalScore()).isEqualTo(85);
--- src/test/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilderTest.java (entry)
+++ src/test/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilderTest.java (current)
@@ -40,7 +40,7 @@
         );
 
         assertThat(prompt.promptVersion())
-                .isEqualTo("evaluation-prompt-v2");
+                .isEqualTo("evaluation-prompt-v3");
         assertThat(prompt.userPrompt())
                 .contains(
                         "MAIN_ANSWER",
```

## 旧/新Prompt冻结

| 对象 | 原v2 SHA256 | 新v3 SHA256 |
| --- | --- | --- |
| FEEDBACK_SYSTEM (UTF-8 runtime String) | eecefc87f6374ccf3267aeb461ff5188b27408703ef18e09a334b98cb5e33151 | 883d0e103ce3467be349db6b3b9fbe8a3f7371dfa5b7315b3bfc741180a0e112 |

`prompt-hash-freeze.json`保存38个旧/新完整EvaluationPrompt、旧/新SYSTEM hash、相同USER hash、两版本和原E06引用。完整Prompt hash按Jackson紧凑序列化EvaluationPrompt（promptVersion/systemPrompt/userPrompt）UTF-8计算；不是只对常量或case名哈希。旧38个hash已独立重算与E06记录相同；新38个均不同；USER及Jev rubric/state/questions请求树38/38不变。新反馈system只用于未来另行获批批次，本轮没发请求。

| case | E06 v2完整Prompt SHA | 新v3完整Prompt SHA |
| --- | --- | --- |
| E02-EXCELLENT | 6121d1b4bd6e0ccde62cc090db368a833bf8cf436b5dc9cbad2790e73d8752fa | baf9d21dc033e260775728d3567c48ce67ba9190b6dd844451663994398789fb |
| E02-PARTIAL | c5d266484ec33fa29b3c5bbff55d6d5a82d8de4eb898a3fb30aa884e6fc8cd5c | c44c8d0c933f2104bcf04d9cce41a148b58b029cfadca1ca2ba5d179cd67be5f |
| E02-WRONG | 78cfb6b0271f706f4e2bb8aefdd3fabaea83402508bbba9184d80af89e850dbd | f7d65106c4cd122a7159e8cbf73ed6f6a61d3afad54a81df9ffd255a5f2a823e |
| E02-UNKNOWN | 7871a86d991ca3bc46cbdc88ee09960c7fccf9cd635e5655f646693786afa3b4 | 37b7c59439b9766eb7887eabf3a438195550ff8c5ed3a3fbe9150fe7e1e905a9 |
| E02-OFF_TOPIC | 1bc5b55ad01d4ea54d0807c37bd2c806c6288efa75df91cc51d1f8f8171b945a | fc273b40e49ddce48eaf7292cf9bdd4fad36c023febc2894c82163d47aed5f98 |
| E02-CONTRADICTORY | 9b630557ce8150bba353304bc77205ba3e1d253f74bbfaf7b39094362759414e | ec54324465c49872384f036347edebd231a11dd39eac2175c4a6311a9bdab90b |
| E02-BOUNDARY | 2e7f403c22948828e99955d3934a7f924e485caf968b4c3a524fec8bedeea085 | aff56ced2aa52d3702c54224f74b3972491cb4b5f311f071b562a2ae058c2938 |
| M04-EXCELLENT | 1b4fd38daec1044538ba5fe003b62cca7ddd659fd51d0d356984913a386e7dba | ad6595669ee100a8d2787791cf7d58340e86e02534151495cca797ff5d7c3d03 |
| M04-PARTIAL | ac7244d3b1c3a5cfecd0437daeb525822a6e7c4e2ce411c76014bb62b0ea7bbf | 60259edcfd3ee117f505f252be5c83e9193f27e8c04a605e6d0574a6f568ff1e |
| M04-WRONG | 3a6d0f68dcd21160b6416dbf935d3027bcd20317587469f865f23ccfd65c7f26 | bf909f8cf6389c9f54e238dd17d03274dac848344e7b08ccc9c82f0095adadc8 |
| M04-UNKNOWN | ebe3e10837bf021a7565712d19b1c1797858df5e7cd4cd660fe44dbc46f7245f | 737f2005508962d70d2a59c693c5603861fe87042a2a5d5d2e52afbc2b440d06 |
| M04-OFF_TOPIC | 4e9d310c5320501468317595f9bd75a0a190832ae6a7d448b1791f8ade418aee | d5909535cf104e2d45669ca2a53530ae37f895c33e4f31a3259b8a6e22a167c2 |
| M04-CONTRADICTORY | 2172792cd70dd17df8ab6075ede079349060e9a5e4f788588d0e79a673260b8b | 2f3936401a919c20fbf8b2b08c46d93765247714f2401ac6e4796954937ae849 |
| M04-BOUNDARY | ccb7399257cc0f363184c9bfae0b12b2323aeae75a1108c3fb7d08ba5ea070f0 | 4008d225334b0fc17c0d8ffb73b56dcfac06b0382c3ce120d1ec83f8dacbbcdd |
| H05-EXCELLENT | ab780a37081a231ebc4f49c1309adebeebb55a96f8d89f1d8c9e07f076430ca6 | 10c272022d01e25352d4f60ebfc1c4fea38105ce5ad419cdd451b7a5789370bd |
| H05-PARTIAL | 7fae06b61b8644879203014fb97eb6e76373f1091924dd6e652485d888ddb6d8 | 62c1a9028d86419f66dd7666e23f658bc493609d261871c8963105748c2dc14a |
| H05-WRONG | c9848957084e659b28d1838bd853d5ee2365dbab2fcd1f3094988a2cd756d8d6 | f3b7c9163f1f3f8bc559c6810f9f6b6b33f14988b03dd3c65b930533cef1b3e8 |
| H05-UNKNOWN | 3e965e1692e2077cdf661ad60c1430c68e0a716c2291f6193886c35cf8eb7010 | 7d3b247c1695f308e72f808ba631911c906e93d1ab3680e60997dafd0df815d8 |
| H05-OFF_TOPIC | 90c04e6c4d9a4a4ce02d87071eda343607d8b6809b26f39bfa4c711708442ad4 | c764b52a2e0a1f9fa52fb499d4f62ffaaad34dbf8417a6d76952f76494c87312 |
| H05-CONTRADICTORY | 6aecec2cc8144214c3a7e414e68eac78cb0f6483bef946361e1b33c1f75871e0 | 79f8b74e2599fdbae15101c0e40355a7a2b549e45334dbf731054936d85f4a3f |
| H05-BOUNDARY | 2cfe248c9615e1bc0ef56e3d774e7b4a7bc780a2e81daaf1f46760839f36e6d0 | 5889b14f45278762b13ca9ed44dbe93495b580bee6593e322fd2dbfc3b280277 |
| H09-EXCELLENT | 4ea5778b900faff7698a72a58a0d91ed2ba0e089580f4b40621c7c8723aade07 | eb3e9c9b48bb4ad8c64955dc36dc54a9f09bd6e42c043358cdc13c0a8134816a |
| H09-PARTIAL | f582136cd2f88feed497950f4e192f15ff78d806a36e86f709a8f532285f386b | 3f2663c679a3a837fd091f569348c0c4428986964db746ab10398300660a6fbe |
| H09-WRONG | fcbdd94fd01d6ce48edc36ca29d498c7ef2c49bf72e25cc038ef24528d09a1c1 | 35b5b0ac17f35fe967cfc15f7866281a4ff957cfd797f4d54ac21627666130df |
| H09-UNKNOWN | ce0e7ac26c1efde0733fde6407c561af7a294a0b2152bfae3ff802f12a291c83 | 6f4876b30a7aac3bd6364c152361e977837de1be4ba006dbaac6fe5915b734db |
| H09-OFF_TOPIC | 10eb8f281910ca6c66c8e8eee61dbc59310d9fadc5d7c9dfd4cae4c6d9967956 | 05e67fd04247e411ae06697f07e49c4c79e551af879dfd1617889a9dc179c38d |
| H09-CONTRADICTORY | 1eb18bbac204423fd4f08380bccf19ce32011cd56d0324524163a9fc777b954c | b9b9c61806c11eb2e8dfad92aca0f89d4079631f5a1343ffa889195c35ec1d59 |
| H09-BOUNDARY | cf94915bdf0d74adf336f1b286a6ecf69c0a2578e6c6746e29581d3ddcddc7b6 | f4840fcce0484e8e03d68a9365afd8a421d0932f1c4080b9c2346dcaa4439521 |
| SEC-E02 | c51fedec8a1cbd7da5699f0fd413c1fd90e26116a4a0dd39f4aa5e31387c1fb8 | 205a36c59af7e3c2940032f17dd4a1243ee244ba60a4659026f260c3f8a1267d |
| SEC-H09 | 7fa3d83928b86cf50b7159d25facd617bf7cb06d1f46bf6e2cd6056285396c42 | 88666398398595e7764893037d8bb1814660de20efcdbd6cf99f0ac7b7de6ceb |
| E02-REPAIR | febdc9a74cb5600bd911e06f5a1c1072e2aac822f5173580506a28e3930c88ad | 554ca5f91d64a4c853284aa615c378fec834d774f3fb0f2581772784a11504a0 |
| E02-FAILED_REPAIR | 75e25d0ee97536a4fd200ad3321e2fce78f8aa0a1c0d0f92098a50fa72790520 | 07f57d7d57ddfa3fb2988ec6b11c7e0b89d852a1e59f31c1d242824951be8ffa |
| M04-REPAIR | 5a2a281bf676c713aa5810b9b4915c8e6d9442df89410b2a52f1a506e33d81d4 | 190691a623fdd4c1ec04a34d959aed5969ef011a0b2567783fa3a3d2e5c96046 |
| M04-FAILED_REPAIR | 8cfaf5ee67bcb1f18c953befdca66ee3460b93aeffdefe0e0a58e90de9f17f44 | ca6cd557380b53d93540f5a9308709c1314ae6323b6c0c7412503518152801d3 |
| H05-REPAIR | e5b390739850ec3a3f10923ac78434e7cd4d35ed55ae05916aa1a34668894df3 | f78bea4a0bcf7d7a5eb91f66940154cbafe27f271552b70f5a3317bd57b0f393 |
| H05-FAILED_REPAIR | e581e7e9b58094abde70ecd1249fe41a4390fb263764c36e319cebf601ea9522 | efdfc99ae5ee1e9951b007613245bcb348275a78e468d46d1a692975708b440b |
| H09-REPAIR | b23c2e24c260bff73f7da24f7a6869c5b13dbeaff8a2440b3442939562ed3aee | c14c5f9b08f2b71185c0ec94f8e94363cfec9d77fe8e18cbf4b1d7c3e597473b |
| H09-FAILED_REPAIR | 52a634d37b4b3272ebce8c4566d82df60e72fc545b4c4b59b5220bc1d7aa7584 | 7d5412812d4c254e8d668291ca835b6773f8118c9d3728d42587d499fe8fbe93 |

## 历史/既有工作树与审阅边界

| E06只读文档 | 冻结SHA256 = 本轮磁盘SHA256 |
| --- | --- |
| docs/evaluation/PHASE_5_EPOCH_06_JUDGE_VALIDATION_REPORT.md | 0b2dd6f13c6de10a83ce934da4240b8ffa55afec285bf56a5c31e430c65a102a |
| docs/evaluation/judge/PHASE_5B_EPOCH_06_STATIC_PATCH_AUDIT.md | baba70d65a3b3940ccd0a7b3e06d6df8ed5b81500f12e0be073078d945eb21d8 |
| docs/evaluation/judge/PHASE_5B_EPOCH_06_RUN_EVIDENCE.json | 960805d571c6a6dcf8787a74286d5c83aec4368dc2b1fb2180bec5f76993caab |
| docs/evaluation/judge/PHASE_5B_EPOCH_06_JEV_RESULTS.json | e3f3c26053ed711b1cfa685d76fe419336af08e0b12f45eedec93c14d4a0cf6b |
| docs/evaluation/judge/PHASE_5B_EPOCH_06_DEEPSEEK_BASELINE_RESULTS.json | a3357b841921f7b6e4686046c67ac4d58d685e2fd5a3ac4a1a6e1ec22b3e822e |
| docs/evaluation/judge/PHASE_5B_EPOCH_06_RELIABILITY_AND_COMPARISON.md | 2cfc49423088e3c0156222b382dcc96ab452cdc775c10d7b5b964e4283a1d444 |
| docs/evaluation/judge/PHASE_5C_EPOCH_06_FALLBACK_PROOF.md | 8d8d53bbd39d9566f4d4ba9dba8ac341fdf97edd6b1299ccc68a103f7845f3cc |

3079项保护原文件前后清单SHA均 `1c4cc1d13b01f2a7a6e0eddc3c05ca01bf385d243557d4fed6096e14aaef441a`。E06七文档、114 raw、锁、target构建和历史报告不变；入口3083原文件仅上表4项有授权变化。现有dirty/untracked明细逐文件SHA提供在本轮 `final-file-manifest.json`，入口Git状态保存在 `entry-git-status.json`。HEAD `6076a70f18f3062b8faff4fc179cf21f00a95036` 不变；无git add/commit/push/reset。新报告在项目既有忽略规则下可能不出现在git status，磁盘交付manifest仍逐项登记，未改忽略规则。

下一轮必要审阅：确认/三个争议的完整候选与模型句子、raw hash与评分点；新旧system全文及38Prompt hash；原响应仍拒绝与新增Mock合规成功；未来8个canary每路径原始response、首试有效率及全字段人工归因审阅。Jev-judge-v3不是数值rubric-v3，rubric/请求树仍冻结。文字增量token/cost/latency与质量效果尚未实测。

**STOP_WAIT_COMMANDER。**
