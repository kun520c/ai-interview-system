# TCP 连接生命周期与状态诊断

> Title: TCP 连接生命周期与状态诊断
> Bank ID: H08
> Knowledge category: NETWORK
> Knowledge point: tcp_connection_lifecycle
> Difficulty: HARD
> Target question: TCP 为什么需要三次握手和四次挥手？TIME_WAIT 和 CLOSE_WAIT 分别为什么出现，数量异常时通常意味着什么？
> Version scope: RFC 9293（2022）TCP；区分普通主动/被动关闭与同时关闭，诊断建议不是状态本身的定罪。
> Primary official source: [TCP]
> Secondary official sources: 无（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

TCP 建立连接需要双方交换并确认初始序列号与状态：每方发送自己的 SYN，也确认对方 SYN。三次握手的重要目的之一是防止旧的重复连接请求造成状态混乱；接收第一份 SYN 的一方不能仅凭该报文就判定它属于新的有效连接，需得到对方的后续确认。[TCP]

## 2. Main mechanism

常见建立过程将一方的 SYN 与对另一方 SYN 的 ACK 合并：

```text
A → B：SYN，初始序列号 x
B → A：SYN + ACK，初始序列号 y，确认 x+1
A → B：ACK，确认 y+1
```

这说明三报文怎样完成双向确认，不是“只有三次才有可靠性”的口号。旧 SYN 可能导致错误半开状态，确认与序列号检查可用于识别并恢复；握手还存在同时打开等情形，本篇以常见主动连接为说明。[TCP]

TCP 是全双工，两个发送方向可以独立结束。一方发 FIN 表示自己不再发送，对方确认后仍可发送自己的剩余数据；对方应用也完成发送时再发 FIN，最后获 ACK，因此常见关闭为 FIN、ACK、FIN、ACK 四个报文。[TCP]

## 3. Important boundary

四报文是普通序列，不是任何抓包都必须正好四包：ACK 与本端 FIN 可合并，也可能重传；同时关闭还有不同状态路径。半关闭意味着发送方向结束不必立刻终止另一个接收方向。[TCP]

普通主动关闭端在确认对方 FIN 后进入 TIME-WAIT，等待 2MSL：它可在对方因最后 ACK 丢失而重传 FIN 时再次确认，并避免旧连接延迟报文影响后续连接。MSL 是报文最大生存时间，不在本文固定成某个系统秒数。同时关闭可使双方进入 TIME-WAIT，因此不能说该状态只可能出现在客户端。[TCP]

## 4. Common misconceptions

CLOSE-WAIT 表示本地已收到远端 FIN，但正在等待本地应用完成关闭；本地 close 后才进入后续发送 FIN 与等待确认的路径。若大量连接长期停留于此，可检查关闭遗漏、异常路径资源释放或处理阻塞；状态短暂出现本身合法，不能仅凭一次数量快照就证明资源泄漏。[TCP]

TIME-WAIT 多可与短连接和主动关闭频率有关，不天然异常；若资源耗尽，应结合流量、连接寿命、关闭方向及本地资源限制判断。诊断不能把两种状态都归咎为“网络差”，也不能无条件删除协议等待来掩盖问题。这些是基于状态含义的工程排查建议，未进行抓包或网络测试。

## 5. Official references

- [TCP] RFC 9293，§3.3.2、§3.4.1、§3.5、§3.6、§3.10.4/3.10.7/3.10.8

[TCP]: https://www.rfc-editor.org/rfc/rfc9293.html
