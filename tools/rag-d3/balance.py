"""Print DeepSeek account availability without exporting account details or invoking a model."""
from decimal import Decimal
from pathlib import Path
import json
import urllib.error
import urllib.request

from config import protected_config


def check(required_cny=Decimal(20)):
    config = protected_config(Path.home() / '.config/ai-interview/evaluation-deepseek.env')
    endpoint = config.get('DEEPSEEK_BASE_URL', '').rstrip('/')
    if endpoint != 'https://api.deepseek.com' or config.get('DEEPSEEK_MODEL') != 'deepseek-flash':
        raise SystemExit('Unexpected DeepSeek configuration')
    request = urllib.request.Request(endpoint + '/user/balance', headers={
        'Authorization': 'Bearer ' + config['DEEPSEEK_API_KEY'], 'Accept': 'application/json'
    })
    try:
        with urllib.request.urlopen(request, timeout=12) as response:
            body = json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit(f'Balance query HTTP {error.code}') from None
    except (OSError, ValueError) as error:
        raise SystemExit(f'Balance query failed: {type(error).__name__}') from None
    return dict(available=body['is_available'], accounts=[
        dict(currency=row['currency'], sufficientForReservation=(Decimal(row['total_balance']) >= required_cny)
             if row['currency'] == 'CNY' else None) for row in body['balance_infos']
    ])


def main():
    result = check()
    for row in result['accounts']:
        row['sufficientFor20Cny'] = row.pop('sufficientForReservation')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
