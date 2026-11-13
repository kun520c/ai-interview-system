"""Read protected, literal environment assignments without evaluating shell code."""
import os
from pathlib import Path
import re
import shlex


def protected_config(path):
    path = Path(path)
    stat = path.stat()
    if path.is_symlink() or not path.is_file() or stat.st_mode & 0o077 or stat.st_uid != os.getuid():
        raise ValueError('Protected configuration must be a regular owner-only file')
    values = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        line = line.strip().removeprefix('export ')
        if not line or line.startswith('#'):
            continue
        key, equals, raw = line.partition('=')
        if not equals or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key) or key in values:
            raise ValueError('Invalid environment assignment')
        parts = shlex.split(raw, comments=True, posix=True)
        if len(parts) > 1 or parts and any(value in parts[0] for value in ('$(', '${', '`')):
            raise ValueError('Expected a literal assignment')
        values[key] = parts[0] if parts else ''
    return values
