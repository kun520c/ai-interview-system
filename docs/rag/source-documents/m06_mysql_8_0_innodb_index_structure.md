# InnoDB 索引：页、聚簇与覆盖

> Title: InnoDB 索引：页、聚簇与覆盖
> Bank ID: M06
> Knowledge category: MYSQL
> Knowledge point: innodb_index_structure
> Difficulty: MEDIUM
> Target question: InnoDB 为什么使用 B+Tree 作为主要索引结构？聚簇索引、二级索引、回表和覆盖索引之间是什么关系？
> Version scope: MySQL 8.0 InnoDB；手册称 B-tree，按 B+Tree 类叶子组织理解，页布局不作固定数值承诺。
> Primary official source: [INDEX]
> Secondary official sources: PAGE, GLOSSARY, BTREE, COVER（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

InnoDB 的普通索引以有序 B-tree 组织，索引记录存放在页中，数据集中于叶子页；面试通常用 B+Tree 类结构说明。它是多路结构，一个内部节点可有多个子节点。由高扇出推导，在同规模的平衡树比较中通常可降低树高，减少从根到叶所需的页访问；这是结构推理，不是手册承诺任何表都有固定高度或固定扇出。[PAGE][GLOSSARY]

## 2. Main mechanism

聚簇索引决定行数据的组织，其叶子记录包含完整行数据；通常采用主键。没有主键时，InnoDB 选择适合的 UNIQUE NOT NULL 索引；再没有，则生成内部聚簇索引。不要把“通常用主键”说成用户没定义主键就无法存储数据。[INDEX]

二级索引记录包含二级索引列及对应主键值。若查找还需要二级索引不能提供的列，可先用二级键定位，再通过主键访问聚簇记录，这通常叫回表。因此主键既是聚簇定位依据，也影响二级索引记录占用；它不是每条二级记录都保存完整行。[INDEX]

## 3. Important boundary

有序树索引不仅可做等值查找，也支持比较与范围条件；普通 Hash 索引主要适合等值条件，不提供同等的范围扫描能力。实际是否使用索引还取决于索引列、条件形态和优化器选择，不能把“支持范围”理解成任意 WHERE 都必定命中。[BTREE][GLOSSARY]

覆盖索引表示该次查询所需数据可由索引提供，可避免不必要的额外回表。下面仅是假设已有二级索引 (category, title) 且 id 为主键时的查询示意，不执行 DDL：

```sql
SELECT id, title FROM article WHERE category = 'JAVA';
```

所需 category、title 与主键 id 均可来自该二级索引；若再请求未包含的正文列，就不再仅凭这一索引覆盖。覆盖由一次查询及索引列共同决定，不是某个索引永久固定的标签。[INDEX][COVER]

## 4. Common misconceptions

“覆盖索引在任何 MVCC 情况下都绝不访问聚簇记录”超出了手册关于避免数据行访问的有限说明；本文只说可避免不必要的额外访问，不把覆盖承诺扩大到全部可见性检查路径。“B+Tree 永远只有三层、每页一定装固定条数”也忽略记录大小、数据规模与配置。

理解关系时应按查询路径回答：聚簇叶子承载行，二级记录带主键，需要额外列时回表，数据已可由索引提供时有覆盖机会。本文不展开 H06 的版本链与 Read View，也不把 E04 的事务性质当作索引结构证据。[PAGE][INDEX][COVER]

## 5. Official references

- [INDEX] MySQL 8.0，§17.6.2.1 Clustered and Secondary Indexes
- [PAGE] MySQL 8.0，§17.6.2.2 Physical Structure of an InnoDB Index
- [GLOSSARY] MySQL 8.0 Glossary，B-tree 与 covering index
- [BTREE] MySQL 8.0，§10.3.9 B-Tree/Hash Index Characteristics
- [COVER] MySQL 8.0，§10.3.1 How MySQL Uses Indexes，索引提供查询数据

[INDEX]: https://dev.mysql.com/doc/refman/8.0/en/innodb-index-types.html
[PAGE]: https://dev.mysql.com/doc/refman/8.0/en/innodb-physical-structure.html
[GLOSSARY]: https://dev.mysql.com/doc/refman/8.0/en/glossary.html
[BTREE]: https://dev.mysql.com/doc/refman/8.0/en/index-btree-hash.html
[COVER]: https://dev.mysql.com/doc/refman/8.0/en/mysql-indexes.html
