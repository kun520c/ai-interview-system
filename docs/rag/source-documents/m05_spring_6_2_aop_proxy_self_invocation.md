# Spring Framework 6.2：AOP 代理与 self-invocation

> Knowledge category: `SPRING`
> Target question: M05 — Spring AOP 的代理机制及内部调用 `@Transactional` 方法的行为
> Version scope: Spring Framework 6.2.x；本项目版本审计解析到 6.2.19。以下 self-invocation 结论专指 Spring AOP 的 proxy 模式。
> Primary source: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html

## 1. Core model

Spring AOP 的标准代理方式通过代理对象承接调用。调用者持有代理时，方法调用先到达 proxy；匹配的 interceptor 或 advice 可以在目标方法执行前后处理调用，然后 proxy 再将调用交给目标对象。事务、日志等横切逻辑依赖调用经过代理这一边界。目标对象本身并不会因为方法上有 advice，就自动改写所有直接调用路径。

Spring Framework 6.2 可用 JDK dynamic proxy 或 CGLIB 创建代理。JDK dynamic proxy 使用 JDK 内置代理机制，围绕目标对象实现的接口代理调用；CGLIB 是基于类的代理方式，通过目标类的子类承接调用。两种方式改变代理的形态，但都属于代理机制：需要拦截的调用必须经过代理。CGLIB 子类化受 Java 类型限制，例如 final class 不能被继承，final method 不能被子类覆盖，因此这类方法无法通过该类代理方式覆盖拦截。

## 2. Main mechanism

在 proxy-based AOP 中，外部调用先经过 Spring proxy，proxy 才有机会应用相应 advice。执行已经进入目标对象之后，如果目标方法用显式 `this.otherMethod()` 或隐式 `otherMethod()` 调用同一对象的另一个方法，接收者是目标对象自身，而不是外部代理。内部调用不会重新绕回 proxy，所以绑定在被调用方法上的 advice 在这条调用路径上没有机会运行。问题的核心是调用是否穿过代理边界，不是被调用方法是否有注解。

Spring Framework 6.2 的声明式事务文档说明，`@Transactional` 默认使用 proxy 模式；在该模式下，只有通过代理进入的外部方法调用才会被拦截。因此，目标对象内部 self-invocation 到另一个 `@Transactional` 方法时，该方法上的事务 advice 不会因这次调用而生效。这个结论解释的是 proxy 模式下的调用边界；不要把它泛化成所有事务问题的唯一成因，也不要把 proxy 模式结论套用到 AspectJ weaving。

## 3. Important boundary

优先通过重构调用边界来处理 self-invocation：将需要独立 advice 的职责放到另一个 Spring Bean / Service，再由调用方通过注入的 Bean 发起调用，使请求经过代理。Spring 文档还列出 self injection 作为备选方式，即通过自身代理引用调用；`AopContext.currentProxy()` 也可取得当前代理，但会让业务代码依赖 AOP 上下文，通常不是首选。无论使用 JDK proxy 还是 CGLIB，只要调用仍在目标对象内部直接发生，proxy-based AOP 的 self-invocation 限制仍然存在。

AspectJ weaving 不通过同一个外部代理拦截模型处理方法执行，因此不应说 AspectJ 模式与 proxy 模式有相同的 self-invocation 限制。本文讨论的是 Spring AOP 与默认声明式事务 proxy 模式。重构调用边界通常更清楚，也便于把事务 advice 的触发点显式放在 Bean 之间的调用路径上。

## 4. Common misconceptions

类中声明 `@Transactional` 不意味着目标对象内部的每条调用都会自动经过事务代理。`this` 调用不会自动回到外部 proxy；给内部方法再加注解也不会改变接收者。JDK dynamic proxy 与 CGLIB 的代理目标不同，但都不能让目标对象对自身的直接调用自动穿过外部代理。分析这类问题时，先确认运行模式和调用路径，再判断 advice 是否有机会执行。

## Official references

- Primary: [Spring Framework 6.2 Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html) — JDK proxy、CGLIB、self-invocation 与建议的处理方式。
- Secondary: [Spring Framework 6.2 Reference — Using `@Transactional`](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html) — 声明式事务的 proxy 模式与外部调用限制。
