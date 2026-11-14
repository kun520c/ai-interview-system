#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deterministic D1 assembly and structural checks. Content acceptance is a separate review."""
import collections
import copy
import hashlib
import json
from pathlib import Path
import re

if not __debug__:
    raise SystemExit('Do not run this validation tool with Python -O')

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'docs/question-bank'
DRAFTS = BASE / 'd1-drafts'
FIELDS = ('category', 'knowledgePoint', 'difficulty', 'questionContent', 'referenceAnswer', 'scoringPoints')
OLD_SHA = 'ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3'
GROUPS = ['EASY_AND_JAVA_MEDIUM_20261114.json', 'MEDIUM_AND_HARD_JAVA_20261114.json', 'HARD_20261114.json']

def sha(data):
    return hashlib.sha256(data).hexdigest()

def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')

def request(q):
    return {k:q[k] for k in FIELDS}

def text(value):
    return '\n'.join('- ' + s for s in value) if isinstance(value, list) else value

def build():
    original_path = BASE / 'PHASE_1_QUESTION_BANK_FINAL.json'
    raw = original_path.read_bytes()
    assert sha(raw) == OLD_SHA, 'Frozen bank changed'
    original = json.loads(raw)
    old_markdown = (BASE / 'PHASE_1_QUESTION_BANK_FINAL.md').read_text()
    titles = dict(re.findall(r'^## ([EMH]\d{2}) — (.+)$', old_markdown, re.M))
    sequences = collections.Counter()
    old_by_id = {}
    bank = []
    for q0 in original:
        q = copy.deepcopy(q0)
        sequences[q['difficulty']] += 1
        bank_id = q['difficulty'][0] + f"{sequences[q['difficulty']]:02d}"
        old_by_id[bank_id] = q0
        sources = list((ROOT / 'docs/rag/source-documents').glob(bank_id.lower() + '_*.md'))
        assert len(sources) == 1
        q.update(bankId=bank_id, title=titles[bank_id], versionScope='沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。',
                 sources=[{'title':'冻结 V1 题库', 'url':str(original_path), 'section':bank_id,
                           'claim':'原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）'},
                          {'title':'既有知识资料与原始官方来源', 'url':str(sources[0]), 'section':bank_id,
                           'claim':'历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过'}],
                 coverageDifference='保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。',
                 acceptableAlternatives='评分按机制、条件和可验证解释；不要求逐字复述参考答案。',
                 misconceptions='以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。')
        bank.append(q)
    revision = json.loads((DRAFTS / 'H01_REVISION_20261114.json').read_text())
    bank = [revision if q['bankId']=='H01' else q for q in bank]
    additions = []
    for filename in GROUPS:
        additions.extend(json.loads((DRAFTS / filename).read_text()))
    assert len(additions) == 42
    bank += additions
    bank.sort(key=lambda q: ('EMH'.index(q['bankId'][0]), int(q['bankId'][1:])))
    plan = (ROOT / 'docs/verification/D1_QUESTION_BANK_EXPANSION_PLAN_20261113.md').read_text()
    planned = {i:(cat, diff, kp) for i,cat,diff,kp in re.findall(
        r'^\| ([EMH]\d{2}) \| [^|]+ \| (\w+) \| (\w+) \| `([^`]+)` \|', plan, re.M)}
    assert len(planned)==42
    enums = {}
    for name in ('QuestionCategory', 'QuestionDifficulty', 'QuestionPointType'):
        source = (ROOT / f'src/main/java/com/kun/aiinterview/question/enums/{name}.java').read_text()
        enums[name] = set(re.findall(r'^\s*([A-Z][A-Z_]+)\s*[,;}]', source, re.M))
    identities = set(); keys = set(); contents = set(); answers = set()
    lineage = []
    max_bytes = collections.Counter()
    for q in bank:
        i = q['bankId']
        assert i not in identities; identities.add(i)
        assert q['category'] in enums['QuestionCategory']
        assert q['difficulty'] in enums['QuestionDifficulty']
        kp = q['knowledgePoint']
        assert re.fullmatch('[a-z][a-z0-9_]*', kp) and len(kp.encode('utf-16-le'))//2 <= 100
        assert kp not in keys; keys.add(kp)
        assert q['questionContent'].strip() not in contents; contents.add(q['questionContent'].strip())
        assert q['referenceAnswer'].strip() not in answers; answers.add(q['referenceAnswer'].strip())
        for field in ('questionContent', 'referenceAnswer'):
            value = q[field]
            assert isinstance(value,str) and value.strip() and len(value.encode('utf-8'))<=65535
            assert len(value.encode('utf-16-le'))//2 <= 65535
            max_bytes[field] = max(max_bytes[field],len(value.encode('utf-8')))
        points=q['scoringPoints']
        assert 4<=len(points)<=6 and sum(p['weight'] for p in points)==100
        assert any(p['pointType']=='CORE' for p in points)
        assert len({p['content'].strip() for p in points})==len(points)
        for p in points:
            assert set(p)=={'pointType','content','weight'}
            assert p['pointType'] in enums['QuestionPointType'] and type(p['weight']) is int and 1<=p['weight']<=100
            assert p['content'].strip() and len(p['content'].encode('utf-8'))<=65535
            max_bytes['pointContent']=max(max_bytes['pointContent'],len(p['content'].encode('utf-8')))
        for field in ('versionScope','sources','coverageDifference','acceptableAlternatives','misconceptions'):
            assert q[field], (i,field)
        for s in q['sources']:
            assert all(s.get(k) for k in ('title','url','section','claim'))
            assert s['url'].startswith('https://') or Path(s['url']).is_file()
        if i in planned:
            assert (q['category'],q['difficulty'],kp)==planned[i],i
        else:
            old=old_by_id[i]
            if i!='H01': assert request(q)==old,i
            else:
                assert all(q[k]==old[k] for k in ('category','knowledgePoint','difficulty','questionContent'))
                assert len(points)==5 and [p['weight'] for p in points]==[p['weight'] for p in old['scoringPoints']]
                assert all(points[n]==old['scoringPoints'][n] for n in (0,1,2,4))
        q['status']='ENABLED'
        q['revision']=2 if i=='H01' else 1
        q['provenance']='V1_REVISED' if i=='H01' else ('D1_NEW' if i in planned else 'V1_UNCHANGED')
        lineage.append({'bankId':i,'knowledgePoint':kp,'requestSha256':sha(canonical(request(q))),
                        'revision':q['revision'],'provenance':q['provenance'],
                        'previousRequestSha256':sha(canonical(old_by_id[i])) if i in old_by_id else None})
    counts=collections.Counter(q['difficulty'] for q in bank)
    assert counts=={'EASY':15,'MEDIUM':21,'HARD':27}
    buckets=collections.Counter((q['difficulty'],q['category']) for q in bank)
    assert len(buckets)==21 and set(buckets.values())=={3}
    root={'bankVersion':'D1-v2-20261114','date':'2026-11-14','baseVersion':'PHASE_1_QUESTION_BANK_FINAL',
          'baseSha256':OLD_SHA,'scoringPolicy':'按机制和条件给分，允许准确等价表述。权重表示覆盖重要性，不替代 Java 五维总分计算。',
          'reviewBoundary':'独立代理内容审查，非真人候选人试答或招聘难度盲评；实测校准待后续。',
          'questions':bank}
    output=BASE/'D1_QUESTION_BANK_V2_20261114.json'
    output.write_text(json.dumps(root,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    summary={'bankSha256':sha(output.read_bytes()),'questionCount':len(bank),'pointCount':sum(len(q['scoringPoints']) for q in bank),
             'addedQuestions':len(additions),'addedPoints':sum(len(q['scoringPoints']) for q in additions),
             'difficultyCounts':dict(counts),'categoryCounts':dict(collections.Counter(q['category'] for q in bank)),
             'buckets':[{'difficulty':d,'category':c,'count':n} for (d,c),n in sorted(buckets.items())],
             'maximumUtf8Bytes':dict(max_bytes),'exactDuplicateQuestionContent':0,'exactDuplicateReferenceAnswer':0,
             'logicalIdentityDuplicates':0,'sourceLineage':lineage}
    (BASE/'D1_BANK_MANIFEST_20261114.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    md=['# D1｜63 道 Java 后端正式候选题库 v2','',
        '日期：2026-11-14。基线 V1 的原始文件保留；本文件由同版 JSON 生成。新增 42 题，H01 修订答案与 SP4。',
        '题号是内容身份，数据库 ID 在导入验收清单中另行映射。所有题目状态目标为 ENABLED。',
        '参考答案是适合口述的参考表达，评分接受准确等价表述和合理替代方案。独立代理审查不等于真人试答校准。',
        f"当前内容共 {len(bank)} 题 / {summary['pointCount']} 点，新增 {len(additions)} 题 / {summary['addedPoints']} 点。",'',
        '[逐题审查与修订记录](../verification/D1_QUESTION_BANK_CONTENT_REVIEW_20261114.md) · [版本与指纹清单](D1_BANK_MANIFEST_20261114.json)', '']
    for q in bank:
        md += [f"## {q['bankId']}｜{q['title']}",'',f"分类：`{q['category']}`；难度：`{q['difficulty']}`；知识点：`{q['knowledgePoint']}`；修订：{q['revision']}；来源：{q['provenance']}。",'',
               '适用版本：'+q['versionScope'],'','### 面试问题','',q['questionContent'],'','### 参考答案','',q['referenceAnswer'],'','### 评分点（合计 100）','']
        for idx,p in enumerate(q['scoringPoints'],1):
            md += [f"{idx}. **{q['bankId']}-SP{idx} · {p['pointType']} · {p['weight']}**：{p['content']}"]
        md += ['','### 技术依据','']
        for s in q['sources']:
            md += [f"- [{s['title']}]({s['url']}) — {s['section']}：{s['claim']}"]
        md += ['','覆盖差异：'+q['coverageDifference'],'','可接受表达／替代方案：','',text(q['acceptableAlternatives']),'','应识别的误解：','',text(q['misconceptions']),'']
    (BASE/'D1_QUESTION_BANK_V2_20261114.md').write_text('\n'.join(md),encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k not in ('sourceLineage','buckets')},ensure_ascii=False))

if __name__=='__main__':
    build()
