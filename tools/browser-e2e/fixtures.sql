USE ai_interview_test;
INSERT INTO question(category,knowledge_point,difficulty,question_content,reference_answer,status) VALUES
('JAVA_BASIC','浏览器验收：equals与hashCode','EASY','请解释 equals 与 hashCode 的关系及使用约定。','equals 相等必须具有相同 hashCode；哈希相同不保证 equals 相等。','ENABLED'),
('JAVA_COLLECTION','浏览器验收：HashMap','EASY','请解释 HashMap 的基本结构与哈希冲突处理。','HashMap 使用数组、链表和红黑树，扩容时重新分配桶。','ENABLED'),
('SPRING','浏览器验收：依赖注入','EASY','请解释 Spring 依赖注入及 Bean 生命周期。','容器创建对象、注入依赖并执行初始化及销毁回调。','ENABLED'),
('MYSQL','浏览器验收：事务','EASY','请解释数据库事务的 ACID 特性。','原子性、一致性、隔离性、持久性。','ENABLED'),
('NETWORK','浏览器验收：TCP','EASY','请解释 TCP 三次握手及其作用。','SYN、SYN ACK、ACK 用于建立连接及确认双方收发能力。','ENABLED');
INSERT INTO question_scoring_point(question_id,content,point_type,weight,status,sort_order)
SELECT id,'准确解释核心机制并说明边界条件','CORE',100,'ENABLED',1 FROM question;
INSERT INTO knowledge_document(title,category,file_name,file_type,content,content_hash,document_version,processing_status)
SELECT knowledge_point,category,CONCAT(category,'.md'),'MARKDOWN',CONCAT('browser-e2e-evidence：',reference_answer),
SHA2(CONCAT('browser-e2e-evidence：',reference_answer),256),1,'READY' FROM question;
INSERT INTO knowledge_chunk(document_id,document_version,chunk_index,content,vector_id,embedding_model,embedding_version,status)
SELECT id,1,1,content,CONCAT('browser-e2e-vector-',id),'browser-e2e-mock-embedding','browser-e2e-mock-v1','ACTIVE' FROM knowledge_document;
