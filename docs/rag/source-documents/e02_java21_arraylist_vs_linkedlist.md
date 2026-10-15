# ArrayList 与 LinkedList：访问与修改成本

> Title: ArrayList 与 LinkedList：访问与修改成本
> Bank ID: E02
> Knowledge category: JAVA_COLLECTION
> Knowledge point: arraylist_vs_linkedlist
> Difficulty: EASY
> Target question: ArrayList 和 LinkedList 在底层结构、常见操作复杂度和实际使用场景上有什么区别？
> Version scope: Java SE 21 ArrayList/LinkedList API；操作成本有前提，实际性能须测量。
> Primary official source: [ARRAY]
> Secondary official sources: LINKED（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

ArrayList 是可调整容量的数组实现；LinkedList 是双向链表实现，同时实现 List 与 Deque。前者通过数组下标访问元素，后者沿节点链接定位元素。底层结构决定基本成本，但接口都叫 List 并不意味着任意操作都有相同复杂度。[ARRAY][LINKED]

## 2. Main mechanism

ArrayList 的 get(index) 等下标访问为 O(1)；尾部 add 的成本是摊还 O(1)，不能把一次需要扩容的追加也说成恒定成本。中间插入要把后续元素向后移动，中间删除要把后续元素向前移动，通常随剩余元素数增长。[ARRAY]

LinkedList 的下标操作从较近的头端或尾端遍历到目标位置；随机下标访问通常为 O(n)。插入或删除已定位节点时，调整相邻链接本身成本较低，但调用 add(index, value) 前的定位可能仍为 O(n)。已有 ListIterator 在当前游标位置修改与每次重新按下标定位，必须分开分析。[LINKED]

## 3. Important boundary

下面是访问模式的区别，而非性能测试：

```java
array.get(i);             // 数组下标访问
linked.add(i, value);     // 包括定位目标位置
iterator.add(value);      // 已有 LinkedList 游标位置
linked.addFirst(value);   // 两端操作
```

两端操作与中间按下标修改不是同一种需求。顺序遍历链表应沿 iterator 前进，反复用 get(i) 会不断重新定位。链表修改的优势需要“位置已知或只操作两端”等条件，不能由“写操作次数多”直接推出整体更快。[ARRAY][LINKED]

## 4. Common misconceptions

工程选择应看随机访问比例、修改位置、是否已有游标、数据规模与内存限制：频繁随机访问通常优先 ArrayList；已有游标的局部修改或双端使用可考虑 LinkedList。结合完整业务路径做测量，不能只比较链接修改与数组移动而漏掉查找节点的成本。

“增删多就一定用 LinkedList”没有说明增删位置和定位方式；“ArrayList 每次都按固定倍数扩容”把某个实现细节当作 API 契约。Java 21 API 明确给出摊还追加成本，并未规定一个可依赖的固定增长倍数。[ARRAY][LINKED]

## 5. Official references

- [ARRAY] ArrayList API，Java SE 21，类说明及 get/add/remove
- [LINKED] LinkedList API，Java SE 21，类说明、下标遍历及 ListIterator/Deque

[ARRAY]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html
[LINKED]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html
