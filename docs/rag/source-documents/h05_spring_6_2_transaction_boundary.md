# Spring 6.2 事务边界与回滚规则

> Title: Spring 6.2 事务边界与回滚规则
> Bank ID: H05
> Knowledge category: SPRING
> Knowledge point: transaction_boundary
> Difficulty: HARD
> Target question: Spring 事务有哪些常见失效场景？如果一个业务方法同时调用多个数据库操作，应该如何设计事务边界和异常回滚策略？
> Version scope: Spring Framework 6.2 官方参考，项目规划版本 6.2.19；未覆盖时才适用默认回滚规则。
> Primary official source: [TX]
> Secondary official sources: ROLLBACK, TXRESOURCE, COMMIT（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

业务事务边界应围绕完整用例：必须共同成功或失败的数据库操作，放入同一个受正确事务管理器控制的事务，检查每步结果再决定提交或回滚。不是每个 DAO 调用都独立提交后再期待整体原子，也不是把一次用户流程中的所有外部动作都无限纳入一个长事务。[TXRESOURCE][COMMIT]

## 2. Main mechanism

Spring 声明式事务在默认 proxy 模式下由代理拦截外部调用。对象内部 this.method() 的 self-invocation 绕过代理，内部方法即使有 @Transactional，也不会因此额外触发事务拦截；若外层已有事务，它仍可能在已有事务中运行，不能把这说成内部数据库操作必然完全无事务。[TX]

未自定义时，RuntimeException 与 Error 默认触发回滚，checked exception 默认不触发。业务可设置 rollbackFor/noRollbackFor。示意入口应由外部经代理调用，异常须按既定契约传播或显式标记回滚：

```java
@Transactional(rollbackFor = BusinessException.class)
public void completeUseCase() throws BusinessException {
    // 多个必须共同提交的数据库步骤及结果检查
}
```

BusinessException 只是示意名称，没有新增生产类。[TX][ROLLBACK]

## 3. Important boundary

Spring 6.2 可通过 @EnableTransactionManagement(rollbackOn=ALL_EXCEPTIONS) 改变全局默认，包括 checked exception；方法具体规则还可覆盖默认。因此“checked exception 永不回滚”不成立，应核对实际配置。[TX]

catch 后吞掉异常，代理通常看不到这个异常，可能按正常返回提交；但已有 rollback-only 标记时仍可能回滚，不能反推“catch 必定提交”。错误的事务管理器、未覆盖目标 DataSource 的资源绑定，或绕过代理的调用边界，同样可使行为与预期不符。[TX][ROLLBACK][TXRESOURCE]

## 4. Common misconceptions

应把数据库原子阶段尽量缩短，避免在持锁期间等待长时间远程调用；只把必须原子提交的相关数据库操作组合进事务。缩短范围不能拆散必须共同成功的步骤，原子性与持锁时长需要一起考虑。[COMMIT][TXRESOURCE]

单数据库本地 JDBC 事务不能原子回滚任意 HTTP 请求、邮件或另一资源上的效果。工程上可根据业务需要设计事务后触发、消息可靠交付或补偿，并明确失败恢复责任；这些是候选一致性方案，不是本仓库已经引入消息系统的声明。[TXRESOURCE]

## 5. Official references

- [TX] Spring 6.2，Using @Transactional，代理/回滚/管理器
- [ROLLBACK] Spring 6.2，Rolling Back，异常与 rollbackFor
- [TXRESOURCE] Spring 6.2，Transaction Abstraction，本地/全局资源
- [COMMIT] MySQL 8.0，§15.3.1 事务提交与回滚

[TX]: https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html
[ROLLBACK]: https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html
[TXRESOURCE]: https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/strategies.html
[COMMIT]: https://dev.mysql.com/doc/refman/8.0/en/commit.html
