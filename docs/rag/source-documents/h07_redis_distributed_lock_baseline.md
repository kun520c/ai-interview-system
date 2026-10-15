# Redis 分布式锁：互斥、租约与安全释放

> Knowledge category: `REDIS`
> Target question: H07 — Redis 分布式锁的获取、所有权校验与安全释放
> Version scope: 经典单实例锁基线为 Redis >= 2.6.12（`SET` 支持 `NX`、`PX`）；`EVAL` 自 Redis 2.6.0 可用。本文不推断本地 Redis 版本，也不描述项目当前运行时依赖。
> Primary source: https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/

## 1. Core model

带过期时间的 Redis 锁是有时限的租约，而不是持有者永久拥有资源的证明。基础目标是在一个 key 上一次只允许一个客户端成功取得锁，并让锁在客户端失败后最终自动过期。只用 `SETNX` 设置 key、再用另一条命令设置 TTL 存在命令间隔：如果进程在两条命令之间停止，key 已被设为锁却还没有过期时间，锁可能长期残留。获取与过期时间应由同一条 `SET` 命令完成。

## 2. Main mechanism

经典单实例获取方式是在一条命令中同时使用条件写入和过期时间：

```text
SET lockKey uniqueToken NX PX ttlMilliseconds
```

`NX` 表示仅当 key 不存在时才设置；`PX` 将毫秒级 TTL 与这次设置一起提交。成功响应表示该客户端取得当前租约，未成功则没有取得锁。Redis `SET` 文档记录 `NX` 与 `PX` 选项从 2.6.12 起可用，因此这是本文件采用的最低兼容基线。设置 TTL 可在客户端异常退出后释放遗留 key，但租约长度也限定了客户端可以安全完成临界区工作的时间窗口。

锁 value 应是本次锁请求唯一的 token，而不是所有请求共用的固定字符串。释放时不能直接执行无条件 `DEL lockKey`：客户端 A 的租约可能先过期，客户端 B 随后取得同名锁；若 A 的迟到释放命令直接删除 key，就会删除 B 的锁。安全释放需要先比较 key 当前 value 与 A 保存的 token，仅匹配时才删除，并且比较与删除必须作为一个原子操作执行。

在该兼容基线中，可用 Lua 脚本执行 compare-and-delete，并通过 `EVAL` 调用：

```lua
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('del', KEYS[1])
end
return 0
```

脚本在 Redis 端完成 token 检查和删除，其他命令不会插入到这两个动作中间。调用方把锁 key 作为 `KEYS[1]`、自己的唯一 token 作为 `ARGV[1]` 传入。Redis 官方 `EVAL` 命令文档记录其自 2.6.0 可用；因此 `SET ... NX PX` 加唯一 token 与 Lua/EVAL 原子释放形成 Redis >= 2.6.12 的经典实现基线。

## 3. Important boundary

TTL 到期只表示 Redis 允许其他客户端再次尝试取得该 key，不会终止已经运行的旧客户端代码。若 A 的业务工作超过 TTL，B 可能已取得新租约，而 A 仍在执行；因此仅设置过期时间无法保证临界区无论运行多久都互斥。应用应按工作时长与故障恢复需求设计租约，并把续租视为需要正确所有权校验和失败处理的额外策略，而不是 Redis 自动赋予的锁属性。

对关键业务不变量，Redis 锁不能替代权威数据存储本身的约束或条件更新。这个工程边界来自租约会到期、客户端可能仍继续运行这一事实：如果重复写入会破坏正确性，最终数据层仍应校验唯一性、版本或业务条件。Redis 锁可用于协调竞争，但不能据此取消数据库已经需要的正确性约束。网络分区使持锁客户端无法联系 Redis 时，客户端无法确认或完成释放；仍可访问 Redis 的竞争者可能要等 TTL 到期后才能再次获取。单实例方案也不能被理解为覆盖所有节点故障：若 Redis 主节点在锁写入复制到副本前故障，异步复制下的副本提升可能使另一个客户端取得同名锁。Redis 官方资料将互斥安全性、可用性与故障场景分开讨论；本文说明这些边界，但不展开多实例算法。

## 4. Common misconceptions

“`SETNX` 成功就构成完整可靠的锁”忽略了原子过期设置；“锁一定会过期，所以任何客户端都能直接 `DEL`”忽略了过期后可能已有新持有者；“TTL 设置正确就能保护任意长的临界区”忽略了租约边界。经典做法是一次 `SET key token NX PX ttl` 获取、用请求唯一 token 标识所有权，并以 Lua/EVAL 原子比较和删除。本文的 Redis >= 2.6.12 是命令兼容基线，不是本地环境版本声明；`EVAL` 的最低版本单独为 2.6.0。

## Official references

- Primary: [Redis — Distributed Locks with Redis](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/) — 锁安全性、唯一随机值、租约时长、释放与故障边界。
- Secondary: [Redis `SET` command](https://redis.io/docs/latest/commands/set/) — `NX` / `PX` 选项及版本历史。
- Secondary: [Redis `EVAL` command](https://redis.io/docs/latest/commands/eval/) — 服务端 Lua 执行与可用版本。
