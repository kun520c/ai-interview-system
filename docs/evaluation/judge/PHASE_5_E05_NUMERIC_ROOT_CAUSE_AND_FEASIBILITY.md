# E05 Jev 数值根因与可行性取证（Gate A/B）

结论：五份原HTTP响应均可用**联合概率守恒和两位显示量化**解释；五个失败维度均给出显式、严格和为1、能按HALF_UP舍入复现显示概率和score的witness。三个长小数是最近cent值的一个相邻binary64数，另外两个显示和为0.99。旧生产校验对五例仍全部拒绝；新版本仅离线数值校验5/5接受。**E05历史仍5次INVALID；这些回放不是新鲜AI质量样本，也没有补做缺失反馈。**

本轮真实Jev/DeepSeek/Embedding API、真实fallback均0。未读取API Key值、未创建datasource/Milvus客户端。原E05总报告、比较报告、RUN_EVIDENCE及两份38 RESULTS交叉核对：109尝试=38/33/38，配对32/38、native33/38、baseline37/38，原报告字节未改。

## 原始字节与旧异常

每个原响应SHA验证后按原实际返回jev-1.13.0审计所有score/legend/probability/type/confidence与choice。原请求所有question keys与response keys相同；score legend逐项等于请求rubrics，全部概率有限且在[0,1]、confidence有限且在[0,1]，choices为合法选项并满足原argmax与严格sum门禁。原错误来自score交叉数值校验，非HTTP/JSON/模型身份/字段缺失。

| case | 原raw响应文件 | raw响应SHA256 | 实际请求body SHA256 |
| --- | --- | --- | --- |
| E02-UNKNOWN | target/phase5-epoch05/http/10.response.txt | `df5336ca5a17d785a6aed7990140472978610047464aa4f0896a44ab2255d373` | `e6fa682ebecae005994ee1e1d17526482dbde970dc5856fb5c37c8e7210fa7a5` |
| M04-PARTIAL | target/phase5-epoch05/http/24.response.txt | `25dde10873d31c9c2544a712123e5d83b89358ed0d4e0acb04bbb891ada9637f` | `3b77c844de5f0b310151975b8dd82362df95893c26d9a028c3698747bc3bfa95` |
| M04-BOUNDARY | target/phase5-epoch05/http/38.response.txt | `1fe7ce4bdbe74ae23567f825a2ed5be7d6ae09f87f5f7dde90423657a1d5730e` | `681329189e019659ae4aea660321b3dd8228af177b41a8f8a75a476e0da7a1bb` |
| H05-EXCELLENT | target/phase5-epoch05/http/40.response.txt | `410a2cc9bd9c596c84ef1e6befb03bac194548c1eb244affe1a00b55f7bfdb64` | `88bce627fc33fff3983d2d4dcfce9514fe1c8d7b119e27c4d530642e945e624a` |
| H05-UNKNOWN | target/phase5-epoch05/http/48.response.txt | `1a8e1ce963c80d5a787d6d941f64de41a9cba7dc76f496cf30cc39c6c775af90` | `7d39ec60c84651da8da0709c3d50af8ab154bf68d4502fc15c6102d9202d33a9` |

纯离线调用旧编译产物的private validateAnswers，五例均得到ExternalServiceException：`Jev分数或概率分布不一致`，堆栈位置`JevJudgeProvider.validateAnswers(JevJudgeProvider.java:187)`。这是E05入口旧版行号，源码副本保存在target/phase5-offline-hardening/before/；不将新行号冒充事故位置。完整重放在native-replay-before.json / native-replay-after.json。

## 显式witness：原数字不变

概率顺序为p0..p4，E=Σi·pi。每一行witness的概率均在[0,1]、和精确1；按HALF_UP保留两位得到最近显示cent值，witness E按HALF_UP两位得到原score。三个噪声token由该显示cent的直接相邻binary64表示复现资格；这里只证明可解释性，不声称知道供应商实际采用了这个p*。

| 失败case/维度 | 原score | 原上报p0..p4 | 原可见sum / E | 独立p* | E* | 回放资格 |
| --- | ---: | --- | --- | --- | --- | --- |
| E02-UNKNOWN / clarityScore | 2.42 | 0.01, 0.12, 0.35, 0.47000000000000003, 0.05 | 1.00000000000000003 / 2.43000000000000009 | 0.0125, 0.12, 0.35, 0.47, 0.0475 | 2.4200 | 旧INVALID → 离线数值接受 |
| M04-PARTIAL / practiceScore | 1.96 | 0.02, 0.22, 0.54, 0.2, 0.01 | 0.99 / 1.94 | 0.022, 0.222, 0.542, 0.202, 0.012 | 1.960 | 旧INVALID → 离线数值接受 |
| M04-BOUNDARY / clarityScore | 3.34 | 0.0, 0.0, 0.05, 0.55, 0.39 | 0.99 / 3.31 | 0, 0, 0.054, 0.552, 0.394 | 3.340 | 旧INVALID → 离线数值接受 |
| H05-EXCELLENT / completenessScore | 3.81 | 0.0, 0.0, 0.0, 0.18, 0.8200000000000001 | 1.0000000000000001 / 3.8200000000000004 | 0.0025, 0, 0, 0.18, 0.8175 | 3.8100 | 旧INVALID → 离线数值接受 |
| H05-UNKNOWN / correctnessScore | 0.09 | 0.9299999999999999, 0.05, 0.01, 0.0, 0.01 | 0.9999999999999999 / 0.1100000000000000 | 0.934, 0.054, 0.006, 0, 0.006 | 0.090 | 旧INVALID → 离线数值接受 |

`0.47000000000000003`为nextUp(0.47)，`0.8200000000000001`为nextUp(0.82)，`0.9299999999999999`为nextDown(0.93)。相邻关系独立由Python math.nextafter检查，并由Java nextUp/nextDown正反例验证。旧BigDecimal.valueOf(...).scale>2使兼容资格失败；可见E与score差又超过原0.001，因此旧拒绝可精确重现。显示和0.99的两例被旧原始sum容差0.0001先行拒绝，虽然潜在量化分布可守恒。

可信度：原字节/数值、旧拒绝原因、相邻关系和数学可行性 **confirmed**；上游二位量化与浮点计算/序列化造成这些token **likely**；供应商内部真实分布、运算路径、tie策略 **unknown**。不能把显示问题结论外推至新模型、任意高精度输出或语义评分正确性。

## 固定规则与数学证明

原严格通道保留：|Σp−1|≤0.0001，score问题还要求|Σi·pi−score|≤0.001。与原一致的真高精度输出及未知模型仍可走此严格通道；不是全面禁止高精度。

仅返回model严格等于jev-1.13.0的score问题可走第二通道。每个score/p先定位最近0.01网格center；只有原Double等于center或其直接nextUp/nextDown之一才有资格。使用一个可表示相邻数，未使用任意绝对epsilon、两邻居、benchmark调参或字段归一化。原原始范围/有限性必须先通过，因此nextUp(1)概率、负概率、nextUp(4)分数等不会借网格投影越界。0和−0按原数值比较等价，原序列化和整数映射仍保留原值。

在精确十进制证明域，对显示cent q_i设置L_i=max(0,q_i−0.005)、U_i=min(1,q_i+0.005)，score显示s设置S_L=max(0,s−0.005)、S_U=min(4,s+0.005)。边界是**闭区间**，保持此前已批准的nearest-rounding并允许half-step tie边界；没有新增epsilon。供应商tie规则仍未知，本轮五个显式witness按HALF_UP成立且未依赖有争议的上界tie。0/1概率、0/4 score均截断，不使用对称的虚假越界预算。

首先要求ΣL≤1≤ΣU。取p=L，剩余质量r=1−ΣL；求最小期望时把r按i=0→4分配至U−L容量，最大时按i=4→0分配。这是线性函数在盒约束与质量守恒平面上的精确极值：若存在较大i上的可移质量和较小i上的可用容量，把质量移到较小i只会降低E；重复交换给出贪心最小，最大同理。可行集合凸且连通，E的值域正是[E_min,E_max]；与score区间有交集当且仅当S_L≤E_max且S_U≥E_min。**质量和与score联合检查，不能只看误差总预算。**

生产helper只用center建立此可行域，不修改原response.score、probabilities、confidence、legend；mapScore继续用原score×5、HALF_UP整数映射。choice仍使用原严格sum与argmax，不扩大本轮score显示假设。五维逐项通过后才能生成反馈；任一维失败继续原一次fallback逻辑。

反例：p4=1的单位质量约束使E_min=3.98，而score3.97最多允许3.975，交集为空，仍拒绝。严重sum偏差、0总质量、真实三位不一致、超过一个相邻数、未知model、非法keys/legend/null/NaN/Inf/choice等均保留拒绝。不会把sum阈值改为0.02，也不会除以显示总和。

## 全25个score维度审计

各case所有5维均有原严格关系或独立区间/witness。gate-a-numeric-proof.json同时保存精确有理数p*、原legend/choice/confidence与每项证书；不以仅检查失败维度代替整个response。

| case | 维度 | score | 可见sum | 可见E | 单位质量E范围 | score范围 | 可用通道 |
| --- | --- | ---: | --- | --- | --- | --- | --- |
| E02-UNKNOWN | depthScore | 0.01 | 1.00 | 0.01 | 0.005 .. 0.040 | ['0.005', '0.015'] | 严格通道 |
| E02-UNKNOWN | completenessScore | 0.01 | 1.0 | 0.0 | 0.000 .. 0.020 | ['0.005', '0.015'] | 守恒可行域 |
| E02-UNKNOWN | clarityScore | 2.42 | 1.00000000000000003 | 2.43000000000000009 | 2.400 .. 2.460 | ['2.415', '2.425'] | 守恒可行域 |
| E02-UNKNOWN | correctnessScore | 0.03 | 1.00 | 0.01 | 0.005 .. 0.040 | ['0.025', '0.035'] | 守恒可行域 |
| E02-UNKNOWN | practiceScore | 0.02 | 1.00 | 0.02 | 0.015 .. 0.050 | ['0.015', '0.025'] | 严格通道 |
| M04-PARTIAL | depthScore | 2.37 | 1.00 | 2.37 | 2.350 .. 2.380 | ['2.365', '2.375'] | 严格通道 |
| M04-PARTIAL | completenessScore | 2.22 | 1.00 | 2.22 | 2.200 .. 2.240 | ['2.215', '2.225'] | 严格通道 |
| M04-PARTIAL | clarityScore | 2.82 | 1.00 | 2.82 | 2.790 .. 2.830 | ['2.815', '2.825'] | 严格通道 |
| M04-PARTIAL | correctnessScore | 3.22 | 1.00 | 3.22 | 3.190 .. 3.230 | ['3.215', '3.225'] | 严格通道 |
| M04-PARTIAL | practiceScore | 1.96 | 0.99 | 1.94 | 1.935 .. 1.985 | ['1.955', '1.965'] | 守恒可行域 |
| M04-BOUNDARY | depthScore | 3.04 | 1.00 | 3.04 | 3.010 .. 3.050 | ['3.035', '3.045'] | 严格通道 |
| M04-BOUNDARY | completenessScore | 2.85 | 1.00 | 2.85 | 2.820 .. 2.860 | ['2.845', '2.855'] | 严格通道 |
| M04-BOUNDARY | clarityScore | 3.34 | 0.99 | 3.31 | 3.305 .. 3.345 | ['3.335', '3.345'] | 守恒可行域 |
| M04-BOUNDARY | correctnessScore | 3.8 | 1.00 | 3.81 | 3.780 .. 3.820 | ['3.795', '3.805'] | 守恒可行域 |
| M04-BOUNDARY | practiceScore | 2.89 | 1.00 | 2.89 | 2.860 .. 2.920 | ['2.885', '2.895'] | 严格通道 |
| H05-EXCELLENT | correctnessScore | 3.55 | 1.00 | 3.56 | 3.530 .. 3.570 | ['3.545', '3.555'] | 守恒可行域 |
| H05-EXCELLENT | depthScore | 3.79 | 1.0 | 3.8 | 3.770 .. 3.805 | ['3.785', '3.795'] | 守恒可行域 |
| H05-EXCELLENT | practiceScore | 3.92 | 1.00 | 3.93 | 3.900 .. 3.935 | ['3.915', '3.925'] | 守恒可行域 |
| H05-EXCELLENT | completenessScore | 3.81 | 1.0000000000000001 | 3.8200000000000004 | 3.790 .. 3.825 | ['3.805', '3.815'] | 守恒可行域 |
| H05-EXCELLENT | clarityScore | 3.38 | 1.00 | 3.39 | 3.360 .. 3.400 | ['3.375', '3.385'] | 守恒可行域 |
| H05-UNKNOWN | correctnessScore | 0.09 | 0.9999999999999999 | 0.1100000000000000 | 0.085 .. 0.140 | ['0.085', '0.095'] | 守恒可行域 |
| H05-UNKNOWN | depthScore | 0.06 | 1.00 | 0.06 | 0.055 .. 0.090 | ['0.055', '0.065'] | 严格通道 |
| H05-UNKNOWN | practiceScore | 0.03 | 1.00 | 0.02 | 0.015 .. 0.050 | ['0.025', '0.035'] | 守恒可行域 |
| H05-UNKNOWN | completenessScore | 0.05 | 1.00 | 0.05 | 0.045 .. 0.080 | ['0.045', '0.055'] | 严格通道 |
| H05-UNKNOWN | clarityScore | 2.21 | 1.00 | 2.22 | 2.190 .. 2.240 | ['2.205', '2.215'] | 守恒可行域 |

离线解释5/5并非质量PASS；仍须Commander批准的新E06对38/38 primary+feedback及38/38 baseline、反馈忠实度和全部原门槛重新真实验证。
