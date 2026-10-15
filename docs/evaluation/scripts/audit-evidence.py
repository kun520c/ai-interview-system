"""Check trace provenance, scoring arithmetic and accidental credential exposure.

Print counts only. Credential values remain local, in memory, and never in output.
"""
import hashlib
import json
import os
import pathlib
import tomllib
import winreg
import yaml

ROOT=pathlib.Path(__file__).resolve().parents[3]
OUT=ROOT/'docs/evaluation'
E=OUT/'evidence'
def read(name): return json.loads((E/name).read_text(encoding='utf-8'))

chunks={c['id']:c for c in read('inventory.json')['chunks']}
for document in read('pipeline.json'):
    chunks.update({c['id']:c for c in document['chunks']})

query_count=0
for name in ('rag-existing.json','rag-expanded.json'):
    for row in read(name):
        assert row['status']=='EXECUTED'
        assert 'ANSWER_NOT_IN_RETRIEVAL' not in row['query']
        result=row['result']
        assert result['requestedTopK']==5
        raw={h['vectorId']:h for h in result['rawHits']}
        for item in result['evidence']:
            chunk=chunks[item['chunkId']]
            assert item['vectorId']==chunk['vector_id']
            assert item['content']==chunk['content']
            assert raw[item['vectorId']]['documentId']==item['documentId']==chunk['document_id']
            assert raw[item['vectorId']]['chunkIndex']==item['chunkIndex']==chunk['chunk_index']
        query_count+=1

for row in read('runtime-judge-e2e.json'):
    s=row['result']['suggestion']
    assert all(isinstance(s[x],int) and 0<=s[x]<=20 for x in
               ('correctnessScore','completenessScore','depthScore','clarityScore','practiceScore'))
    context=row['context']
    assert {p['scoringPointId'] for p in s['scoringPointResults']}=={p['id'] for p in context['scoringPoints']}
    prompt=row['prompt']['userPrompt']
    assert context['mainAnswerContent'] in prompt

report_count=0
for name in ('e2e-first.json','e2e.json'):
    for session in read(name):
        for turn in session['turns']:
            for evaluation in turn['evaluationRows']:
                assert sum(evaluation[x] for x in ('correctness_score','completeness_score','depth_score','clarity_score','practice_score'))==evaluation['total_score']
        if session.get('report',{}).get('httpStatus')==200:
            actual=session['report']['body']['data']['overallScore']
            assert actual==session['expectedReportScore']
            report_count+=1
for result in read('recovery.json'):
    if 'state' in result and result['response']['httpStatus']==200:
        assert result['state']['report_status']=='READY'
        assert result['response']['body']['data']['overallScore']==result['state']['total_score']
        report_count+=1

# Exact-match detection against credentials already available to the project.
secrets=[]
key=winreg.OpenKey(winreg.HKEY_CURRENT_USER,'Environment')
for name in ('JEV_API_KEY','DEEPSEEK_API_KEY','EMBEDDING_API_KEY','JWT_SECRET','DB_PASSWORD'):
    try:
        value=winreg.QueryValueEx(key,name)[0]
        if value: secrets.append(value)
    except FileNotFoundError: pass
    if os.environ.get(name): secrets.append(os.environ[name])
def collect(value):
    if isinstance(value,dict):
        for name,item in value.items():
            if name in ('api-key','secret','password','token') and isinstance(item,str) and item:
                secrets.append(item)
            else: collect(item)
    elif isinstance(value,list):
        for item in value: collect(item)
collect(yaml.safe_load((ROOT/'src/main/resources/application-local.yaml').read_text(encoding='utf-8')))
settings=tomllib.loads((pathlib.Path.home()/'.codex/config.toml').read_text(encoding='utf-8')).get('shell_environment_policy',{}).get('set',{})
if settings.get('ANTHROPIC_AUTH_TOKEN'): secrets.append(settings['ANTHROPIC_AUTH_TOKEN'])
scanned=0
for file in OUT.rglob('*'):
    if file.is_file() and file.suffix in ('.md','.json','.py','.ps1'):
        content=file.read_text(encoding='utf-8')
        if any(secret in content for secret in secrets):
            raise AssertionError('Credential exposure detected in '+str(file.relative_to(ROOT)))
        assert '\r' not in content, 'Unexpected CR escape in generated content'
        scanned+=1

manifest={}
for directory in (OUT/'data',OUT/'evidence'):
    for file in directory.glob('*.json'):
        if file.name=='manifest.json': continue
        manifest[str(file.relative_to(OUT))]=hashlib.sha256(file.read_bytes()).hexdigest()
for file in (ROOT/'src/main/java').rglob('*.java'):
    manifest[str(file.relative_to(ROOT))]=hashlib.sha256(file.read_bytes()).hexdigest()
(E/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(json.dumps(dict(traceQueriesVerified=query_count,reportScoresVerified=report_count,
                     artifactsScanned=scanned,credentialExposure=False,manifestCreated=True)))
