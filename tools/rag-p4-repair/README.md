# P4 第五题中断修复：纯离线入口

```powershell
pwsh -NoProfile -File .\tools\rag-p4-repair\reverify.ps1
```

只读g01～g05已保存响应；校验12道冻结输入与P3一致性、五次HTTP请求内容及UTF-8字节；分别编译归档原校验器和修复校验器，逐题并列复验。运行专项回归及原Java Mock测试，所有新编译和报告写入target/rag-p4-repair/support-point-v1下独立时间目录。没有HTTP请求入口或密钥参数。

最小修复位于tools/rag-p4/SemanticEvidenceGate.java：同一候选可使用多段原文支持同一已声明pointId；每段分别检查，覆盖按不同核心点去重。未知ID、伪造引用、标题/纯链接/参考列表、版本冲突及缺少依据仍拒绝。

负例测试同时补强嵌在Markdown链接中的纯URL，以及`- Primary/Secondary:`参考条目的识别，避免引用这些低信息内容通过检查。其余语义准入和原prompt保持不变。

原始源码和全部历史SHA记录在target/rag-p4-repair/support-point-v1；原P4的prepared/离线编译/历史响应/结果/锁保持不变。原freeze仍引用旧源码，绝不重新封印旧ExecuteOnce。新源码导致旧入口的源码封印不再匹配是预期状态，不以改freeze解除它。

g01 c2语义误拒单独记录为AI工程师的PROVISIONAL建议，不更新用户标签，不自动覆盖模型原判。离线复验PASS不代表完整语义质量或独立泛化PASS。

后7题只有supplement-plan.json独立方案与费用；不续跑旧入口，不删除锁，不新建可收费入口。本阶段不需要也不请求额外收费授权。
