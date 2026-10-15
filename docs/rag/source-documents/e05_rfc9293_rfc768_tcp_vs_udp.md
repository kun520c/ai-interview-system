# TCP 与 UDP：语义与应用取舍

> Title: TCP 与 UDP：语义与应用取舍
> Bank ID: E05
> Knowledge category: NETWORK
> Knowledge point: tcp_vs_udp
> Difficulty: EASY
> Target question: TCP 和 UDP 有什么主要区别？实际开发中分别适合哪些场景？
> Version scope: TCP 依据 RFC 9293（2022）；UDP 基础协议依据 RFC 768（1980）。
> Primary official source: [TCP]
> Secondary official sources: UDP（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

TCP 是面向连接的可靠、有序字节流协议，通信双方维护连接状态；UDP 提供数据报服务，没有 TCP 式 SYN 握手建立过程。字节流不保留应用每次写入的消息边界，应用需自行分帧；UDP 保留数据报单位，但这种边界不代表报文一定到达。[TCP][UDP]

## 2. Main mechanism

TCP 使用序列号识别字节位置，以 ACK 确认已接收数据，通过丢失检测、校验和与重传修复传输错误或丢失，并向应用提供有序字节流。UDP 基础协议没有同等的确认、重传、顺序与去重保证；如果业务需要这些能力，须由应用或所采用的上层协议另行实现。[TCP][UDP]

两种基础服务可以这样比较：

| 特征 | TCP | UDP |
|---|---|---|
| 应用看到的数据 | 字节流 | 数据报 |
| 连接与状态 | 有连接状态 | 无 TCP 式建立流程 |
| 可靠有序交付 | 协议提供 | 基础协议不保证 |

TCP 的可靠性也不是“网络永不失败”：连接可因故障终止，可靠交付的协议语义不能证明服务端已经完成某项业务操作。[TCP]

## 3. Important boundary

TCP 通过接收窗口进行流量控制，避免发送方超过接收方处理能力；还要求拥塞控制来调节对网络的发送负载。连接状态、确认与重传等维护成本通常高于 UDP 基础服务，但不能据此得到每个业务请求都更慢的结论。[TCP][UDP]

协议选择属于工程取舍：需要可靠、有序数据传输时，TCP 可减少应用自行实现的工作；允许部分旧数据失效、需要数据报边界或已有上层可靠性控制时，可以考虑 UDP。选择 UDP 后仍需处理丢包、重复、乱序及网络负载；需求若不允许丢失，就必须说明可靠性由哪一层承担，不能将责任留空。[TCP][UDP]

## 4. Common misconceptions

“UDP 总比 TCP 快”没有限定负载、网络与业务语义；“用 UDP 就有上层协议的可靠性”把不同层的能力混在一起。某个 UDP 上层协议可以自行增加可靠传输机制，这种能力不能反过来归于 RFC 768 的 UDP 本身。比较时先说明可靠性、时延、消息边界与控制责任，再选择协议；握手和关闭状态细节属于连接生命周期主题。[TCP][UDP]

## 5. Official references

- [TCP] RFC 9293，§2.2、§3.7、§3.8.1–3.8.3、§3.8.6，TCP 服务与控制
- [UDP] RFC 768，Introduction、Format、User Interface，UDP 数据报服务

[TCP]: https://www.rfc-editor.org/rfc/rfc9293.html
[UDP]: https://www.rfc-editor.org/rfc/rfc768.html
