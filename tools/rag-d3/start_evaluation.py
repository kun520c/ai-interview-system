"""Launch the application's loopback evaluation instance after a read-only D3 check."""
import base64
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys

from config import protected_config
from run import JDK_OPTIONS, WORK, evaluation_environment, runtime_classpath

BACKEND_PORT = 18482
MYSQL = ('jdbc:mysql://127.0.0.1:3308/ai_interview_evaluation'
         '?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=20000')


def main():
    os.umask(0o077)
    resume = sys.argv[1:] == ['--smoke-recovery']
    smoke = resume or sys.argv[1:] == ['--smoke']
    if sys.argv[1:] not in ([], ['--smoke'], ['--smoke-recovery']):
        raise SystemExit('Usage: start_evaluation.py [--smoke | --smoke-recovery]')
    env = evaluation_environment(paid=True)
    deepseek = protected_config(Path.home() / '.config/ai-interview/evaluation-deepseek.env')
    if (deepseek['DEEPSEEK_BASE_URL'].rstrip('/') != 'https://api.deepseek.com'
            or deepseek['DEEPSEEK_MODEL'] != 'deepseek-flash' or not deepseek['DEEPSEEK_API_KEY']):
        raise SystemExit('Unexpected DeepSeek target')
    env.update({key: deepseek[key] for key in (
        'DEEPSEEK_BASE_URL', 'DEEPSEEK_MODEL', 'DEEPSEEK_API_KEY')})
    env.update(SPRING_PROFILES_ACTIVE='evaluation', EVALUATION_DB_URL=MYSQL, MILVUS_ENABLED='true',
               EVALUATION_MILVUS_URI='http://127.0.0.1:19532', EVALUATION_MILVUS_DATABASE_NAME='default',
               EVALUATION_MILVUS_COLLECTION_NAME='knowledge_chunk_vectors_evaluation',
               JWT_SECRET=base64.b64encode(secrets.token_bytes(48)).decode("ascii"), DEEPSEEK_ENABLED='true', DEEPSEEK_MAX_TOKENS='4096')
    with socket.socket() as port:
        port.bind(('127.0.0.1', BACKEND_PORT))
    base = ['java', *JDK_OPTIONS]
    if resume:
        base.append('-Dd3.smoke.resume=true')
    base.append('-cp')
    options = ['--spring.config.location=classpath:/application.yaml', '--spring.sql.init.mode=never',
               '--server.address=127.0.0.1', f'--server.port={BACKEND_PORT}']
    if smoke:
        main_class = 'D3SmokeServer'
        cp = f'{WORK / "classes"}:{runtime_classpath()}'
    else:
        main_class = 'com.kun.aiinterview.AiInterviewSystemApplication'
        cp = runtime_classpath()
    return subprocess.call([*base, cp, main_class, *options], env=env)


if __name__ == '__main__':
    raise SystemExit(main())
