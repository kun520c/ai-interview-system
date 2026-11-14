"""Cumulative U1 paid execution guard. Call check_budget while holding execution_lock."""
from collections import Counter
from contextlib import contextmanager
from decimal import Decimal
import fcntl
from hashlib import sha256
import json
import os
from pathlib import Path
import stat


RUN = Path('backups/U1_20261114')
D3BCD = Path('backups/D3_BCD_20261114')
D3E = Path('backups/D3E_20261114_scoring_v1')
PREVIOUS_BCD = Path('docs/rag/d3-d/API_USAGE_20261114.json')
PREVIOUS_E = Path('docs/rag/d3-e/API_USAGE_20261114.json')
PREVIOUS_SCORE = Path('docs/rag/d3-e/SCORING_DIAGNOSTIC_20261114.json')
DEEPSEEK_JOURNAL = RUN / 'deepseek-usage.jsonl'
EMBEDDING_JOURNAL = RUN / 'embedding-usage.jsonl'
OLD_COUNTS = {'deepseek': 26, 'embedding': 215}
U1_LIMIT = {'deepseek': 24, 'embedding': 12}
CUMULATIVE_LIMIT = {'deepseek': 200, 'embedding': 300}
MILLION = Decimal(1_000_000)
INPUT_RATE = Decimal(2)
CACHE_RATE = Decimal('0.04')
OUTPUT_RATE = Decimal(8)
EMBEDDING_RATE = Decimal('0.5')


def require(condition, code):
    if not condition:
        raise SystemExit(code)


def is_owner_only(path, key):
    status = path.lstat()
    return status.st_uid == os.getuid() and status.st_mode & 0o077 == 0 and key(status.st_mode)


@contextmanager
def execution_lock():
    """Serialize all guarded U1 callers and exclude guarded historical D3 callers."""
    os.umask(0o077)
    RUN.mkdir(mode=0o700, exist_ok=True)
    for folder in (D3BCD, D3E, RUN):
        require(is_owner_only(folder, stat.S_ISDIR), 'U1_PROTECTED_DIRECTORY')
    # Use the same lock order for each U1 caller; D3-E used the original D3-BCD lock.
    with (D3BCD / 'execution.lock').open('a') as older, (RUN / 'execution.lock').open('a') as current:
        for lock in (older, current):
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


def records(path, expected_sha=None):
    if not path.exists() and expected_sha is None:
        return []
    require(is_owner_only(path, stat.S_ISREG), 'U1_PROTECTED_JOURNAL')
    raw = path.read_bytes()
    require(expected_sha is None or sha256(raw).hexdigest() == expected_sha, 'U1_PRIOR_JOURNAL_CHANGED')
    return [json.loads(line) for line in raw.splitlines()]


def previous():
    bcd = json.loads(PREVIOUS_BCD.read_bytes())
    e = json.loads(PREVIOUS_E.read_bytes())
    diagnostic = json.loads(PREVIOUS_SCORE.read_bytes())
    prior = {}
    for name in OLD_COUNTS:
        file = name + '-usage.jsonl'
        events = records(D3BCD / file, bcd['privateJournalsSha256'][file])
        frequency = Counter(row['event'] for row in events)
        expected_bcd = bcd['actual']['embeddingHttpAttempts' if name == 'embedding' else 'deepseekModelHttpAttempts']
        require(frequency['attempt'] == frequency['headers'] == expected_bcd, 'U1_INVALID_D3BCD_ATTEMPTS')
        prior[name] = expected_bcd
    extra = records(D3E / 'deepseek-usage.jsonl', diagnostic['privateUsageJournalSha256'])
    frequency = Counter(row['event'] for row in extra)
    require(frequency['attempt'] == frequency['headers'] == frequency['usage']
            == diagnostic['httpAttempts'] == e['d3e']['scoringDeepSeekAttempts'], 'U1_INVALID_D3E_ATTEMPTS')
    prior['deepseek'] += frequency['attempt']
    estimate = (Decimal(bcd['actual']['embeddingEstimatedCny'])
                + Decimal(bcd['actual']['deepseekPeakPriceEstimateCny']) + Decimal(diagnostic['peakEstimateCny']))
    require(prior == OLD_COUNTS == {f: e['cumulative'][f + 'Attempts'] for f in prior}
            and estimate == Decimal(e['cumulative']['peakEstimatedCny']), 'U1_PRIOR_TOTALS')
    return prior, estimate


def integer(row, field, default=None):
    value = row.get(field, default)
    require(type(value) is int and value >= 0, 'U1_INVALID_JOURNAL_NUMBER')
    return value


def current(service):
    path = DEEPSEEK_JOURNAL if service == 'deepseek' else EMBEDDING_JOURNAL
    attempts, headers, usage = {}, {}, {}
    for row in records(path):
        number = integer(row, 'attempt')
        event = row['event']
        if event == 'attempt':
            require(number == len(attempts) + 1 and isinstance(row.get('operation'), str), 'U1_ATTEMPT_SEQUENCE')
            attempts[number] = row
        elif event == 'headers':
            require(number in attempts and number not in headers, 'U1_HEADERS_SEQUENCE')
            headers[number] = row
        elif event in ('usage', 'success'):
            require(number in headers and number not in usage, 'U1_USAGE_SEQUENCE')
            usage[number] = row
        else:
            raise SystemExit('U1_UNKNOWN_JOURNAL_EVENT')
    estimate = Decimal(0)
    fully_accounted = True
    by_operation = Counter()
    for number, attempt in attempts.items():
        by_operation[attempt['operation']] += 1
        used = usage.get(number)
        if service == 'deepseek':
            max_input = min(128_000, integer(attempt, 'bodyUtf8Bytes', 48000))
            max_output = integer(attempt, 'maxOutputTokens', 4096)
            fields = ('prompt_tokens', 'completion_tokens', 'total_tokens', 'prompt_cache_hit_tokens', 'prompt_cache_miss_tokens')
            if used and all(type(used.get(key)) is int and used[key] >= 0 for key in fields):
                prompt, output, total, cached, uncached = (integer(used, key) for key in fields)
                require(cached + uncached == prompt and prompt + output == total, 'U1_DEEPSEEK_USAGE_TOTALS')
                estimate += (CACHE_RATE * cached + INPUT_RATE * uncached + OUTPUT_RATE * output) / MILLION
            else:
                fully_accounted = False
                estimate += (INPUT_RATE * max_input + OUTPUT_RATE * max_output) / MILLION
        else:
            reserved = integer(attempt, 'reservedTokens', 128_000)
            measured = used and used.get('usageTokens')
            if type(measured) is int and 0 < measured <= reserved:
                estimate += EMBEDDING_RATE * measured / MILLION
            else:
                fully_accounted = False
                estimate += EMBEDDING_RATE * reserved / MILLION
    return len(attempts), estimate, dict(by_operation), fully_accounted


def check_budget(reserving_deepseek, reserving_embedding, *, deepseek_request_bytes=48000,
                 deepseek_max_output=4096, embedding_input_reservation=128000):
    """Return measured or conservatively reserved spend. This function makes no network requests."""
    prior, prior_estimate = previous()
    latest = {service: current(service) for service in U1_LIMIT}
    requests = {'deepseek': reserving_deepseek, 'embedding': reserving_embedding}
    for service, count in requests.items():
        require(type(count) is int and 0 <= count <= U1_LIMIT[service], 'U1_INVALID_RESERVATION')
        future_u1 = latest[service][0] + count
        require(future_u1 <= U1_LIMIT[service] and prior[service] + future_u1 <= CUMULATIVE_LIMIT[service], 'U1_ATTEMPT_CAP')
    reservation = (Decimal(reserving_deepseek) * (INPUT_RATE * deepseek_request_bytes + OUTPUT_RATE * deepseek_max_output)
                   + Decimal(reserving_embedding) * EMBEDDING_RATE * embedding_input_reservation) / MILLION
    u1_estimate = sum(value[1] for value in latest.values())
    require(prior_estimate + u1_estimate + reservation <= 20, 'U1_COST_CAP')
    return {
        'priorAttempts': prior,
        'u1Attempts': {service: item[0] for service, item in latest.items()},
        'u1AttemptsByOperation': {service: item[2] for service, item in latest.items()},
        'u1UsageComplete': {service: item[3] for service, item in latest.items()},
        'reservationCny': str(reservation),
        'priorPeakEstimateCny': str(prior_estimate),
        'u1PeakEstimateOrReservationCny': str(u1_estimate),
        'cumulativeBeforeReservationCny': str(prior_estimate + u1_estimate),
        'vendorBillReconciled': False,
    }
