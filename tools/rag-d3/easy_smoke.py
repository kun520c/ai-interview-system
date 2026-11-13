"""One synthetic EASY interview over loopback HTTP. No POST retries."""
from pathlib import Path
import json
import os
import secrets
import sys
import urllib.error
import urllib.request
from uuid import uuid4

from run import WORK
from start_evaluation import BACKEND_PORT

BASE = f'http://127.0.0.1:{BACKEND_PORT}/api'
ANSWERS = {
    'JAVA_BASIC': '我会先明确对象之间的行为契约和资源归属，再验证具体实现的边界。例如异常向上传递时用 try-with-resources 管理本方法拥有的流，避免资源泄漏。',
    'JAVA_COLLECTION': '我会根据访问方式、去重和顺序要求选集合。数组列表适合按下标读，哈希集合要求稳定的相等关系和哈希值，共享修改还需要检查并发约束。',
    'SPRING': '我会用构造器明确依赖，通过容器装配 Bean。MVC 的请求体先经消息转换，校验失败要返回清楚的输入错误。事务边界由调用到的代理和事务管理器决定。',
    'MYSQL': '先确定一次业务的原子边界。需要一起成功的多条更新放在事务中。索引要依据查询条件和访问范围选择，并用 EXPLAIN 核对执行计划。',
    'NETWORK': '先区分传输层和应用层。TCP 提供有序字节流，应用仍要划分消息边界。客户端超时意味着没收到响应，服务端可能已经完成，需要应用定义重试语义。',
}
FOLLOWUP = '我能确认的是，这个结论依赖当前实现和业务前提。我的做法是先写一个最小对照例，观察失败路径并记录可重现证据。'


def write_new(name, value):
    with (WORK / name).open('x', encoding='utf-8') as output:
        json.dump(value, output, ensure_ascii=False, indent=2)


def api(path, body=None, token=None):
    headers = {'Accept': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(BASE + path, data=data, headers=headers)
    with urllib.request.urlopen(request, timeout=100) as response:
        value = json.load(response)
    if value['code'] != 200:
        raise RuntimeError(f'BUSINESS_{value["code"]}')
    return value.get('data')


def main():
    os.umask(0o077)
    resume = sys.argv[1:] == ['--recovery']
    if sys.argv[1:] not in ([], ['--recovery']):
        raise SystemExit('Usage: easy_smoke.py [--recovery]')
    first_id = None
    if resume:
        if not (WORK / 'smoke-recovery-started.json').exists() or (WORK / 'smoke-recovery-result.json').exists():
            raise SystemExit('Missing bounded recovery server or existing recovery result')
        credentials = json.loads((WORK / 'smoke-credentials.json').read_text())
        original = json.loads((WORK / 'smoke-result.json').read_text())
        original_answer = json.loads((WORK / 'smoke-original-answer.json').read_text())
        first_id = original_answer['requestId']
    else:
        if not (WORK / 'smoke-started.json').exists() or (WORK / 'smoke-credentials.json').exists():
            raise SystemExit('Missing bounded server or existing one-run credentials')
        identity = 'd3easy_' + uuid4().hex[:10]
        credentials = {'account': identity, 'password': secrets.token_urlsafe(24)}
        write_new('smoke-credentials.json', credentials)
    progress = {'state': 'STARTED', 'answers': []}
    step = 'register'
    try:
        if not resume:
            api('/auth/register', {**credentials, 'username': 'D3 EASY synthetic', 'email': identity + '@example.test'})
        step = 'login'
        login = api('/auth/login', credentials)
        token = login['accessToken']
        progress['userId'] = login['userId']
        step = 'me'
        if api('/users/me', token=token)['userId'] != login['userId']:
            raise RuntimeError('IDENTITY')
        step = 'create'
        created = api('/interviews', {'difficulty': 'EASY'}, token)
        session = created['sessionId']
        progress['sessionId'] = session
        if resume and (session != original['sessionId'] or login['userId'] != original['userId']):
            raise RuntimeError('RECOVERY_SESSION_IDENTITY')
        current = api('/interviews/current', token=token)
        if created['plannedQuestionCount'] != 5 or current['sessionId'] != session:
            raise RuntimeError('EASY_PLAN')
        question = current['currentQuestion']
        seen = set()
        main_count = followup_count = 0
        while question:
            question_id = question['interviewQuestionId']
            kind = question['questionType']
            if kind == 'MAIN':
                main_count += 1
                answer = ANSWERS[question['category']]
            elif kind == 'FOLLOW_UP':
                followup_count += 1
                if not progress['answers'] or progress['answers'][-1]['kind'] != 'MAIN':
                    raise RuntimeError('FOLLOWUP_ORDER')
                answer = FOLLOWUP
            else:
                raise RuntimeError('QUESTION_TYPE')
            if question_id in seen or main_count > 5 or followup_count > main_count:
                raise RuntimeError('ANSWER_LIMIT')
            seen.add(question_id)
            step = 'answer-' + str(len(seen))
            request_id = first_id if resume and len(seen) == 1 else str(uuid4())
            if resume and len(seen) == 1 and question_id != original_answer['questionId']:
                raise RuntimeError('RECOVERY_QUESTION_IDENTITY')
            response = api(f'/interviews/{session}/answers', {'interviewQuestionId': question_id,
                           'answerContent': answer, 'requestId': request_id}, token)
            action = response['nextAction']
            progress['answers'].append({'kind': kind, 'questionId': question_id, 'action': action,
                                        'status': response['sessionStatus']})
            question = response['nextQuestion']
            if action == 'FINISH' and (question is not None or response['sessionStatus'] != 'COMPLETED'):
                raise RuntimeError('FINISH_STATE')
        if main_count != 5 or progress['answers'][-1]['action'] != 'FINISH':
            raise RuntimeError('INCOMPLETE_INTERVIEW')
        step = 'report'
        report_path = f'/interviews/{session}/report'
        api(report_path, {}, token)
        report = api(report_path, token=token)
        if report['reportStatus'] != 'READY':
            raise RuntimeError('REPORT_' + report['reportStatus'])
        progress['reportStatus'] = report['reportStatus']
        step = 'detail-history'
        detail = api(f'/interviews/{session}', token=token)
        history = api('/interviews/history?page=1&pageSize=10', token=token)
        if detail['status'] != 'COMPLETED' or str(session) not in {
                str(row['sessionId']) for row in history.get('records', history.get('items', []))}:
            raise RuntimeError('DETAIL_OR_HISTORY')
        progress.update(state='COMPLETED', main=main_count, followup=followup_count)
    except (OSError, ValueError, RuntimeError, KeyError) as error:
        progress.update(state='STOPPED', failedAt=step, errorType=type(error).__name__)
        if isinstance(error, urllib.error.HTTPError):
            progress['httpStatus'] = error.code
        if isinstance(error, RuntimeError) and str(error).replace('_', '').isalnum():
            progress['errorCode'] = str(error)
    finally:
        write_new('smoke-recovery-result.json' if resume else 'smoke-result.json', progress)
    print(json.dumps({'state': progress['state'], 'answerResponses': len(progress['answers']),
                      'failedAt': progress.get('failedAt'), 'httpStatus': progress.get('httpStatus'),
                      'errorCode': progress.get('errorCode')}, ensure_ascii=False))
    return 0 if progress['state'] == 'COMPLETED' else 1


if __name__ == '__main__':
    sys.exit(main())
