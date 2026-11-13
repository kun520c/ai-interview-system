"""Read the original D3-A cells and export the catalog, coverage and source index."""
import hashlib
import json
import re
import sys
from pathlib import Path

from openpyxl import load_workbook

WORKBOOK = Path('docs/rag/d3-a/D3A_KNOWLEDGE_COVERAGE_20261114.xlsx')


def rows(book, sheet, identity):
    source = book[sheet].iter_rows(values_only=True)
    headers = next(source)
    return [dict(zip(headers, row)) for row in source if re.fullmatch(identity, str(row[0]))]


def export():
    workbook = load_workbook(WORKBOOK, read_only=True, data_only=True)
    try:
        catalog = rows(workbook, '建设目录', r'[JSD][0-9]{2}')
        points = rows(workbook, '评分点矩阵', r'[EMH][0-9]{2}\.[0-9]{2}')
        sources = rows(workbook, '来源索引', r'A[0-9]{3}')
    finally:
        workbook.close()
    for records, key, expected in [(catalog, '文档 ID', 51), (points, '评分点', 298),
                                   (sources, '来源 ID', 449)]:
        if len(records) != expected or len({row[key] for row in records}) != expected:
            raise ValueError(f'Unexpected worksheet rows: {key}')
    doc_ids = {row['文档 ID'] for row in catalog}
    source_ids = {row['来源 ID'] for row in sources}
    for row in points:
        if not set(re.findall(r'[JSD]\d{2}', row['计划文档'])) <= doc_ids:
            raise ValueError(f'Unknown document for {row["评分点"]}')
        if not set(re.findall(r'A\d{3}', row['权威来源 ID'])) <= source_ids:
            raise ValueError(f'Unknown source for {row["评分点"]}')
    result = dict(workbook=str(WORKBOOK), sha256=hashlib.sha256(WORKBOOK.read_bytes()).hexdigest(),
                  catalog=catalog, points=points, sources=sources)
    with Path(sys.argv[1]).open('x', encoding='utf-8') as target:
        target.write(json.dumps(result, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    export()
