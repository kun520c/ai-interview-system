# Java 21 接口与抽象类：契约、状态与选择

> Title: Java 21 接口与抽象类：契约、状态与选择
> Bank ID: E01
> Knowledge category: JAVA_BASIC
> Knowledge point: interface_vs_abstract_class
> Difficulty: EASY
> Target question: Java 中接口和抽象类有什么区别？实际开发中应该如何选择？
> Version scope: Java SE 21；语言规则依据 JLS 8/9，设计选择是基于这些规则的工程判断。
> Primary official source: [JLS9]
> Secondary official sources: JLS8（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

接口主要描述某种行为契约或能力，让调用者面向共同约定使用不同实现；抽象类适合表达具有共同基础特征的类层次，把共享状态与公共实现放进基类。这是设计含义，具体能力由 Java 21 的类与接口规则决定，不是编译器自动替开发者选择继承关系。[JLS8][JLS9]

## 2. Main mechanism

一个类只能直接继承一个类，但可以实现多个接口；接口也可以继承多个接口。因此支付方式、通知渠道等多实现契约适合用接口组合，不能为了增加第二种能力再继承第二个基类。[JLS8][JLS9]

抽象类不能直接实例化，但可以有实例字段和构造器；创建具体子类对象时仍会执行基类构造过程。它可以同时声明抽象方法与有方法体的具体方法，向子类提供公共状态初始化和行为实现。接口没有普通对象实例字段；其字段隐含为 public static final，不能拿来保存每个实现对象各自的可变状态。[JLS8][JLS9]

## 3. Important boundary

Java 21 接口并非只有抽象方法：default 方法可提供实现，static 方法属于接口，private 方法可供接口内部复用代码。这些方法体不会使接口变成带普通实例字段和构造器的基类；default 的代码可以调用实现类的方法，但不等于接口自身拥有共享实例状态。[JLS9]

若多个实现必须共享状态、初始化规则与紧密相关的基类行为，可以考虑抽象类；若重点是稳定调用契约、替换实现、隔离调用者与实现细节，或组合多种能力，则优先考虑接口。也可由抽象基类实现接口，让契约与共享实现各负其责；该建议依据语言能力推导，不是要求所有项目增加两层抽象。[JLS8][JLS9]

## 4. Common misconceptions

“接口不能有方法体”遗漏 default、static、private 方法；“抽象类只能有抽象方法”遗漏普通实现和构造过程；“接口总比抽象类好”忽略共享状态需求。选择应先说明调用者依赖什么、哪些状态需要复用，再判断继承或实现关系。不要仅因两个类有一段相似代码就建立不合理的基类关系，也不要把接口常量当作对象字段。

## 5. Official references

- [JLS9] JLS 21 Interfaces，§9.3 字段、§9.4 接口方法
- [JLS8] JLS 21 Classes，§8.1.1.1、§8.1.4–8.1.5、§8.3–8.4、§8.8

[JLS9]: https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html
[JLS8]: https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html
