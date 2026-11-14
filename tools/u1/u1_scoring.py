"""Seal or run the U1 synthetic scoring comparison. Run from the repository root."""
from decimal import Decimal
from hashlib import sha256
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile

from u1_budget import D3BCD, RUN, check_budget, execution_lock, is_owner_only, require

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'rag-d3'))
from balance import check as check_balance
from config import protected_config
from run import JDK_OPTIONS


SCORING = RUN / 'scoring'
SOURCE = Path('src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java')
OLD_COMMIT = '921f8cdb24bfda50e4e9a4d76d4e1017449fe032'
OLD_SOURCE_SHA = 'a5a4350b68ca6ffb56b3beaf084f6e47d52fcd033b86cc5a32579c9177dbfd11'
CONFIG = Path.home() / '.config/ai-interview/evaluation-deepseek.env'
MAX_ATTEMPTS = 16


def compile_probe(build, env):
    java_home = subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True, env=env).strip()
    cp = 'target/classes:' + (D3BCD / 'classpath.txt').read_text().strip()
    original = subprocess.check_output(['git', 'show', f'{OLD_COMMIT}:{SOURCE}'], env=env)
    require(sha256(original).hexdigest() == OLD_SOURCE_SHA, 'U1_OLD_PROMPT_SHA')
    historical = build / 'U1HistoricalPromptBuilder.java'
    old_java = original.replace(b'public class EvaluationPromptBuilder', b'public class U1HistoricalPromptBuilder', 1)
    historical.write_bytes(old_java)
    candidate_java = SOURCE.read_bytes()
    candidate = build / 'EvaluationPromptBuilder.java'
    candidate.write_bytes(candidate_java)
    supporting = ['D3Corpus.java', 'D3Inventory.java', 'D3PaidEmbedding.java', 'D3SmokeServer.java']
    main_sources = [historical, candidate, Path('tools/u1/U1ScoringProbe.java')]
    subprocess.run([java_home + '/bin/javac', '-proc:none', '-cp', cp, '-d', str(build),
                    *map(str, main_sources), *(str(Path('tools/rag-d3') / name) for name in supporting)],
                   check=True, env=env)
    command = [java_home + '/bin/java', *JDK_OPTIONS, '-cp', f'{build}:{cp}', 'U1ScoringProbe']
    return command, (OLD_SOURCE_SHA, sha256(candidate_java).hexdigest())


def main():
    if sys.argv[1:] not in (['prepare'], ['verify'], ['execute']):
        raise SystemExit('Usage: python3 -B tools/u1/u1_scoring.py prepare|verify|execute')
    mode = sys.argv[1]
    env = {key: os.environ[key] for key in ('PATH', 'HOME', 'LANG', 'TMPDIR') if key in os.environ}
    with execution_lock(), tempfile.TemporaryDirectory(prefix='u1-scoring-') as tmp:
        if mode == 'prepare':
            SCORING.mkdir(mode=0o700, exist_ok=True)
        require(is_owner_only(SCORING, stat.S_ISDIR), 'U1_PRIVATE_SCORING_DIRECTORY')
        command, identities = compile_probe(Path(tmp), env)
        args = [*command, mode, str(SCORING), *identities]
        if mode != 'prepare':
            frozen = SCORING / 'frozen-plan.json'
            require(is_owner_only(frozen, stat.S_ISREG), 'U1_PRIVATE_FROZEN_PLAN')
            plan_sha = sha256(frozen.read_bytes()).hexdigest()
            args.append(plan_sha)
        if mode == 'execute':
            marker = SCORING / 'started.json'
            require(not marker.exists() and not any(SCORING.glob('http-*.json')), 'U1_COMPARISON_ALREADY_STARTED')
            budget = check_budget(MAX_ATTEMPTS, 0)
            prior = budget['u1Attempts']['deepseek']
            require(not budget['u1AttemptsByOperation']['deepseek'].get('scoring-comparison'), 'U1_NO_SCORING_RETRY')
            subprocess.run([*command, 'verify', str(SCORING), *identities, plan_sha], check=True, env=env)
            require(os.environ.get('U1_WINDOW_CONFIRMED') == 'true', 'U1_WINDOW_REQUIRED')
            reserved = Decimal(budget['reservationCny'])
            require(Decimal(os.environ.get('U1_VERIFIED_RESERVATION_CNY', '0')) == reserved, 'U1_PRICE_RESERVATION_REQUIRED')
            private_settings = protected_config(CONFIG)
            require(private_settings.get('DEEPSEEK_BASE_URL', '').rstrip('/') == 'https://api.deepseek.com'
                    and private_settings.get('DEEPSEEK_MODEL') == 'deepseek-flash'
                    and bool(private_settings.get('DEEPSEEK_API_KEY')), 'U1_PRIVATE_MODEL_CONFIGURATION')
            available = check_balance(reserved)
            require(available['available'] and any(r['sufficientForReservation'] for r in available['accounts']), 'U1_CNY_BALANCE')
            with marker.open('x', encoding='utf-8') as sink:
                json.dump({**budget, 'model': 'deepseek-flash', 'freeBalanceRequests': 1, 'available': True,
                           'planSha256': plan_sha, 'maxModelAttempts': MAX_ATTEMPTS}, sink, indent=2)
            env.update(DEEPSEEK_API_KEY=private_settings['DEEPSEEK_API_KEY'], U1_SCORING_ARMED='true')
            args.append(str(prior))
        return subprocess.call(args, env=env)


if __name__ == '__main__':
    raise SystemExit(main())
