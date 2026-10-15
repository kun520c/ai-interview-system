# equals 与 hashCode：契约与哈希集合

> Title: equals 与 hashCode：契约与哈希集合
> Bank ID: M01
> Knowledge category: JAVA_BASIC
> Knowledge point: equals_hashcode_contract
> Difficulty: MEDIUM
> Target question: `equals()` 和 `hashCode()` 之间有什么约定？如果只重写 `equals()` 而不重写 `hashCode()`，在 HashMap / HashSet 中可能出现什么问题？
> Version scope: Java SE 21 Object/HashMap API；查找机制示意固定为 OpenJDK jdk-21+35。
> Primary official source: [OBJECT]
> Secondary official sources: HASHAPI, HASH21（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

equals 定义对象的逻辑相等关系，hashCode 提供哈希数据结构使用的整数哈希值。Object 默认 equals 按对象身份比较；业务类若改为按字段定义相等，应遵守自反、对称、传递、一致性，并保证非 null 对象与 null 比较为 false。这里的一致性前提是参与比较的信息没有发生改变。[OBJECT]

## 2. Main mechanism

若 a.equals(b) 为 true，则 a 与 b 必须返回相同 hashCode；反过来，相同 hashCode 不保证 equals 为 true，不同对象允许发生哈希碰撞。同一对象在一次程序执行中，参与 equals 的信息未改变时，hashCode 应保持一致，规范不要求跨程序执行永远相同。[OBJECT]

HashMap 先使用 key 的 hash 缩小候选桶，再在候选节点中检查 hash，并通过引用相同或 equals 判断是否为同一个逻辑 key。固定 Java 21 源码中的 getNode、putVal 体现此流程。HashSet 的去重同样要求键的相等与哈希契约协调；不是仅比较两个整数就认定元素相等。[HASHAPI][HASH21][OBJECT]

## 3. Important boundary

只重写 equals 而沿用不匹配的 hashCode，可能让两个逻辑相等的对象产生不同 hash，因而查找不到预期映射，或被当作两个不同 key/集合元素。这个反例强调的是“可能”，不是每次都必然失败：偶然相同的 hash 或某次碰巧找到，不能修复契约违规。[OBJECT][HASH21]

例如相等关系只依据稳定的 userId，就应让 hashCode 也依据与该关系相容的信息计算，而不能加入 equals 根本不比较的随机字段。修正职责是配套实现两者并保持所有契约；不是要求返回唯一编号，也不是要求把所有字段无条件纳入相等关系。

```text
a.userId == b.userId → equals 为 true
则必须保证 a.hashCode() == b.hashCode()
反向推理不成立：同 hash 的不同 userId 仍可不相等
```


## 4. Common misconceptions

把“哈希碰撞”当成程序错误会误解契约：碰撞允许存在，集合需要后续相等判断。真正危险的是逻辑相等却提供不一致的哈希值。

另一个风险是在 key 已进入 HashMap 后修改参与 equals/hashCode 的字段：节点保存的是插入时的 hash，而后续查找可能按新 hash 定位，结果不再符合预期。这是由源码查找方式推导的使用边界，工程上可优先使用相等信息稳定的键；不把这条建议扩大为所有可变对象都不能存在。[OBJECT][HASH21]

## 5. Official references

- [OBJECT] Object API，Java SE 21，equals/hashCode 契约
- [HASHAPI] HashMap API，Java SE 21，类说明及 key 操作
- [HASH21] OpenJDK jdk-21+35 HashMap.java，hash/getNode/putVal

[OBJECT]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html
[HASHAPI]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html
[HASH21]: https://raw.githubusercontent.com/openjdk/jdk/jdk-21%2B35/src/java.base/share/classes/java/util/HashMap.java
