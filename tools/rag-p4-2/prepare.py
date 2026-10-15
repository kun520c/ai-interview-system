"""P4.2 local preparation only. No network, credentials, database or paid execution mode."""
import hashlib
import json
import pathlib
import sys
from difflib import SequenceMatcher

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'target/rag-p4-2/prepared'
OFFLINE = ROOT / 'target/rag-p4-2/offline'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads((ROOT / path).read_text(encoding='utf-8-sig'))


def save(name, value):
    path = OUT / name
    data = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    if path.exists() and path.read_bytes() != data:
        raise RuntimeError(f'Refusing to overwrite prepared artifact: {name}')
    path.write_bytes(data)


def requirement(number, role, anchor, description):
    return dict(id=f'r{number}', role=role, questionQuote=anchor, description=description)


P = 'PRIMARY_MECHANISM'
S = 'SUPPORTING_CONTEXT'


def contract(identifier, text, version, requirements):
    assert any(r['role'] == P for r in requirements)
    assert all(r['questionQuote'] in text for r in requirements)
    return dict(questionId=identifier, question=text, versionConstraint=version, requirements=requirements)


def historical_paths():
    paths = set()
    for prefix in ['src/main', 'src/test', 'docs/rag/source-documents', 'tools/rag-p2', 'tools/rag-p3', 'tools/rag-p4', 'tools/rag-p4-1',
                   'target/rag-p2', 'target/rag-p3', 'target/rag-p4', 'target/rag-p4-1', 'target/rag-p4-repair',
                   'target/rag-p1-independent', 'target/rag-p1-20261012', 'target/rag-p1-closeout-20261012',
                   'target/local-rag-p0-codex-20261011-v1', 'target/local-rag-p0-codex-20261011-v2-perfmark']:
        paths.update(p for p in (ROOT / prefix).rglob('*') if p.is_file())
    return sorted(paths)


def verify_map(files):
    for relative, expected in files.items():
        path = ROOT / relative
        if not path.is_file() or sha(path) != expected:
            raise RuntimeError(f'Frozen input changed/missing: {relative}')


def prepare():
    OUT.mkdir(parents=True, exist_ok=True)
    OFFLINE.mkdir(parents=True, exist_ok=True)
    baseline = OUT / 'protected-history.json'
    if baseline.exists():
        verify_map(read('target/rag-p4-2/prepared/protected-history.json'))
    else:
        save('protected-history.json', {p.relative_to(ROOT).as_posix(): sha(p) for p in historical_paths()})
    fixtures = {}
    development = {
        'C01': (1, '', [(P, '每次循环中调用get(i)', '下标遍历的定位机制与总成本'), (P, '改用持续前进的ListIterator', '持续游标的定位与当前位置插入机制')]),
        'C02': (2, 'JDK 8u', [(P, '其中一个CAS失败后应如何继续', '空桶CAS竞争失败的继续路径'), (P, '为什么锁住桶头后还要复核', '非空桶锁后复核的并发机制')]),
        'C03': (3, '', [(P, '为什么前面的普通字段写入可以对消费者可见', '发布前写入的可见性机制'), (P, '在写ready之后又修改config', '发布后写入的保证边界')]),
        'C04': (4, 'Spring Framework 6.2 默认 proxy 事务规则，未修改全局回滚默认', [(P, '它是否必然回滚', '默认配置下checked异常的回滚语义'), (P, '为什么不能简单断言必然提交', 'catch后正常返回与rollback-only边界'), (S, '没有改变全局默认', '改变全局配置属于题设外对比，不能证明默认行为')]),
        'C05': (5, 'MySQL 8.0 InnoDB REPEATABLE READ', [(P, '快照时点', '第一次一致性读与后续读取的快照时点'), (P, '锁定读语义', 'FOR UPDATE与普通一致性读的机制区别')]),
        'N02': (8, 'MySQL 8.0 InnoDB REPEATABLE READ 非唯一索引范围查询', [(P, '不存在的行为什么还能阻止插入', '不存在索引值的位置如何阻止插入'), (P, 'record lock、gap lock和next-key lock各自覆盖什么', '三种锁的索引记录与区间覆盖机制'), (S, '为什么不能只用MVCC快照解释这次等待', 'MVCC与locking read的一般区别是背景，不能代替范围锁的阻塞机制')]),
        'N06': (12, '', [(P, 'SO_KEEPALIVE的探测间隔与TCP_USER_TIMEOUT分别控制什么', '两种socket选项控制对象与失效判断机制'), (P, '会不会同时改变重传或keepalive探测发送的时间', '超时选项与探测/重传时序的关系'), (S, '为什么不能用TIME_WAIT的2MSL解释这次断开', '关闭后TIME-WAIT是背景，不能替代题设连接上的socket超时机制')]),
    }
    reviews = read('tools/rag-p4-1/development-review-v1.json')['cases']
    for key, (number, version, specs) in development.items():
        inp = read(f'target/rag-p4/prepared/inputs/g{number:02}.json')
        fixture = dict(input=inp, contract=contract(inp['questionId'], inp['question'], version,
                        [requirement(i + 1, *s) for i, s in enumerate(specs)]), provenance='OFFLINE_AUTHORED_MOCK_FROM_SAVED_BODY_NOT_NEW_MODEL_OUTPUT')
        matches = [r for r in reviews if r['queryId'] == f'P3-{key}']
        chosen = matches[1] if key in ('C01', 'C05') else matches[0]
        fixture['contextQuote' if key.startswith('N') else 'primaryQuote'] = chosen['quote']
        assert chosen['quote'] in inp['candidates'][1 if key in ('C01', 'C05') else 0]['content']
        if key == 'C04':
            fixture['conflictQuote'] = 'Spring 6.2 可通过 @EnableTransactionManagement(rollbackOn=ALL_EXCEPTIONS) 改变全局默认'
            # Keep an exact continuous body quotation, not a paraphrased configuration label.
            body = inp['candidates'][0]['content']
            fixture['conflictQuote'] = next(line for line in body.splitlines() if 'ALL_EXCEPTIONS' in line)
        fixtures[key] = fixture
    save('development-fixtures.json', fixtures)

    # New holdout authoring is complete before any new retrieval. It is never used by Mock semantic tests.
    specifications = [
        ('H42-01', True, 'Java SE 21', 'Java 21中，Parent<T>声明accept(T)，Child extends Parent<Integer>覆盖accept(Integer)。javap还能看到Child中标记为ACC_BRIDGE的accept(Object)。为什么编译器生成这个方法，它如何在擦除后的调用中维持覆盖与多态，而不是一个独立的业务重载？',
         [(P, '为什么编译器生成这个方法', '擦除与桥接方法的生成原因'), (P, '如何在擦除后的调用中维持覆盖与多态', '桥接方法的调用与委派机制'), (S, '而不是一个独立的业务重载', '与业务重载的对比')],
         'h01_java21_generic_type_erasure.md', ['编译器可在擦除后的访问位置'], '正文明确说明擦除后签名、类型转换、桥接委派和ACC_BRIDGE；与旧泛型instanceof/反射问题的主要机制不同。', [], []),
        ('H42-02', False, 'Java SE 21', 'Java 21 ThreadPoolExecutor通过submit(Callable)运行任务，任务抛出RuntimeException，但afterExecute的Throwable参数为null，而Future.get()抛出ExecutionException。请解释FutureTask如何记录异常完成状态，以及为什么异常要通过Future结果或hook中的额外检查观察；不要用拒绝策略解释这个已执行任务的异常。',
         [(P, 'FutureTask如何记录异常完成状态', '已执行任务的异常保存机制'), (P, '为什么异常要通过Future结果或hook中的额外检查观察', 'Future结果与afterExecute异常观察的差异'), (S, '不要用拒绝策略解释', '拒绝策略只是提交阶段背景')],
         'h04_java21_thread_pool_design.md', ['execute 的主要提交路径', '监控可观察'], '最接近的正文描述容量、入队、拒绝与监控hook；没有submit包装的FutureTask状态保存、异常吞收或get传播机制。文末链接不能当作已入库正文。', ['FutureTask', 'afterExecute', 'ExecutionException'], ['https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html#afterExecute(java.lang.Runnable,java.lang.Throwable)']),
        ('H42-03', True, 'MySQL 8.0 InnoDB', 'MySQL 8.0 InnoDB表t(id主键, region, payload)只有二级索引idx_region(region)。对于按region筛选的SELECT id, region，以及再多取payload的SELECT，请解释二级索引记录提供哪些数据、什么时候需要回表，为什么不能把idx_region永久叫作覆盖索引？不要求证明任意MVCC状态下都绝不访问聚簇记录。',
         [(P, '二级索引记录提供哪些数据', '二级记录和主键的结构机制'), (P, '什么时候需要回表', '额外查询列与聚簇访问的机制'), (P, '为什么不能把idx_region永久叫作覆盖索引', '覆盖与具体查询所需列的关系'), (S, '任意MVCC状态下', '不外推全部可见性访问保证')],
         'm06_mysql_8_0_innodb_index_structure.md', ['二级索引记录包含', '所需 category'], '正文说明二级记录含列与主键、额外列回表和查询相关覆盖；旧题主要讨论长主键的二级索引体积。', [], []),
        ('H42-04', False, 'MySQL 8.0 InnoDB READ COMMITTED', 'MySQL 8.0 InnoDB在READ COMMITTED下执行UPDATE扫描，遇到已被另一事务锁住的记录时使用semi-consistent read。它为何先提供最新已提交版本给WHERE判断，什么情况下要再次读取并获取或等待锁？请解释这条DML扫描路径，不能用普通非锁定SELECT每次建立新快照的结论替代。',
         [(P, '为何先提供最新已提交版本给WHERE判断', 'semi-consistent DML条件检查的机制'), (P, '什么情况下要再次读取并获取或等待锁', '匹配条件后的重读和锁定机制'), (S, '普通非锁定SELECT每次建立新快照', 'RC一致性读快照属于另一条路径的背景')],
         'h06_mysql_8_0_innodb_mvcc_consistent_read.md', ['在 `REPEATABLE READ`', '`SELECT ... FOR UPDATE`'], '正文只解释consistent SELECT快照和FOR UPDATE一般锁定语义；缺少UPDATE在锁住记录上用于WHERE匹配的半一致性检查及之后的锁定重读。', ['semi-consistent', '半一致'], ['https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html']),
        ('H42-05', True, 'Spring Framework 6.2', 'Spring 6.2中，相同的Service类型一个由@Bean定义交给容器创建，另一个在业务代码里直接new并手动传入同样的依赖。在没有额外注册手动对象的前提下，初始化和销毁回调分别由谁负责，为什么依赖都已传入仍不能说明两个对象都由容器管理生命周期？请解释容器职责与管理边界。',
         [(P, '初始化和销毁回调分别由谁负责', '容器生命周期回调的管理责任'), (P, '不能说明两个对象都由容器管理生命周期', '依赖组装与容器管理的边界')],
         'e03_spring_6_2_ioc_di.md', ['容器不只负责注入'], '正文直接说明实例化、属性、组装及约定回调，且有依赖不等于容器管理；旧题针对注入后自行new绕过替代依赖。', [], []),
        ('H42-06', False, 'Spring Framework 6.2，显式允许循环引用', '在明确允许循环引用的Spring 6.2容器中，两个非懒加载singleton通过Setter互相依赖，且A需要AOP代理。请解释singletonObjects、earlySingletonObjects和singletonFactories怎样协作暴露早期引用，以及getEarlyBeanReference为何关系到B持有的A与最终代理的身份一致性；仅说明Setter注入或外部调用穿过代理还不够。',
         [(P, '怎样协作暴露早期引用', '三级singleton缓存与早期引用的协作'), (P, '为何关系到B持有的A与最终代理的身份一致性', '早期代理与最终实例身份的机制'), (S, 'Setter注入或外部调用穿过代理', '一般注入和代理调用只是背景')],
         'e03_spring_6_2_ioc_di.md', ['构造器注入由容器'], 'IoC正文描述依赖组装和回调，AOP正文描述外部代理调用与self-invocation；都没有早期引用缓存、工厂回调或提前代理身份的创建协议。显式允许循环引用避免错误默认前提。', ['singletonFactories', 'earlySingletonObjects', 'getEarlyBeanReference', '早期引用'], ['https://github.com/spring-projects/spring-framework/blob/v6.2.19/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.java']),
        ('H42-07', True, 'Java SE 21', 'Java 21 ThreadPoolExecutor的corePoolSize=2、maximumPoolSize=4、keepAliveTime=20秒，已有4个worker且都空闲。默认超时回收针对哪个规模的线程，启用allowCoreThreadTimeOut(true)后策略范围有什么变化？请把空闲回收机制与任务入队及扩容路径区分开。',
         [(P, '默认超时回收针对哪个规模的线程', '默认空闲线程回收的适用范围'), (P, '策略范围有什么变化', '核心线程超时开关的机制'), (S, '任务入队及扩容路径', '提交路径背景，不能单独支持空闲回收')],
         'h04_java21_thread_pool_design.md', ['keepAliveTime 默认'], '正文明确keepAlive默认超核心规模以及核心超时开关；旧题是CallerRuns关闭丢弃与持久化责任。', [], []),
        ('H42-08', False, 'HotSpot JDK 21', 'HotSpot JDK 21启用TLAB时，小对象分配如何通过线程本地top/end指针推进完成，剩余空间不足时为何进入补充分配缓冲区或其他慢路径？TLAB为什么仍属于共享Java堆的一段空间，而不是把对象放到该线程的Java调用栈？请解释实际分配机制，不能只列共享堆与线程私有栈的名称。',
         [(P, '如何通过线程本地top/end指针推进完成', 'TLAB对象分配的指针推进机制'), (P, '剩余空间不足时', 'TLAB不足的补充与慢路径机制'), (S, '共享Java堆的一段空间', '共享数据区的背景不能单独证明TLAB分配实现')],
         'm03_jvm21_runtime_data_areas.md', ['JVM 规范描述', '“对象在 Heap'], '正文描述逻辑运行时数据区及对象/引用边界，明确不从规范概念推导具体优化分配；缺少TLAB缓冲、指针推进与补充慢路径实现。', ['TLAB', 'ThreadLocalAllocBuffer', '线程本地分配'], ['https://github.com/openjdk/jdk/blob/jdk-21%2B35/src/hotspot/share/gc/shared/threadLocalAllocBuffer.inline.hpp'])
    ]
    prior_files = ['src/test/resources/rag-p1-independent/queries-v1.json', 'tools/rag-p3/queries-v1.json', 'docs/rag/evaluation/PHASE_4C2_HOLDOUT_QUERY_SET.json']
    old_questions = []
    def extract(node):
        if isinstance(node, dict):
            for k, v in node.items():
                if k in ('questionText', 'question', 'queryText') and isinstance(v, str): old_questions.append(v)
                else: extract(v)
        elif isinstance(node, list):
            for item in node: extract(item)
    for path in prior_files: extract(read(path))
    corpus = sorted((ROOT / 'docs/rag/source-documents').glob('*.md'))
    assert len(corpus) == 21
    corpus_hash = {p.relative_to(ROOT).as_posix(): sha(p) for p in corpus}
    frozen_corpus = read('tools/rag-p3/queries-v1.json')['corpusDocumentSha256']
    assert corpus_hash == frozen_corpus
    queries, audit = [], []
    for identifier, covered, version, text, requirements, filename, starts, reasoning, terms, urls in specifications:
        q = contract(identifier, text, version, [requirement(i + 1, *s) for i, s in enumerate(requirements)])
        queries.append(q)  # This file contains neither expected coverage nor answer/label material.
        assert text not in old_questions
        closest = max(old_questions, key=lambda x: SequenceMatcher(None, text, x).ratio())
        doc = ROOT / 'docs/rag/source-documents' / filename
        quotes = []
        for start in starts:
            line_no, line = next((i, l) for i, l in enumerate(doc.read_text(encoding='utf-8').splitlines(), 1) if l.startswith(start))
            quotes.append(dict(path=doc.relative_to(ROOT).as_posix(), line=line_no, quote=line, sha256=sha(doc)))
        matches = {term: [p.name for p in corpus if term.lower() in p.read_text(encoding='utf-8').lower()] for term in terms}
        audit.append(dict(questionId=identifier, expectedCoverage='COVERED' if covered else 'HARD_NEGATIVE',
                          source='AI_DOCUMENT_AUDIT_BEFORE_RETRIEVAL_PROVISIONAL_NOT_HUMAN', humanConfirmed=False,
                          basisOrNearestNonAnswer=quotes, mechanismBoundary=reasoning, supplementalTermSearch=matches,
                          termAbsenceAloneIsNotCoverageProof=True, officialReferences=urls, officialReferencesNotAddedToCorpus=True,
                          novelty='NEW_STEM_AND_PRIMARY_MECHANISM_SCENARIO_NOT_A_NEW_TECHNOLOGY_DOMAIN',
                          nearestHistoricalQuestion=closest, nearestTextSimilarity=round(SequenceMatcher(None, text, closest).ratio(), 4)))
    save('holdout-questions-v1.json', {'scope': 'NEW_PRE_RETRIEVAL_SET_NEVER_USED_IN_SEMANTIC_MOCKS', 'questions': queries})
    save('coverage-audit-v1.json', {'reviewDate': '2026-10-13', 'coverageSource': 'AI_OFFLINE_PROVISIONAL', 'corpusSha256': corpus_hash,
                                 'historyQuestionFilesSha256': {p: sha(ROOT / p) for p in prior_files}, 'reviews': audit})
    # UTF-8 byte counts with overhead are conservative token proxies, never claimed as tokenizer results.
    prompt_bytes = len((ROOT / 'tools/rag-p4-2/system-prompt.txt').read_bytes())
    gate_in = 8 * (16000 + prompt_bytes + 2048)
    rank_in = 8 * (5 * (3 * (1200 + 256) + 3000) + 2048)
    budget = {'currency': 'CNY', 'proxyNotInvoiceOrGuaranteedTokenUpperBound': True, 'utf8ByteTokenProxy': True,
              'gateInputTokensProxy': gate_in, 'gateOutputMaxTokens': 8 * 3072, 'rerankInputTokensProxy': rank_in, 'embeddingInputTokensProxy': 8 * 3000,
              'peakNoCacheDeepSeekInputPerMillion': 2, 'peakDeepSeekOutputPerMillion': 8,
              'rerankInputPerMillion': .5, 'embeddingInputPerMillion': .5,
              'combinedPeakNoCacheProxyCny': round((gate_in * 2 + 8 * 3072 * 8 + rank_in * .5 + 8 * 3000 * .5) / 1e6, 6), 'hardRunBudgetCny': 1.0,
              'priceSources': ['https://api-docs.deepseek.com/zh-cn/quick_start/pricing/', 'https://help.aliyun.com/zh/model-studio/qwen3-7-text-rerank', 'https://help.aliyun.com/zh/model-studio/qwen3-7-text-embedding']}
    assert budget['combinedPeakNoCacheProxyCny'] < budget['hardRunBudgetCny']
    plan = {'schema': 'P42_PRIMARY_MECHANISM_PREREGISTRATION_V1', 'status': 'PREP_ONLY_NO_PAID_RUN_AUTHORIZATION',
            'sample': {'covered': 4, 'hardNegative': 4, 'maximumCandidateCount': 40, 'maximumGateCandidateCount': 16},
            'callsUpperBound': {'Embedding': 8, 'MilvusSearch': 8, 'Rerank': 8, 'DeepSeekSemanticGate': 8, 'DeepSeekAnswerEvaluation': 0, 'Jev': 0, 'MySqlOrVectorWrites': 0},
            'query': 'ORIGINAL_QUESTION_ONLY_NO_ANSWER_NO_SCORING_POINT_QUERY', 'ranking': 'MILVUS_TOP5_ELIGIBLE_TO_RERANK_TOP2_NO_COSINE_CUTOFF_NO_BACKFILL',
            'gate': {'inputJsonMaxUtf8Bytes': 16000, 'maxOutputTokens': 3072, 'requirements': 'AUTHORITATIVE_ROLES_LOCKED_BEFORE_RETRIEVAL',
                     'admission': 'ONE_OR_MORE_TRUE_DIRECT_PRIMARY_MECHANISMS_WITH_VERIFIABLE_BODY_QUOTES', 'wholeAnswerSufficiency': 'NOT_INDEPENDENTLY_VERIFIED',
                     'timeoutsSeconds': {'connect': 5, 'rerankRead': 10, 'gateRead': 30}, 'newCallsPerQuestion': {'rerank': 1, 'gate': 1}},
            'comparisons': ['MILVUS_TOP2', 'RERANK_TOP2', 'RERANK_TOP2_SCORE_GE_0.50_HISTORICAL_ONLY', 'RERANK_TOP2_PRIMARY_GATE'],
            'qualityCriteriaLocked': {'coveredQuestionCoreEvidenceRetention': '4/4', 'hardNegativeNormalCorrectRejection': '4/4; SAFE_EMPTY_API/INVALID/UNCERTAIN does not count as semantic success',
                                      'coreEvidenceRetentionRelativeToRerankTop2': '>=90% of independently labelled core-support candidates; denominator zero -> inconclusive',
                                      'backgroundOrNoSupportAdmitted': '0', 'allQuoteIdVersionChecks': 'PASS', 'evidenceCount': '0..2',
                                      'execution': '8/8 complete, retries=0', 'labels': 'INDEPENDENT_HUMAN_CORE_SUPPORT_LABELS_REQUIRED; AI suggestions kept separate',
                                      'smallSampleLimit': 'This new set is a limited holdout; it cannot establish broad production generalization'},
            'metrics': ['covered retention', 'outside correct rejection', 'context/no-support misadmission', 'core useful false deletion', '0/1/2 histogram', 'comparison to fixed Top2', 'per-stage median/p95/wall latency', 'actual model usage and price estimate'],
            'review': 'Label all 40 retrieved candidates for core support, context only, none, uncertain and version/conditions. Historical useful label >=1 is not the same as primary mechanism support.',
            'budget': budget,
            'futureExecutionSafety': ['New independent run-once directory and exclusive CreateNew lock; never reuse any P3/P4/P4.1 lock',
                                      'Require a new one-time interactive authorization AFTER frozen plan review; no execution mode in this preparation entry',
                                      'Read-only test schema ai_interview_test and existing knowledge_chunk_vectors_test; retain EnvironmentIsolationInitializer',
                                      'Never initialize or create a Collection during the read-only experiment; use existing guarded P3 read-only loader',
                                      'Verify frozen sources, rules, compiled adapters and corpus hashes before first call; stop on drift',
                                      'Save every raw request/response/status/usage and latency, increment attempt before send, stop on failure with no retry or silent model substitution',
                                      'Check budget before each request and stop when usage missing or upper proxy would exceed budget; never silently bypass budget',
                                      'Record frozen MySQL row fingerprints and vector count/identifiers before/after; no production/evaluation schema overrides',
                                      'Freeze labels without altering predictions; FAIL stays FAIL and subsequent rules require a new holdout']}
    save('validation-plan-v1.json', plan)
    print('P42_INPUT_PREP=READY; new holdout=8 (4+4); paid calls=0')


def freeze():
    verify_map(read('target/rag-p4-2/prepared/protected-history.json'))
    java = read('target/rag-p4-2/offline/java-tests.json')
    assert java['failures'] == java['errors'] == java['skipped'] == 0
    assert java['legacyResponsesReplayed'] == 8 and java['newRealModelCalls'] == 0
    for name in ('python-tests.json', 'powershell-tests.json'):
        checks = read('target/rag-p4-2/offline/' + name)
        assert checks['failures'] == checks['errors'] == checks['skipped'] == 0
    replay = read('target/rag-p4-2/offline/legacy-replay.json')
    assert all(row['gate']['status'] != 'SAFE_EMPTY' for row in replay['queries'])
    for name in ('holdout-questions-v1.json', 'validation-plan-v1.json'):
        (OUT / (name + '.sha256')).write_text(sha(OUT / name) + '\n', encoding='ascii')
    tracked = sorted(p for p in (ROOT / 'tools/rag-p4-2').rglob('*') if p.is_file() and '__pycache__' not in p.parts)
    tracked += sorted(p for p in OUT.glob('*') if p.is_file() and p.name not in ('freeze.json', 'freeze.json.sha256', 'report.md'))
    tracked += sorted(p for p in (OFFLINE / 'classes').rglob('*.class'))
    tracked += sorted(OFFLINE.glob('*.json'))
    # Preserve binary runtime ports as well as source hashes. No Maven rebuild of frozen classes.
    tracked += [p for p in (ROOT / 'target/classes/com/kun/aiinterview').rglob('*.class') if p.is_file()]
    bound = {p.relative_to(ROOT).as_posix(): sha(p) for p in tracked}
    save('freeze.json', {'ruleVersion': 'P42_PRIMARY_MECHANISM_V1', 'filesSha256': bound,
                         'newModelCalls': 0, 'productionIntegrated': False, 'noExecuteMode': True})
    (OUT / 'freeze.json.sha256').write_text(sha(OUT / 'freeze.json') + '\n', encoding='ascii')
    verify()
    report()


def report():
    plan = read('target/rag-p4-2/prepared/validation-plan-v1.json')
    analysis = read('target/rag-p4-2/prepared/development-analysis.json')
    counts = [read('target/rag-p4-2/offline/' + f) for f in ('java-tests.json', 'python-tests.json', 'powershell-tests.json')]
    def link(label, relative):
        return f'[{label}]({(ROOT / relative).as_posix()})'
    metrics = analysis['p3Baselines']
    label_counts = {}
    for q in read('target/rag-p4/prepared/dataset-with-provenance.json')['queries']:
        for c in q['candidates']:
            label_counts[c['labelSource']] = label_counts.get(c['labelSource'], 0) + 1
    qrows = []
    audit = read('target/rag-p4-2/prepared/coverage-audit-v1.json')['reviews']
    for q, a in zip(read('target/rag-p4-2/prepared/holdout-questions-v1.json')['questions'], audit):
        qrows.append(f"| {q['questionId']} | {a['expectedCoverage']} | {q['question']} |")
    text = f"""P4_2_PREP=READY

日期：2026-10-13（Asia/Shanghai）。完成的是独立 Java 适配层、规则与离线准备；生产业务调用点尚未接线，独立新题尚未执行。新增收费请求、数据库连接/写入、Milvus连接/写入均为0。未提交或推送。旧P3冻结策略FAIL、旧P4首次执行FAIL保持原样；P4尚未尝试的7题仍留在原执行锁中。

**规则与实现**

题目要求由调用方在检索前锁定为PRIMARY_MECHANISM或SUPPORTING_CONTEXT。模型只能引用既定ID，不能再通过自行生成corePoints扩大准入范围。每条CORE_SUPPORT必须引用真实正文并直接支持至少一个主要机制；部分主要机制的支持可以保留，缺失点单独输出。背景、版本描述、题设外配置分支不能单独使证据准入。即使所有主要点被模型声明支持，wholeAnswerSufficiency始终为NOT_INDEPENDENTLY_VERIFIED。

| 文件 | 职责 |
| --- | --- |
| {link('QuestionContract.java', 'tools/rag-p4-2/java/com/kun/aiinterview/knowledge/admission/QuestionContract.java')} | 锁定题面锚点、ID、主辅角色和版本约束，禁止只含辅助点的契约 |
| {link('PrimarySemanticGate.java', 'tools/rag-p4-2/java/com/kun/aiinterview/knowledge/admission/PrimarySemanticGate.java')} | 结构、ID、逐字正文引用、版本声明和主辅角色验证；返回支持点、缺口与0～2条知识 |
| {link('system-prompt.txt', 'tools/rag-p4-2/system-prompt.txt')} | 要求说明引文实际支持的最小机制及条件，不用背景补齐核心，不要求单条完整答题 |
| {link('MilvusTop5Candidates.java', 'tools/rag-p4-2/java/com/kun/aiinterview/knowledge/admission/MilvusTop5Candidates.java')} | 复用EmbeddingClient、VectorStoreClient及既有MySQL资格查询；不按COSINE提前删证据，不写库 |
| {link('BailianRerankClient.java', 'tools/rag-p4-2/java/com/kun/aiinterview/knowledge/admission/BailianRerankClient.java')} | qwen3.7-text-rerank原生北京Workspace接口，一次调用返回全部候选排序，禁止误用Embedding接口 |
| {link('ExperimentalEvidenceStrategy.java', 'tools/rag-p4-2/java/com/kun/aiinterview/knowledge/admission/ExperimentalEvidenceStrategy.java')} | 原题Milvus Top5 → 合资格正文 → Rerank Top2 → 一次Gate；不补Rank3；保留原Rank/身份及诊断 |
| {link('P42Configuration.java', 'tools/rag-p4-2/java/com/kun/aiinterview/knowledge/admission/P42Configuration.java')}、{link('P42Properties.java', 'tools/rag-p4-2/java/com/kun/aiinterview/knowledge/admission/P42Properties.java')} | 独立显式导入的Spring配置，enabled缺省false；另建限3072输出的实验客户端，复用现有DeepSeek JSON客户端实现，不改生产设置 |
| {link('P42OfflineTest.java', 'tools/rag-p4-2/P42OfflineTest.java')}、{link('verify_tests.py', 'tools/rag-p4-2/verify_tests.py')} | 聚焦Mock、服务、开关、协议、保存真实响应回归及指标复算 |
| {link('run.ps1', 'tools/rag-p4-2/run.ps1')}、{link('prepare.py', 'tools/rag-p4-2/prepare.py')} | UTF-8、本地隔离编译、历史SHA保护、题目/规则/方案冻结；只有Offline和Verify，无收费执行入口 |

以上文件在tools/rag-p4-2，全部位于src/main之外。P4.1封存清单绑定现有业务源码/编译产物，本阶段采用用户允许的独立适配层，未解除冻结或修改EvaluationRetrievalAdapter、生产Prompt、配置与默认路径。默认关闭时组合类原样委派既有query/topK/result，不新增Rerank或Gate调用；未显式导入实验配置时生产应用根本不加载这些类。

原生Rerank接口为POST https://{{WorkspaceId}}.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank，Bearer认证；Workspace/key必须显式提供，不自动切换。Gate复用DeepSeekEvaluationClient，POST https://api.deepseek.com/chat/completions，Flash别名、json_object、thinking=disabled、stream=false，max_tokens=3072。当前官方deepseek-v4-flash别名由DeepSeek-V4.1-Flash服务：[官方模型/价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)。真实配置、网络与该新组合没有执行验证。

**故障降级与实际限制**

没有锁定契约、没有合资格候选、检索错误、Rerank异常、Gate超时/API失败/JSON不合法/引用不真实均明确返回空知识；不回退到未经确认的Top2。UNCERTAIN单独标识，不能计为库外题质量成功。条件冲突和仅辅助支持不准入。合法同点多段引文逐段验证；模型返回候选顺序不会改变Rerank优先级。应用层不重试。连接5秒、Rerank读取10秒、Gate读取30秒；Embedding和Milvus继续使用已有端口的超时。

Java验证可证明引用出现、ID和角色一致、版本声明符合协议，不能独立证明模型的DIRECT_MECHANISM/MATCH语义判定真实。模型仍可能把真实背景引文错误关联到一个主要ID，需独立人工审查发现。版本/条件蕴含检查的规则与提示已实现，自动事实证明未实现；没有引入LLM Judge或新Agent。缺契约或检索失败时诊断明确，但embedding元信息可能为空，未来正式接线需适配业务Trace/空知识路径，不能把此独立适配层描述为已完成生产集成。题库契约提供器尚未接入业务题库。

现有生产DeepSeek客户端不保留usage，此适配层也未声称已统计新组合真实费用。后续独立运行器须捕获原始响应usage、每阶段延迟与应用层尝试数；尚未实现/授权新的收费ExecuteOnce运行器。

**离线验证**

| 验证 | 测试数 | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Java 21独立编译后的可执行断言/Mockito/Spring Mock | {counts[0]['tests']} | 0 | 0 | 0 |
| 本地数据、冻结、指标与历史结果一致性 | {counts[1]['tests']} | 0 | 0 | 0 |
| PowerShell语法和拒绝ExecuteOnce模式 | {counts[2]['tests']} | 0 | 0 | 0 |

覆盖C01持续游标的部分主要机制、C02 CAS重试、C03 happens-before部分支持、C04默认/改变全局配置分支与rollback-only、C05简短快照摘要、N02 MVCC背景、N06 TIME-WAIT背景；以及多段引文、伪造/标题/URL/参考引用、未知ID、额外字段、重复JSON键、技术版本、UNCERTAIN、服务超时/API失败、Top2不补位和开关行为。新holdout没有用于语义Mock或参数调整。测试使用Mock运输，未建立真实JDK网络连接；本环境初始化JDK HttpClient的loopback失败属于环境限制，不能算真实网络通过。Mockito改用JVM启动时标准-javaagent参数，未改变系统保护。

P4 g01～g05与P4.1 t1～t3共8份保存的真实响应，使用已有修复校验器在隔离目录回放，与P4修复报告及P4.1历史decision逐字段完全一致。没有把旧协议corePoints自动转换为新协议并伪称新版模型验证成功。详细证据：{link('Java测试', 'target/rag-p4-2/offline/java-tests.json')}、{link('数据测试', 'target/rag-p4-2/offline/python-tests.json')}、{link('历史回放', 'target/rag-p4-2/offline/legacy-replay.json')}、{link('开发集分析', 'target/rag-p4-2/prepared/development-analysis.json')}。

修订P3标签来源计数：{json.dumps(label_counts, ensure_ascii=False)}。这些来源保持原样，质量比较仍为PROVISIONAL；历史label≥1可能只代表背景部分相关，不能直接等同本阶段主要机制支持。

| 历史策略 | 选择数 | 旧定义有用 | 直接相关 | 完全无关 | 覆盖题保留有用 | 库外题返回空 |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| Rerank Top2 | {metrics['RERANK_TOP2']['selected']} | 17 | 11 | 7 | 6/6 | 0/6 |
| Top2 + score≥0.50 | 15 | 15 | 11 | 0 | 6/6 | 4/6 |

N02/N06仍展示“旧有用标签”与“支持主要机制”不同。旧P4 g01第二条与g05第二条仍为模型误拒的开发诊断；新规则Mock说明可接纳其明确部分机制。P4.1真实结果已保留C01两条，并让N02/N06返回0，但只属于旧提示的3题微验证。新P4.2协议的真实保留率/误准入率/版本判断能力尚未测量。

**新独立验证集与预注册**

在看任何新检索结果之前冻结8道新题及要求，4覆盖+4相同术语但机制缺失的Hard Negative。覆盖核对依据21份冻结正文、邻近非答案及官方机制资料；属于AI离线覆盖审计，正式结果仍需独立人工标签。官方资料没有加入知识库。原题全文：{link('holdout-questions-v1.json', 'target/rag-p4-2/prepared/holdout-questions-v1.json')}；逐题覆盖/机制缺口、来源与旧题差异：{link('coverage-audit-v1.json', 'target/rag-p4-2/prepared/coverage-audit-v1.json')}。

| ID | 预期覆盖状态（PROVISIONAL） | 冻结原题 |
| --- | --- | --- |
{chr(10).join(qrows)}

题目SHA256：`{sha(OUT / 'holdout-questions-v1.json')}`

方案SHA256：`{sha(OUT / 'validation-plan-v1.json')}`

完整规则/源码/编译适配层/历史保护清单冻结SHA256：`{sha(OUT / 'freeze.json')}`

预注册验收：4/4覆盖题保留真实主要机制证据；4/4库外题正常返回0，API/格式/UNCERTAIN空结果不算成功；相对Rerank Top2经独立人工确认的核心证据保留≥90%；背景/无支持误准入为0；0～2数量、引用/ID/版本校验及8/8无重试执行合格。分母无核心证据则inconclusive。人工检查最多40条候选，AI建议另列，不将标签送入模型。不看结果回调规则；失败保留FAIL，改规则要换新验证集。8题的小样本不能证明广泛泛化成功。

最多8 Embedding + 8 Milvus Search + 8 Rerank + 8 DeepSeek Gate；其中最多24次收费模型请求，答案评价/Jev/写入0。Embedding与Rerank输入各按北京公开原价¥0.50/百万Token；DeepSeek采用高峰无缓存输入¥2、输出¥8/百万Token的保守代理估算。总代理约¥{plan['budget']['combinedPeakNoCacheProxyCny']:.6f}，拟定预算门禁¥1.00。UTF-8字节+开销是token代理，不能作为实际usage或账单。价格来源：[Embedding](https://help.aliyun.com/en/model-studio/qwen3-7-text-embedding)、[Rerank](https://help.aliyun.com/zh/model-studio/qwen3-7-text-rerank)、[DeepSeek](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)。小样本每题最多增加一次Rerank和一次Gate，串行延迟相加；P4.1约5.11秒仅为历史Gate均值，不是P4.2组合延迟预测。

**本机离线入口与下一步**

```powershell
Set-Location 'E:\\learning\\IDEA_PROJECTJ\\ai-interview-system'
pwsh -NoProfile -File '.\\tools\\rag-p4-2\\run.ps1' -Mode Offline
pwsh -NoProfile -File '.\\tools\\rag-p4-2\\run.ps1' -Mode Verify
```

入口只支持Offline/Verify，拒绝ExecuteOnce参数，不重新运行P3/P4收费入口，不改历史锁。生产默认仍关闭，独立策略只能在显式导入配置且enabled=true时使用。当前编译使用缓存Java依赖，在target/rag-p4-2/offline/classes输出，没有重编译target/classes、运行无关全量测试或E2E。

下一步：按{link('冻结验证方案', 'target/rag-p4-2/prepared/validation-plan-v1.json')}补齐专用的只读、独立单次锁、逐次预算/usage捕获运行器后，再请求一次明确授权执行这8道新题。运行器复用P3只读测试环境保护，不实例化会创建Collection的生产Milvus初始化器；新锁不得续跑旧P4剩余7题。只有独立质量结果通过并验证业务空结果/Trace链路后，才考虑解除源码冻结并显式接线；默认开关仍应保持关闭。
"""
    destination = OUT / 'report.md'
    if destination.exists() and destination.read_text(encoding='utf-8') != text:
        raise RuntimeError('Refusing to overwrite completed P4.2 report')
    destination.write_text(text, encoding='utf-8')


def verify():
    expected = (OUT / 'freeze.json.sha256').read_text(encoding='ascii').strip()
    assert expected == sha(OUT / 'freeze.json')
    verify_map(read('target/rag-p4-2/prepared/freeze.json')['filesSha256'])
    verify_map(read('target/rag-p4-2/prepared/protected-history.json'))
    print('P4_2_FROZEN_INPUTS=VERIFIED; PROTECTED_HISTORY=UNCHANGED; real calls=0')


if __name__ == '__main__':
    if len(sys.argv) != 2 or sys.argv[1] not in ('Prepare', 'Freeze', 'Verify'):
        raise SystemExit('Only local Prepare/Freeze/Verify are supported; no external execution mode.')
    {'Prepare': prepare, 'Freeze': freeze, 'Verify': verify}[sys.argv[1]]()
