"""Unauthenticated reachability check, never a Jev inference quality test."""
import json
import time
from datetime import datetime, timezone
from pathlib import Path
import requests

result = dict(timestamp=datetime.now(timezone.utc).isoformat(), endpoint='https://api.typesafe.ai/v1/systemone',
              credentialSent=False, inferenceVerified=False)
start=time.perf_counter()
try:
    response=requests.post(result['endpoint'], json=dict(model='jev-latest', state='non-sensitive connectivity probe',
                           questions={'probe':dict(type='choice', instructions='Is the text about connectivity?',
                           criteria={'yes':'about connectivity','no':'other'})}), timeout=(3,10))
    result['httpStatus']=response.status_code
    result['status']='REACHABLE_AUTH_REQUIRED' if response.status_code==401 else 'REACHABLE_NOT_AUTHENTICATED'
except requests.RequestException as failure:
    result['status']='NETWORK_ERROR'
    result['errorClass']=type(failure).__name__
result['elapsedMs']=round((time.perf_counter()-start)*1000)
out=Path(__file__).resolve().parents[1]/'evidence/jev-access.json'
out.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False))
