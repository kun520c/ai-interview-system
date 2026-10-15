# E04 DeepSeek 第二例 JSON 精确取证

结论：零模型API调用复现真实非法内层JSON。HTTP外层对象已由E04观测器成功解析；`choices[0].message.content` 解码后的字符串第10行包含未转义ASCII双引号，原生产DeepSeekEvaluationClient.parseSuggestion严格拒绝是合理边界。该错误不属于业务字段不合规，也不是之前临时检查器的范围/数字类型误判。本取证未修复原内容、调用线上模型、修改生产代码或历史E04文件。

取证材料：E04汇总、RUN_EVIDENCE第6个推理事件（E02-PARTIAL / BASELINE）、实际RunEpoch04.java、CapturedBaselineProtocolDiagnostic.java及其诊断JSON，当前DeepSeekEvaluationClient.java与EvaluationPromptBuilder.java。E04七报告与所有历史清单SHA校验通过；E04结束时480项旧源码/证据也均保持原样。E05当前完整冻结清单805项包括E04目标/日志/锁/报告与编译产物。

| 层级 | 结论 |
| --- | --- |
| HTTP响应外层JSON | E04真实观测器readTree成功，HTTP200；message.content是可读取的字符串，不是外层转义错误 |
| 内层JSON语法 | FAIL；Jackson和Python严格解析在同一字符位置失败 |
| 业务字段/Java评分 | 没有到达；不能将内层看起来像分数的数值算成有效结果 |

原始片段来自公开冻结的合成benchmark回答，而非真实用户私有回答；未包含Credential/Authorization。第10行如下：

```text
"对不确定的部分能坦诚说明"还没有形成清楚的判断"，没有强行编造。"
```

`说明`之后、`还`之前的U+0022双引号未被反斜杠转义，过早终止strengths中的JSON字符串。紧随的`还`成为意料之外的字符。Jackson报告第10行第19列、charOffset=290；未转义引号位于第10行第18列、零基offset=289。Python json.JSONDecodeError同为line10/column19/pos290；UTF-8字节偏移为422。本例前部没有代理对或CRLF，Java UTF-16和Python代码点偏移在该位置相同。列为1基，offset为0基。后续内层也出现其他未转义引号，解析器在第一处停止，无需猜测修复后内容。

原生产离线反射仅调用private parseSuggestion，异常链：ExternalServiceException（DeepSeek评价结果不是可解析的JSON对象）→JsonMappingException→JsonParseException；后二者均line10/column19/charOffset290。原始捕获内容SHA `5f22e991b0b3b07f35d5531690e253423828a5f4f6d5f1a6e0efff0c0072e5e8` 与E04离线诊断一致。严格重放仍失败，没有启用JSON5、宽松Jackson、正则补引号或字符猜测。

## 请求参数的真实摘要证明

E04并未保存请求正文文件，但观测器在实际HTTP执行前记录了原请求body SHA。使用当前未变的生产private buildRequest与E04冻结system/user Prompt、model/maxTokens，离线重建字节SHA与真实body SHA精确相等：`fa26a790dda2d90666887df9a9205743c9c35c686c5bdeec281ac789467cb226`。因此下列参数并非仅从源码猜测：

| 参数 | 真实请求重建（与捕获字节摘要一致） |
| --- | --- |
| model | deepseek-flash |
| response_format | type=json_object |
| thinking | type=disabled |
| max_tokens | 4096 |
| stream | false |
| temperature / tools | 未发送，保持原生产参数 |
| system JSON指示与schema示例 | 均已存在 |

真实返回model=deepseek-flash，finish_reason=stop，HTTP200；prompt_tokens3422、completion_tokens812、total_tokens4234；cache hit768 / miss2654。没有length截断或空content的观测证据，响应未给实际扣费金额。

官方[JSON Output说明](https://api-docs.deepseek.com/zh-cn/guides/json_mode/)要求JSON格式参数、消息中的JSON指示及示例，并提示可能出现空content；[Chat Completions说明](https://api-docs.deepseek.com/zh-cn/api/create-chat-completion/)将json_object定义为有效JSON输出模式、disabled定义为非思考模式，解释length可能截断。本次请求已符合这些前置要求，不能建议再次“开启JSON mode”。本次实际观察与JSON输出保证不一致；供应商解码约束、服务端后处理或其他内部原因均未证实，不能以这些猜测代替事实。

证据局限：E04保存了解析后的外层对象和实际外层字节SHA `2f5f4897316f50dfeaa88bfaa2b4e9ac0554629d5091a252d571cf03dd303bf0`，没有外层HTTP原始字节文件，故不能恢复其字节级空白布局。内层字符串在完整历史文件中保留、SHA一致、双解析器及原生产异常一致，足以证明内层问题；不声称抓取了TLS线上的服务端原包。

## 后续备选项（仅提出，未实施）

1. 带脱敏事件ID、参数摘要和原始非法片段向供应商排查：需要人工/Commander批准对外联系；不会由本轮发送。
2. 保持严格拒绝并记录失败：不改变业务可信边界；本轮E05只在临时测量harness隔离该类异常，取得可靠性分布。
3. 另行授权后，仅对确证非法模型JSON进行有限重试：需明确调用上限、成本、幂等及原始失败计数，存在掩盖首尝试可靠性的风险；本轮禁止重试。
4. 另行授权Prompt或结构化协议改造：会改变冻结比较条件，需要新版本/新epoch并重新验证；本轮不实施。

不将自动修复、宽松解析或更换模型列为本轮实现。密钥只用于后续子进程，取证阶段无API凭证输出、无单独密钥hash。完整重放和位置证明在target/phase5-epoch05/e04-forensics.json、e04-exact-positions.json；E01-E04原文件均不被覆盖。E04取证PASS，允许进入E05零模型调用preflight。
