# Phase 5C controlled fallback — NOT_RUN

5B 首个真实 Jev 响应未通过生产 score/probability 一致性校验。按恢复指令首次真实调用遇到协议问题立即停止，因此没有启动受控本地服务，没有调用故障注入路径，也没有调用真实 DeepSeek fallback。

```text
CONTROLLED_FALLBACK_JEV_ATTEMPTS: 0
CONTROLLED_FALLBACK_DEEPSEEK_CALLS: 0
CONTROLLED_FALLBACK_PASS: NOT_RUN
LOCAL_FAILURE_SERVER_CREATED: NO
```

5B 中生产请求 fallback 一次，但被首调用停止观察器拦截；实际 fallback HTTP=0。这不是 5C 的一次 Jev 故障→一次真实 DeepSeek fallback 证明，不计作通过。
