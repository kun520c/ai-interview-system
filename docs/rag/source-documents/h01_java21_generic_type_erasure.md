# Java 泛型擦除、桥接与运行时边界

> Title: Java 泛型擦除、桥接与运行时边界
> Bank ID: H01
> Knowledge category: JAVA_BASIC
> Knowledge point: generic_type_erasure
> Difficulty: HARD
> Target question: Java 的泛型擦除是什么？它会带来哪些限制？为什么很多泛型类型信息在运行时不存在？
> Version scope: 规范以 JLS/JVMS SE 21 为准；Oracle JDK 8 教程仅作擦除与 bridge 示意，不决定 Java 21 限制。
> Primary official source: [JLS4]
> Secondary official sources: ERASURE, JLS8, BRIDGE, JVMS4, JLS15（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

Java 泛型主要在编译期检查类型关系。擦除将类型变量替换为其最左侧 bound 的擦除；未写显式上界时通常为 Object。参数化类型擦除为对应原始类型。例如 `T extends Number` 的擦除依据 Number，而不是一律替换成 Object。[JLS4][ERASURE]

同一个泛型类的不同类型实参通常不生成独立运行时 Class：例如 `ArrayList<String>` 与 `ArrayList<Integer>` 的实例共享 ArrayList 的 Class。不同具体实现类不能据此混同。[JLS8][JLS4]

## 2. Main mechanism

编译器可在擦除后的访问位置插入类型转换，保留源代码需要的类型检查；为维持覆盖方法的多态关系，还可能生成 bridge method。例如父类方法接受 T，子类以 Integer 覆盖时，擦除后的参数类型可能不同，桥接方法可接受擦除类型、转换后委派给子类方法。class 文件的 ACC_BRIDGE 标记识别这类编译器桥接方法。[ERASURE][BRIDGE][JVMS4]

## 3. Important boundary

典型限制包括：不能直接 `new T()`；不能直接创建 `new List<String>[n]` 这种元素类型不可实化的数组；两个方法不能仅靠擦除后相同的泛型参数签名重载，例如分别接受 `List<String>` 与 `List<Integer>` 的同名方法。[JLS15][JLS8][JLS4]

但“所有参数化 instanceof 都非法”是旧规则的过度推广。Java 16 起放宽了限制，Java 21 中静态类型为 `List<Integer>` 的 x 可以测试 `x instanceof ArrayList<Integer>`；若 o 的静态类型为 Object，则 `o instanceof List<String>` 不能用运行时检查验证其元素实参，需要 unchecked conversion，因而非法。能否检查取决于操作数静态类型与转换规则，不能把前一例当作泛型实参已完全实化。[JLS15]

## 4. Common misconceptions

“所有泛型信息在运行时消失”也不准确：class 文件可保留类、字段、方法的 generic Signature 元数据，反射可以取得这些声明信息。声明中记录 `List<String>`，与某个运行时对象自行携带可检查的完整类型实参，是两回事；Signature 不改变擦除后的实际执行类型。[JVMS4]

## 5. Official references

- [JLS4] JLS 21 Types，§4.4、§4.6、§4.7
- [ERASURE] Oracle JDK 8 Tutorial，Type Erasure（机制示意）
- [JLS8] JLS 21 Classes，§8.1.2、§8.4.8.3
- [BRIDGE] Oracle JDK 8 Tutorial，Bridge Methods（机制示意）
- [JVMS4] JVMS SE 21，§4.6 ACC_BRIDGE、§4.7.9 Signature
- [JLS15] JLS 21 Expressions，§15.9、§15.10.1、§15.20.2

[JLS4]: https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html
[ERASURE]: https://docs.oracle.com/javase/tutorial/java/generics/erasure.html
[JLS8]: https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html
[BRIDGE]: https://docs.oracle.com/javase/tutorial/java/generics/bridgeMethods.html
[JVMS4]: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html
[JLS15]: https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html
