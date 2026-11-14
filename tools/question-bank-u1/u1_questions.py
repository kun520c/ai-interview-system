"""Run the two-question U1 gate without sourcing or exposing database credentials."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/rag-d3'))
from config import evaluation_database_config  # noqa: E402


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=('validate', 'inspect', 'apply'))
    parser.add_argument('--session', type=int)
    parser.add_argument('--account')
    parser.add_argument('--window-confirmed', action='store_true')
    args = parser.parse_args()
    if args.mode != 'validate' and (not args.session or args.session <= 0 or not args.account):
        parser.error('inspect and apply require a positive --session and an --account')
    if args.mode == 'apply' and not args.window_confirmed:
        parser.error('apply requires a confirmed idle window')
    if args.mode != 'apply' and args.window_confirmed:
        parser.error('--window-confirmed is only valid for apply')

    os.umask(0o077)
    os.chdir(ROOT)
    env = {key: os.environ[key] for key in ('PATH', 'HOME', 'JAVA_HOME', 'LANG', 'TMPDIR') if key in os.environ}
    if args.mode != 'validate':
        env.update(evaluation_database_config())
        env['U1_EVALUATION_ACCOUNT'] = args.account
    if args.mode == 'apply':
        env['U1_WINDOW_CONFIRMED'] = 'true'

    classpath = 'target/classes:' + (ROOT / 'backups/D3_BCD_20261114/classpath.txt').read_text().strip()
    candidate = 'docs/question-bank/U1_QUESTION_CONTENT_CANDIDATES_20261114.json'
    with tempfile.TemporaryDirectory(prefix='u1-question-gate-') as build:
        subprocess.run(['javac', '-proc:none', '-cp', classpath, '-d', build,
                        'tools/question-bank-u1/U1EvaluationQuestions.java'], check=True, env=env)
        command = ['java', '-cp', f'{build}:{classpath}', 'U1EvaluationQuestions', args.mode]
        if args.mode == 'validate':
            command.append(candidate)
        else:
            command.extend(('backups/U1_20261114', str(args.session)))
            if args.mode == 'apply':
                command.append(candidate)
        return subprocess.call(command, env=env)


if __name__ == '__main__':
    raise SystemExit(main())
