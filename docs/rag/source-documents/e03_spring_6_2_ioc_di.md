# Spring 6.2 IoC 与 DI：容器与依赖

> Title: Spring 6.2 IoC 与 DI：容器与依赖
> Bank ID: E03
> Knowledge category: SPRING
> Knowledge point: ioc_di
> Difficulty: EASY
> Target question: 什么是 IoC 和 DI？Spring 容器主要帮开发者解决了什么问题？
> Version scope: Spring Framework 6.2 官方参考；项目规划已解析版本为 6.2.19。
> Primary official source: [IOC]
> Secondary official sources: DI, LIFECYCLE（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

IoC 表示把对象创建、配置和依赖关系组装的控制权交给 Spring 容器：业务对象声明自己需要什么，而不是自行 new 所有依赖或主动查找依赖位置。DI 是 IoC 的一种具体形式，容器在创建 Bean 时把声明的依赖提供给它；二者分别描述控制关系和实现这种关系的方式。[IOC][DI]

## 2. Main mechanism

构造器注入由容器调用带依赖参数的构造器，Setter 注入则在实例创建后调用相应设置方法。示意中 InterviewService 只使用 Repository 契约；谁提供 Repository 实例，由组装方决定：

```java
class InterviewService {
    private final Repository repository;
    InterviewService(Repository repository) {
        this.repository = repository;
    }
}
```

这仅是依赖声明示意，不是仓库中某个类的完整实现。容器还须有相应 Bean 定义，才能解析并提供依赖；写一个构造器本身不会自动创建完整应用。[DI]

## 3. Important boundary

依赖接口或抽象基类时，业务类不必知道具体实现怎样构造、在哪里获得连接等细节。测试可直接传入 stub 或 mock，生产组装可选择真实实现，从而降低对具体依赖创建方式的耦合。DI 并不会消除业务类对依赖行为的需求；稳定且合理的契约仍由设计者负责。[DI]

容器不只负责注入：还根据 Bean 配置实例化对象、配置属性、组装对象，并调用约定的初始化和销毁回调。生命周期管理需按具体 Bean 配置与回调契约理解，不能仅凭“有依赖”就认定对象已交给容器管理。[IOC][LIFECYCLE]

## 4. Common misconceptions

“IoC 就是 @Autowired”把原则缩成一种配置写法；配置元数据可以有不同形式，关键是依赖如何获得。“用了 DI 就完全没有耦合”混淆了依赖契约与依赖创建方式；仍需合理划分职责。若业务类内部又自行构造具体依赖，外部提供的替代实现就可能失去作用。

本题关注容器与依赖，回答应串起实例化、配置、组装、生命周期四项职责，再用构造器或 Setter 说明 DI。不要用 AOP 或事务代理流程替代 IoC 的解释，也不要把手动创建的任意对象都称为容器管理的 Bean。[IOC][DI][LIFECYCLE]

## 5. Official references

- [IOC] Spring Framework 6.2，IoC Container and Beans，IoC/DI 与容器职责
- [DI] Spring Framework 6.2，Dependency Injection，构造器/Setter 注入与可测试性
- [LIFECYCLE] Spring Framework 6.2，Customizing the Nature of a Bean，初始化/销毁回调

[IOC]: https://docs.spring.io/spring-framework/reference/6.2/core/beans/introduction.html
[DI]: https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html
[LIFECYCLE]: https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html
