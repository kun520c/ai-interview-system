"""Prepare or run the single-turn, non-persistent D3-E paired scoring probe."""
from collections import Counter
from decimal import Decimal
import fcntl
from hashlib import sha256
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

from balance import check
from config import protected_config
from run import JDK_OPTIONS, WORK, runtime_classpath

RUN = Path('backups/D3E_20261114_scoring_v1')
LEDGER = Path('docs/rag/d3-d/API_USAGE_20261114.json')
CONFIG = Path.home() / '.config/ai-interview/evaluation-deepseek.env'
MODEL_CAP = 16
MAX_REQUEST_RESERVATION_CNY = (Decimal(48000) * 2 + Decimal(4096) * 8) / 1_000_000


def owner_only(path):
    return path.is_dir() and not path.is_symlink() and path.stat().st_uid == os.getuid() and path.stat().st_mode & 0o077 == 0


def read_previous_usage():
    ledger = json.loads(LEDGER.read_bytes())
    counts = {}
    for key in ('embedding', 'deepseek'):
        filename = key + '-usage.jsonl'
        data = (WORK / filename).read_bytes()
        if sha256(data).hexdigest() != ledger['privateJournalsSha256'][filename]:
            raise SystemExit('Original cumulative journal integrity changed; reconcile before a real call')
        events = Counter(json.loads(line)['event'] for line in data.splitlines())
        if events['attempt'] != events['headers']:
            raise SystemExit('Unresolved previous request attempt')
        counts[key] = events['attempt']
    if counts != {'embedding': ledger['actual']['embeddingHttpAttempts'],
                  'deepseek': ledger['actual']['deepseekModelHttpAttempts']}:
        raise SystemExit('Cumulative request count differs from ledger')
    estimate = (Decimal(ledger['actual']['embeddingEstimatedCny'])
                + Decimal(ledger['actual']['deepseekPeakPriceEstimateCny']))
    reserved = Decimal(MODEL_CAP) * MAX_REQUEST_RESERVATION_CNY
    if counts['embedding'] > 300 or counts['deepseek'] + MODEL_CAP > 200 or estimate + reserved > 20:
        raise SystemExit('Cumulative paid request guard')
    return counts, estimate, reserved


def main():
    os.umask(0o077)
    if sys.argv[1:] not in (['prepare'], ['execute'], ['resume-before-first-request']):
        raise SystemExit('Usage: d3e_scoring.py prepare | execute | resume-before-first-request')
    mode = sys.argv[1]
    if not owner_only(WORK):
        raise SystemExit('Historical run directory must be owner-only')
    if mode == 'prepare':
        RUN.mkdir(mode=0o700, exist_ok=True)
    if not owner_only(RUN):
        raise SystemExit('Scoring run directory must be owner-only')
    env = {key: os.environ[key] for key in ('PATH', 'HOME', 'JAVA_HOME', 'LANG', 'TMPDIR') if key in os.environ}
    cp = runtime_classpath()
    with (WORK / 'execution.lock').open('a') as lock, tempfile.TemporaryDirectory(prefix='d3e-score-') as classes:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        subprocess.run(['javac', '-proc:none', '-cp', cp, '-d', classes,
                        *map(str, sorted(Path('tools/rag-d3').glob('D3*.java')))], check=True, env=env)
        java = ['java', *JDK_OPTIONS, '-cp', f'{classes}:{cp}', 'D3EScoringProbe']
        args = [*java, mode, str(RUN)]
        if mode != 'prepare':
            marker_path = RUN / 'started.json'
            resume = mode == 'resume-before-first-request'
            if marker_path.exists() != resume or (RUN / 'resumed-before-first-request.json').exists() \
                    or (RUN / 'deepseek-usage.jsonl').exists() or any(RUN.glob('http-*.json')):
                raise SystemExit('Scoring model requests may have started; no retry')
            counts, estimate, reserved = read_previous_usage()
            frozen = (RUN / 'frozen-scoring-plan.json').read_bytes()
            frozen_sha = sha256(frozen).hexdigest()
            credentials = protected_config(CONFIG)
            if credentials.get('DEEPSEEK_BASE_URL', '').rstrip('/') != 'https://api.deepseek.com' \
                    or credentials.get('DEEPSEEK_MODEL') != 'deepseek-flash' or not credentials.get('DEEPSEEK_API_KEY'):
                raise SystemExit('Unexpected private DeepSeek configuration')
            if Decimal(os.environ.get('D3E_VERIFIED_RESERVATION_CNY', '0')) != reserved:
                raise SystemExit(f'Check official rate and reserve {reserved} CNY before execution')
            subprocess.run([*java, 'verify', str(RUN), frozen_sha], check=True, env=env)
            identity = {'frozenSha256': frozen_sha, 'priorAttempts': counts, 'priorPeakEstimateCny': str(estimate),
                        'reservedCny': str(reserved), 'maxModelAttempts': MODEL_CAP}
            if resume:
                prior = json.loads(marker_path.read_bytes())
                if any(prior.get(key) != value for key, value in identity.items()):
                    raise SystemExit('Frozen plan or cumulative budget changed during recovery')
                balance = prior['balance']
                marker_path = RUN / 'resumed-before-first-request.json'
            else:
                balance = check(reserved)
            if not balance['available'] or not any(row['sufficientForReservation'] for row in balance['accounts']):
                raise SystemExit('DeepSeek lacks a visible CNY balance for the planned reservation')
            with marker_path.open('x', encoding='utf-8') as marker:
                json.dump({**identity, 'balance': balance, 'freeBalanceRequests': 0 if resume else 1,
                           'inputReservation': 'serialized byte cap used as token estimate',
                           'vendorBillReconciled': False}, marker, indent=2)
            env.update(DEEPSEEK_API_KEY=credentials['DEEPSEEK_API_KEY'], D3E_SCORING_ARMED='true')
            args = [*java, 'execute', str(RUN), frozen_sha, str(counts['deepseek'])]
        return subprocess.call(args, env=env)


if __name__ == '__main__':
    sys.exit(main())
