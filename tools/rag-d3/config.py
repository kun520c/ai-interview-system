"""Read protected, literal environment assignments without evaluating shell code."""
import os
from pathlib import Path
import re
import shlex
import stat


EVALUATION_DB_PATH = Path.home() / '.config/ai-interview/evaluation-db.env'
EVALUATION_DB_BASE_URL = 'jdbc:mysql://127.0.0.1:3308/ai_interview_evaluation'
EVALUATION_DB_URL = (EVALUATION_DB_BASE_URL
                     + '?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=20000')
EVALUATION_DB_KEYS = frozenset(('EVALUATION_DB_URL', 'EVALUATION_DB_USERNAME', 'EVALUATION_DB_PASSWORD'))


def protected_config(path, *, allowed_keys=None):
    path = Path(path)
    stat = path.stat()
    if path.is_symlink() or not path.is_file() or stat.st_mode & 0o077 or stat.st_uid != os.getuid():
        raise ValueError('Protected configuration must be a regular owner-only file')
    values = {}
    with path.open(encoding='utf-8') as source:
        for line in source:
            line = line.strip().removeprefix('export ')
            if not line or line.startswith('#'):
                continue
            key, equals, raw = line.partition('=')
            if (not equals or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key) or key in values
                    or allowed_keys is not None and key not in allowed_keys):
                raise ValueError('Invalid or unexpected environment assignment')
            parts = shlex.split(raw, comments=True, posix=True)
            if len(parts) > 1 or parts and any(value in parts[0] for value in ('$(', '${', '`')):
                raise ValueError('Expected a literal assignment')
            values[key] = parts[0] if parts else ''
    return values


def evaluation_database_config(path=EVALUATION_DB_PATH):
    """Load only evaluation DB fields; never fall back to project or process credentials."""
    path = Path(path)
    directory = path.parent
    if (directory.is_symlink() or not directory.is_dir() or directory.stat().st_uid != os.getuid()
            or stat.S_IMODE(directory.stat().st_mode) != 0o700):
        raise ValueError('Evaluation configuration directory must be owner-only (0700)')
    if path.is_symlink() or not path.is_file() or stat.S_IMODE(path.stat().st_mode) != 0o600:
        raise ValueError('Evaluation database configuration must be an owner-only file (0600)')
    values = protected_config(path, allowed_keys=EVALUATION_DB_KEYS)
    username = values.get('EVALUATION_DB_USERNAME', '')
    if not username.strip() or not values.get('EVALUATION_DB_PASSWORD'):
        raise ValueError('Evaluation database credentials are missing')
    if username.strip().lower() == 'root':
        raise ValueError('Evaluation database configuration must use a dedicated non-root account')
    if (values.get('EVALUATION_DB_URL') or EVALUATION_DB_URL) not in (EVALUATION_DB_BASE_URL, EVALUATION_DB_URL):
        raise ValueError('Unexpected evaluation database target')
    return {
        'EVALUATION_DB_URL': EVALUATION_DB_URL,
        'EVALUATION_DB_USERNAME': values['EVALUATION_DB_USERNAME'],
        'EVALUATION_DB_PASSWORD': values['EVALUATION_DB_PASSWORD'],
    }
