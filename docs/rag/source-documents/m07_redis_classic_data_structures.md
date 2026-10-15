# Redis 五类常用数据结构与选择

> Title: Redis 五类常用数据结构与选择
> Bank ID: M07
> Knowledge category: REDIS
> Knowledge point: redis_data_structures
> Difficulty: MEDIUM
> Target question: Redis 常用数据结构有哪些？如果要分别实现对象缓存、元素去重、按顺序保存数据和排行榜，你会选择什么数据结构？为什么？
> Version scope: Redis 官方文档，核验日期 2026-10-06；仅经典结构与单元素命令，不声明项目 Redis 运行版本或依赖。
> Primary official source: [REDISTYPES]
> Secondary official sources: STRING, HASH, LPUSH, RPOP, SET, ZSET（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

String、Hash、List、Set、Sorted Set 是 Redis 常见的五类通用数据结构，并非 Redis 全部类型的穷尽清单。选择需区分单值、字段、序列、唯一成员和带 score 的成员。Redis 并不只是保存字符串的简单 Key-Value。[REDISTYPES]

## 2. Main mechanism

String 适合普通缓存值，也可以用 INCR 表达整数计数器；计数操作以符合命令要求的数值字符串为前提。Hash 是 field-value 集合，适合保存对象的多个字段，使用 HSET/HGET 可单独写读字段，避免每次都按一个整体值处理。[STRING][HASH]

List 是有序序列，适合从两端操作元素。单元素 LPUSH 在头部插入，RPOP 从尾部取出并移除，配合可形成按入队顺序消费的简单队列。List 可以有重复元素，不能用序列自动替代去重需求。[REDISTYPES][LPUSH][RPOP]

```text
INCR visits
HSET profile:1 name Kun
LPUSH tasks taskA
RPOP tasks
```


## 3. Important boundary

Set 的成员唯一且无序，适合去重、成员判断和交集、并集、差集：重复加入同一成员不会产生第二份成员。Sorted Set 同样保持成员唯一，但每个成员关联 score 并按 score 排序，适合排行榜或按权重、时间排序。不同成员可以有相同 score，此时按成员的字典序区分，不是 score 也必须唯一。[SET][ZSET]

```text
SADD visitors userA
ZADD ranking 100 userA
```

对象字段缓存可考虑 Hash；整个对象序列化为一个单值也可用 String。元素去重用 Set，有序两端列表用 List，排行榜用 Sorted Set。这些是基于结构语义的选择，仍需明确更新与读取模式；命令示意不是本仓库已实现的功能。[STRING][HASH][SET][ZSET]

## 4. Common misconceptions

“Redis 只有五种类型”与官方完整概览不符；“有序”也要区分 List 的序列位置与 Sorted Set 的 score 排序。简单 LPUSH/RPOP 不提供完整的消费确认、崩溃恢复或业务幂等协议，不能直接称为可靠消息系统。

本文不展开 H07 锁算法，也不声明项目 Redis 依赖。latest 文档含新功能，本题只用经典语义，不采用新增选项或 RPOP count。[REDISTYPES][LPUSH][RPOP]

## 5. Official references

- [REDISTYPES] Redis Data types，五类概览
- [STRING] Redis Strings，Introduction / counters
- [HASH] Redis Hashes，字段操作
- [LPUSH] Redis LPUSH，Description，单元素头部插入（自 1.0.0）
- [RPOP] Redis RPOP，Description，单元素尾部移除（自 1.0.0）
- [SET] Redis Sets，唯一成员与交并差
- [ZSET] Redis Sorted sets，唯一成员、score、排行榜

[REDISTYPES]: https://redis.io/docs/latest/develop/data-types/
[STRING]: https://redis.io/docs/latest/develop/data-types/strings/
[HASH]: https://redis.io/docs/latest/develop/data-types/hashes/
[LPUSH]: https://redis.io/docs/latest/commands/lpush/
[RPOP]: https://redis.io/docs/latest/commands/rpop/
[SET]: https://redis.io/docs/latest/develop/data-types/sets/
[ZSET]: https://redis.io/docs/latest/develop/data-types/sorted-sets/
