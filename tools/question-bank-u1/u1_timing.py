"""Run a single browser EASY timing smoke with the U1 cumulative paid guard."""
from base64 import b64encode
from contextlib import ExitStack
from decimal import Decimal
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import secrets
import signal
import socket
import stat
import subprocess
import sys
import tempfile
import time
from urllib.request import build_opener, ProxyHandler

TOOLS = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(TOOLS / 'u1'), str(TOOLS / 'rag-d3')]
from balance import check as check_balance
from config import evaluation_database_config, protected_config
from run import JDK_OPTIONS
from u1_budget import D3BCD, RUN, check_budget, execution_lock, is_owner_only, require


CHROME = Path('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome')
MYSQL = ('jdbc:mysql://127.0.0.1:3308/ai_interview_evaluation'
         '?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=20000')
TIMING_LOG = re.compile(r'interview_timing operation=(ANSWER|REPORT) timingId=([a-f0-9-]{36})'
                        r' httpStatus=(\d+) unhandledError=(true|false) durationUs=(\d+) phasesUs=\{(.*)\}$')
EMBEDDING_KEYS = ('EMBEDDING_BASE_URL', 'EMBEDDING_API_KEY', 'EMBEDDING_MODEL',
                  'EMBEDDING_DIMENSION', 'EMBEDDING_PROFILE_VERSION')


def write_new(path, value):
    with path.open('x', encoding='utf-8') as output:
        print(json.dumps(value, ensure_ascii=False, indent=2), file=output)


def compile_runner(build, env):
    java_home = subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True, env=env).strip()
    cp = 'target/classes:' + (D3BCD / 'classpath.txt').read_text().strip()
    files = [TOOLS / 'question-bank-u1/U1TimingServer.java']
    files.extend(TOOLS / 'rag-d3' / name for name in ('D3SmokeServer.java', 'D3Inventory.java', 'D3Corpus.java', 'D3PaidEmbedding.java'))
    subprocess.run([java_home + '/bin/javac', '-proc:none', '-cp', cp, '-d', str(build), *map(str, files)], check=True, env=env)
    node_script = TOOLS / 'question-bank-u1/u1_timing_browser.mjs'
    subprocess.run(['node', '--check', str(node_script)], check=True, env=env)
    return [java_home + '/bin/java', *JDK_OPTIONS, '-cp', f'{build}:{cp}', 'U1TimingServer'], node_script


def stop_owned(process):
    if process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=5)


def start_owned(stack, command, env, log):
    output = stack.enter_context(log.open('xb'))
    process = subprocess.Popen(command, env=env, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    stack.callback(stop_owned, process)
    return process


def await_http(process, url, max_wait):
    opener = build_opener(ProxyHandler({}))
    deadline = time.monotonic() + max_wait
    while time.monotonic() < deadline and process.poll() is None:
        try:
            with opener.open(url, timeout=1) as response:
                if response.status == 200:
                    return
        except OSError:
            pass
        time.sleep(0.2)
    raise SystemExit('U1_SERVICE_NOT_READY')


def check_ports(ports):
    require(len(set(ports)) == 3 and all(1024 <= port <= 65535 and port not in {18482, 15174, 3308, 19532}
                                       for port in ports), 'U1_TIMING_PORTS')
    with ExitStack() as stack:
        for port in ports:
            listener = stack.enter_context(socket.socket())
            listener.bind(('127.0.0.1', port))


def backend_phases(path):
    result = {}
    for line in path.read_text(errors='replace').splitlines():
        if match := TIMING_LOG.search(line):
            operation, timing_id, status, unhandled, duration, phases = match.groups()
            if timing_id in result:
                raise SystemExit('U1_DUPLICATE_TIMING_ID')
            durations = {phase: int(value) for phase, value in (item.split('=') for item in phases.split(', ') if item)}
            result[timing_id] = dict(operation=operation, httpStatus=int(status), unhandledError=unhandled == 'true',
                                     durationUs=int(duration), phasesUs=durations)
    return result


def summarize(exit_code):
    phases = backend_phases(RUN / 'timing-backend.log')
    browser_path = RUN / 'timing-browser.json'
    browser = json.loads(browser_path.read_bytes()) if browser_path.exists() else {'status': 'STOPPED'}
    for row in browser.get('answers', []) + browser.get('reports', []):
        row['backend'] = phases.get(row.get('timingId'))
    readback = RUN / 'timing-post-readback.json'
    rows = browser.get('answers', []) + browser.get('reports', [])
    expected = browser.get('observedMutationRequests', {})
    joined = sum(row['backend'] is not None and row['backend']['httpStatus'] == row['status'] for row in rows)
    complete = browser.get('status') == 'COMPLETED' and len(expected.get('answer', [])) == len(browser.get('answers', [])) \
        and len(expected.get('report', [])) == 1 and joined == len(rows) and readback.exists()
    write_new(RUN / 'timing-result.json', {'browser': browser, 'executionExitCode': exit_code if exit_code else int(not complete),
                                         'timingEvidenceComplete': complete,
                                         'independentSyntheticDbReadback': json.loads(readback.read_bytes()) if readback.exists() else None,
                                         'timingsJoined': joined,
                                         'budget': check_budget(0, 0)})
    return complete


def execute(java, node_script, build, env, ports):
    require(os.environ.get('U1_WINDOW_CONFIRMED') == 'true', 'U1_WINDOW_REQUIRED')
    database = evaluation_database_config()
    require(database['EVALUATION_DB_URL'] == MYSQL, 'U1_DB_TARGET')
    require(CHROME.is_file(), 'U1_LOCAL_CHROME_REQUIRED')
    check_ports(ports)
    budget = check_budget(8, 7)
    require(not (RUN / 'timing-started.json').exists() and not any(
        operation.startswith('timing-') for ops in budget['u1AttemptsByOperation'].values() for operation in ops),
            'U1_NO_TIMING_RETRY')
    require(budget['u1Attempts'] == {'deepseek': 16, 'embedding': 0}
            and all(budget['u1UsageComplete'].values()), 'U1_TIMING_REQUIRES_COMPLETED_SCORING_ALLOCATION')
    reserved = Decimal(budget['reservationCny'])
    require(Decimal(os.environ.get('U1_VERIFIED_RESERVATION_CNY', '0')) == reserved, 'U1_PRICE_RESERVATION_REQUIRED')
    provider_dir = Path.home() / '.config/ai-interview'
    deepseek = protected_config(provider_dir / 'evaluation-deepseek.env')
    embedding = protected_config(provider_dir / 'evaluation-embedding.env')
    require(deepseek.get('DEEPSEEK_BASE_URL', '').rstrip('/') == 'https://api.deepseek.com'
            and deepseek.get('DEEPSEEK_MODEL') == 'deepseek-flash' and bool(deepseek.get('DEEPSEEK_API_KEY'))
            and all(embedding.get(key) for key in EMBEDDING_KEYS), 'U1_PRIVATE_MODEL_CONFIGURATION')
    available = check_balance(reserved)
    require(available['available'] and any(row['sufficientForReservation'] for row in available['accounts']), 'U1_CNY_BALANCE')
    backend, frontend, cdp = ports
    run_key = secrets.token_urlsafe(32)
    spring_env = {**env, **{key: embedding[key] for key in EMBEDDING_KEYS}, 'DEEPSEEK_API_KEY': deepseek['DEEPSEEK_API_KEY'],
                  **database,
                  'EVALUATION_MILVUS_URI': 'http://127.0.0.1:19532', 'EVALUATION_MILVUS_DATABASE_NAME': 'default',
                  'EVALUATION_MILVUS_COLLECTION_NAME': 'knowledge_chunk_vectors_evaluation', 'MILVUS_ENABLED': 'true',
                  'DEEPSEEK_ENABLED': 'true', 'DEEPSEEK_MAX_TOKENS': '4096', 'JWT_SECRET': b64encode(secrets.token_bytes(48)).decode(),
                  'U1_WINDOW_CONFIRMED': 'true', 'U1_TIMING_BROWSER_KEY': run_key,
                  'U1_PREVIOUS_LOCAL_DEEPSEEK_ATTEMPTS': str(budget['u1Attempts']['deepseek'])}
    write_new(RUN / 'timing-started.json', {**budget, 'ports': {'backend': backend, 'frontend': frontend, 'cdp': cdp},
                                           'freeBalanceRequests': 1, 'maxSyntheticSessions': 1,
                                           'answersRemaining': 7, 'reportsRemaining': 1,
                                           'backendTimingClassSha256': sha256(Path('target/classes/com/kun/aiinterview/common/observability/InterviewRequestTiming.class').read_bytes()).hexdigest()})
    exit_code = 1
    try:
        with ExitStack() as owned:
            service = start_owned(owned, [*java, '--spring.profiles.active=evaluation',
                                 '--spring.config.location=classpath:/application.yaml', '--spring.sql.init.mode=never',
                                 '--server.address=127.0.0.1', f'--server.port={backend}'], spring_env, RUN / 'timing-backend.log')
            vite = start_owned(owned, ['npm', '--prefix', 'frontend', 'run', 'dev', '--', '--host', '127.0.0.1',
                                      '--port', str(frontend), '--strictPort'],
                               {**env, 'AI_INTERVIEW_BACKEND_URL': f'http://127.0.0.1:{backend}'}, RUN / 'timing-vite.log')
            await_http(vite, f'http://127.0.0.1:{frontend}/login', 20)
            # /api/auth/login with GET gives an expected non-200; the known local application startup line is the readiness gate.
            deadline = time.monotonic() + 60
            while service.poll() is None and time.monotonic() < deadline:
                if f'Tomcat started on port {backend}' in (RUN / 'timing-backend.log').read_text(errors='replace'):
                    break
                time.sleep(0.2)
            else:
                raise SystemExit('U1_BACKEND_NOT_READY')
            profile = build / 'chrome-profile'
            start_owned(owned, [str(CHROME), '--headless=new', '--disable-background-networking', '--disable-sync',
                        '--no-first-run', '--no-default-browser-check', '--no-proxy-server',
                        '--remote-debugging-address=127.0.0.1', f'--remote-debugging-port={cdp}',
                        f'--user-data-dir={profile}', 'about:blank'], env, RUN / 'timing-chrome.log')
            exit_code = subprocess.run(['node', str(node_script)], env={**env, 'U1_TIMING_BROWSER_KEY': run_key,
                                       'U1_TIMING_FRONTEND_PORT': str(frontend), 'U1_TIMING_CDP_PORT': str(cdp)}, timeout=750).returncode
            if exit_code == 0:
                exit_code = subprocess.run([*java, 'readback'], env=spring_env, timeout=30).returncode
    finally:
        if not summarize(exit_code) and exit_code == 0:
            exit_code = 1
    return exit_code


def main():
    if len(sys.argv) not in (2, 5) or sys.argv[1] not in ('verify', 'execute'):
        raise SystemExit('Usage: python3 -B tools/question-bank-u1/u1_timing.py verify | execute BACKEND_PORT FRONTEND_PORT CDP_PORT')
    mode = sys.argv[1]
    require((mode == 'verify') == (len(sys.argv) == 2), 'U1_TIMING_ARGUMENTS')
    env = {key: os.environ[key] for key in ('PATH', 'HOME', 'LANG', 'TMPDIR') if key in os.environ}
    with execution_lock(), tempfile.TemporaryDirectory(prefix='u1-timing-') as folder:
        build = Path(folder)
        java, node_script = compile_runner(build, env)
        if mode == 'verify':
            require(is_owner_only(RUN, stat.S_ISDIR), 'U1_PRIVATE_TIMING_DIRECTORY')
            print('U1 timing server compiled; Chrome browser runner syntax checked; no network, credentials, or writes outside the build/lock.')
            return 0
        return execute(java, node_script, build, env, tuple(map(int, sys.argv[2:])))


if __name__ == '__main__':
    sys.exit(main())
