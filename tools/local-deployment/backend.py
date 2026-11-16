#!/usr/bin/env python3
"""Start the existing local demo candidate; never read an evaluation DB or process environment."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import socket
import subprocess
import sys
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rag-d3"))
from config import protected_config  # noqa: E402

JAR = ROOT / "backups/MQ_CANDIDATE_20261115_soe3tev4/candidate-0509cd6.jar"
JAR_SHA = "3346146f49fca7c7c604b2d70c3ebddc134d7d49a97467a07d80fe35c2fa07e0"
RELAY_TOKEN = ROOT / "backups/DEMO_ENV_20261115_9lpoxk7u/relay-token-next.private"
RELAY_STATE = ROOT / "backups/DEMO_ENV_20261115_9lpoxk7u/demo-runtime-rearmed.json"
RELAY_SCRIPT = ROOT / "backups/DEMO_ENV_20261115_9lpoxk7u/model-usage-relay-next.py"
PROVIDER_KEYS = frozenset({"DEEPSEEK_BASE_URL", "DEEPSEEK_API_KEY", "EMBEDDING_BASE_URL",
                           "EMBEDDING_API_KEY", "EMBEDDING_MODEL", "EMBEDDING_DIMENSION",
                           "EMBEDDING_PROFILE_VERSION"})


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def require_protected(path):
    stat = path.stat()
    if path.is_symlink() or not path.is_file() or stat.st_uid != os.getuid() or stat.st_mode & 0o077:
        raise ValueError("private file must be a regular owner-only file: " + str(path))


def provider_config(path):
    if path:
        # This is a separate, explicitly chosen local provider-only configuration.
        if "evaluation" in str(path).lower():
            raise ValueError("choose a separate local provider configuration")
        result = protected_config(path, allowed_keys=PROVIDER_KEYS)
    else:
        require_protected(RELAY_TOKEN)
        require_protected(RELAY_STATE)
        pid = int(json.loads(RELAY_STATE.read_text(encoding="utf-8"))["relay_pid"])
        listeners = subprocess.run(["lsof", "-nP", "-t", "-iTCP:28891", "-sTCP:LISTEN"],
                                   text=True, capture_output=True, check=True, timeout=5).stdout.split()
        command = subprocess.run(["/bin/ps", "-p", str(pid), "-o", "command="],
                                 text=True, capture_output=True, check=True, timeout=5).stdout
        if listeners != [str(pid)] or str(RELAY_SCRIPT) not in shlex.split(command):
            raise ValueError("local relay process identity does not match the recorded demo")
        token = RELAY_TOKEN.read_text(encoding="utf-8").strip()
        with socket.create_connection(("127.0.0.1", 28891), timeout=2):
            pass
        result = {"DEEPSEEK_BASE_URL": "http://127.0.0.1:28891/deepseek",
                  "DEEPSEEK_API_KEY": token,
                  "EMBEDDING_BASE_URL": "http://127.0.0.1:28891/embedding",
                  "EMBEDDING_API_KEY": token}
    for prefix in ("DEEPSEEK", "EMBEDDING"):
        url = urlsplit(result.get(prefix + "_BASE_URL", ""))
        local_relay = ((url.scheme, url.hostname, url.port) == ("http", "127.0.0.1", 28891)
                       and url.path.rstrip("/") == ("/deepseek" if prefix == "DEEPSEEK" else "/embedding"))
        remote_provider = (url.scheme == "https" and
                           (url.hostname == "api.deepseek.com" if prefix == "DEEPSEEK"
                            else bool(url.hostname and url.hostname.endswith(".aliyuncs.com"))))
        if not (local_relay or remote_provider) or url.username or url.password or url.query or url.fragment:
            raise ValueError("unexpected " + prefix + " endpoint")
        if not result.get(prefix + "_API_KEY"):
            raise ValueError("missing " + prefix + " API key")
    result.setdefault("EMBEDDING_MODEL", "qwen3.7-text-embedding")
    result.setdefault("EMBEDDING_DIMENSION", "1024")
    result.setdefault("EMBEDDING_PROFILE_VERSION", "qwen3.7-text-embedding-1024-dense-v1")
    if (result["EMBEDDING_MODEL"] != "qwen3.7-text-embedding" or result["EMBEDDING_DIMENSION"] != "1024"
            or result["EMBEDDING_PROFILE_VERSION"] != "qwen3.7-text-embedding-1024-dense-v1"):
        raise ValueError("unexpected embedding model, dimension or profile")
    return result


def database_preflight():
    query = ("SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()),"
             "(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
             "AND table_name='interview_report_task'),"
             "(SELECT COUNT(*) FROM interview_report_task WHERE task_state IN ('QUEUED','RUNNING'));")
    command = ["docker", "exec", "-i", "ai-interview-mac-mysql-1", "sh", "-c",
               'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" -D ai_interview -N -B']
    response = subprocess.run(command, input=query, text=True, capture_output=True, check=True, timeout=15)
    tables, columns, active = map(int, response.stdout.strip().split("\t"))
    if (tables, columns) != (14, 17):
        raise ValueError("unexpected local schema; review it before starting")
    return active


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate configuration without starting")
    parser.add_argument("--allow-pending", action="store_true", help="start after reviewing active local tasks")
    parser.add_argument("--model-config", type=Path, help="owner-only, local provider-only environment file")
    args = parser.parse_args()
    local = protected_config(ROOT / ".env")
    db = urlsplit(local.get("DB_URL", "").removeprefix("jdbc:"))
    if (db.scheme, db.hostname, db.port, db.path) != ("mysql", "127.0.0.1", 3308, "/ai_interview"):
        raise ValueError("refusing a database other than 127.0.0.1:3308/ai_interview")
    if not local.get("DB_USERNAME") or local["DB_USERNAME"].lower() == "root" or not local.get("DB_PASSWORD") or not local.get("JWT_SECRET"):
        raise ValueError("local non-root DB and JWT configuration is incomplete")
    if sha256(JAR) != JAR_SHA:
        raise ValueError("candidate JAR SHA-256 mismatch")
    provider = provider_config(args.model_config)
    active = database_preflight()
    print("candidate=0509cd6 jar_sha256=" + JAR_SHA + " target=127.0.0.1:3308/ai_interview active_tasks=" + str(active), flush=True)
    if active and not args.allow_pending:
        raise ValueError("active tasks need review; use --allow-pending only after reviewing recovery")
    if args.check:
        return
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 8081))
    java = shutil.which("java")
    if not java:
        raise ValueError("Java not found")
    runtime_root = ROOT / "backups/LOCAL_RUNTIME_20261116"
    runtime_root.mkdir(mode=0o700, exist_ok=True)
    if runtime_root.is_symlink() or runtime_root.stat().st_uid != os.getuid() or runtime_root.stat().st_mode & 0o077:
        raise ValueError("runtime directory must be owner-only")
    runtime_home = runtime_root / "java-home"
    runtime_home.mkdir(mode=0o700, exist_ok=True)
    if runtime_home.is_symlink() or runtime_home.stat().st_uid != os.getuid() or runtime_home.stat().st_mode & 0o077:
        raise ValueError("Java home directory must be owner-only")
    env = {key: os.environ[key] for key in ("PATH", "HOME", "TMPDIR", "LANG", "LC_ALL", "LC_CTYPE") if key in os.environ}
    env.update({key: local[key] for key in ("DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET")})
    env.update(provider)
    env.update({"SPRING_PROFILES_ACTIVE": "local", "SERVER_ADDRESS": "127.0.0.1", "SERVER_PORT": "8081",
                "DEEPSEEK_ENABLED": "true", "DEEPSEEK_MODEL": "deepseek-flash", "MILVUS_ENABLED": "true",
                "MILVUS_URI": "http://127.0.0.1:19532", "MILVUS_DATABASE_NAME": "default",
                "MILVUS_COLLECTION_NAME": "knowledge_chunk_vectors_local", "MILVUS_DIMENSION": "1024",
                "REPORT_ASYNC_ENABLED": "true", "REPORT_ASYNC_ACCEPTING": "true",
                "REPORT_MQ_NAMESERVER": "127.0.0.1:19877", "REPORT_MQ_TOPIC": "ai-interview-local-demo-report",
                "REPORT_MQ_CONSUMER_GROUP": "ai-interview-local-demo-report-consumer"})
    os.execve(java, [java, "-Xms128m", "-Xmx512m", "-Duser.home=" + str(runtime_home), "-jar", str(JAR)], env)


if __name__ == "__main__":
    try:
        main()
    except (KeyError, OSError, TypeError, ValueError, subprocess.SubprocessError) as error:
        print("local backend start refused: " + str(error), file=sys.stderr)
        sys.exit(1)
