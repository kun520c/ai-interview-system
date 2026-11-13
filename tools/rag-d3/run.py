"""Launch the guarded evaluation runner with explicitly selected credentials."""
import fcntl
import os
from pathlib import Path
import subprocess
import sys

from config import protected_config

WORK = Path('backups/D3_BCD_20261114')
MODEL_CONFIG = Path.home() / '.config/ai-interview/evaluation-embedding.env'
EMBEDDING_KEYS = ('EMBEDDING_BASE_URL', 'EMBEDDING_API_KEY', 'EMBEDDING_MODEL',
                  'EMBEDDING_DIMENSION', 'EMBEDDING_PROFILE_VERSION')
JDK_OPTIONS = ('-Djdk.httpclient.redirects.retrylimit=1', '-Djdk.httpclient.disableRetryConnect=true',
               '-Djdk.httpclient.enableAllMethodRetry=false')


def evaluation_environment(paid):
    local = protected_config('.env')
    env = {key: os.environ[key] for key in ('PATH', 'HOME', 'JAVA_HOME', 'LANG', 'TMPDIR') if key in os.environ}
    env['EVALUATION_DB_USERNAME'] = 'root'
    env['EVALUATION_DB_PASSWORD'] = local['AI_MYSQL_ROOT_PASSWORD']
    if paid:
        embedding = protected_config(MODEL_CONFIG)
        if not embedding.get('EMBEDDING_API_KEY') or 'placeholder' in embedding['EMBEDDING_API_KEY'].lower():
            raise ValueError('Missing real evaluation Embedding credential')
        env.update({key: embedding[key] for key in EMBEDDING_KEYS})
    return env


def runtime_classpath():
    return 'target/classes:' + (WORK / 'classpath.txt').read_text().strip()


def main():
    os.umask(0o077)
    if len(sys.argv) < 2 or sys.argv[1] not in {'preflight', 'ingest', 'retrieve'}:
        raise SystemExit('Usage: run.py preflight <snapshot.json> | ingest | retrieve <set> [path sha256]')
    mode, *args = sys.argv[1:]
    if mode == 'preflight' and (len(args) != 1 or Path(args[0]).name != args[0] or not args[0].endswith('.json')):
        raise SystemExit('Use a snapshot basename ending in .json')
    if WORK.is_symlink() or not WORK.is_dir() or WORK.stat().st_mode & 0o077:
        raise SystemExit('Missing owner-only evaluation work directory')
    class_dir = WORK / 'classes'
    class_dir.mkdir(exist_ok=True)
    cp = runtime_classpath()
    with (WORK / 'execution.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        subprocess.run(['javac', '-proc:none', '-cp', cp, '-d', str(class_dir),
                        *map(str, sorted(Path('tools/rag-d3').glob('D3*.java')))], check=True)
        env = evaluation_environment(paid=mode != 'preflight')
        return subprocess.call(['java', *JDK_OPTIONS, '-cp', f'{class_dir}:{cp}', 'D3Live', mode, str(WORK), *args], env=env)


if __name__ == '__main__':
    raise SystemExit(main())
