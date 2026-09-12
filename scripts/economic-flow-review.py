#!/usr/bin/env python3
"""Replay one stored article in a disposable PostgreSQL copy; keep model outputs for human review."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def run(args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--article-id', required=True)
    parser.add_argument('--date', required=True)
    parser.add_argument('--source-db', default='economic_briefing')
    parser.add_argument('--review', required=True, help='new review directory name; prior runs are never overwritten')
    parser.add_argument('--cache-from', type=Path, help='reuse frozen article, corpus and matching API responses from a previous review')
    parser.add_argument('--article-file', type=Path, help='frozen article JSON for an article not yet in the source database')
    parser.add_argument('--selected-by-user', action='store_true', help='test a user-selected article without the daily article-selection gate')
    args = parser.parse_args()
    if args.article_file and args.cache_from:
        parser.error('--article-file and --cache-from cannot be combined')
    if not re.fullmatch(r'[a-z0-9_]+', args.review):
        parser.error('--review must use lowercase letters, numbers, underscores')
    dt.date.fromisoformat(args.date)
    directory = ROOT / 'pipeline-debug' / 'beginner-memory' / args.review
    directory.mkdir(parents=True, exist_ok=False)
    db = 'economic_briefing_review_' + args.review
    if len(db) > 63:
        parser.error('review name is too long')
    # psql variables quote the article identifier as SQL data, never as SQL syntax.
    fixture = args.article_file or (args.cache_from / 'article.json' if args.cache_from else None)
    article = fixture.read_text() if fixture else run(
        ['psql', '-X', '-At', '-d', args.source_db, '-v', 'article_id=' + args.article_id],
        input="SELECT row_to_json(a) FROM articles a WHERE id = :'article_id';\n", capture_output=True).stdout.strip()
    if not article:
        raise SystemExit('article is not in the source database')
    source = json.loads(article)
    if source['id'] != args.article_id:
        raise SystemExit('frozen article does not match --article-id')
    if args.cache_from and json.loads((args.cache_from / 'result.json').read_text())['targetDate'] != args.date:
        raise SystemExit('cached target date does not match --date')
    if source.get('body_status') != 'FULL_TEXT' or not source.get('body'):
        raise SystemExit('freeze the article body before replaying')
    (directory / 'article.json').write_text(json.dumps(source, ensure_ascii=False, indent=2))
    if args.cache_from:
        shutil.copyfile(args.cache_from / 'corpus.sql', directory / 'corpus.sql')
    else:
        run(['pg_dump', '-d', args.source_db, '--no-owner', '--no-privileges', '--table=articles',
             '--table=article_observations', '--table=economic_principle_chunk', '-f', str(directory / 'corpus.sql')])
    run(['createdb', db])
    run(['psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-d', db, '-c', 'CREATE EXTENSION IF NOT EXISTS vector;'], stdout=subprocess.DEVNULL)
    run(['psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-d', db, '-f', str(directory / 'corpus.sql')], stdout=subprocess.DEVNULL)
    has_v26 = run(['psql', '-X', '-At', '-d', db, '-c', "SELECT count(*) FROM information_schema.columns WHERE table_name='article_observations' AND column_name='memory_eligible'"], capture_output=True).stdout.strip()
    if has_v26 == '0':
        run(['psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-d', db, '-f', str(ROOT / 'src/main/resources/db/migration/V26__add_curated_article_memory.sql')], stdout=subprocess.DEVNULL)
    # The fixture is the selected article's source of truth, including when the corpus predates its collection.
    run(['psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-d', db, '-v', 'article_json=' + json.dumps(source)],
        input="""INSERT INTO articles SELECT * FROM json_populate_record(NULL::articles, :'article_json'::json)
                 ON CONFLICT (id) DO UPDATE SET title=EXCLUDED.title, summary=EXCLUDED.summary,
                 body=EXCLUDED.body, body_status=EXCLUDED.body_status, url=EXCLUDED.url,
                 published_at=EXCLUDED.published_at, body_fetched_at=EXCLUDED.body_fetched_at;\n""",
        stdout=subprocess.DEVNULL)
    if args.cache_from:
        shutil.copytree(args.cache_from / 'cache', directory / 'cache')
    environment = dict(os.environ)
    for line in (ROOT / '.env').read_text().splitlines():
        if line.strip() and not line.lstrip().startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            if key.strip() == 'OPENAI_API_KEY':
                environment.setdefault(key.strip(), value.strip().strip('\"\''))
    environment.update(BRIEFING_REVIEW_DB=db, BRIEFING_REVIEW_DIR=str(directory), BRIEFING_REVIEW_DATE=args.date,
                       BRIEFING_REVIEW_SELECTED=str(args.selected_by_user).lower())
    print(f'Review: {directory}\nIsolated database: {db}', flush=True)
    process = subprocess.run(['./gradlew', 'test', '--tests', '*BeginnerMemoryReplayTest', '--rerun-tasks', '--console=plain'],
                             cwd=ROOT, env=environment)
    if (directory / 'result.json').exists():
        report(directory)
    raise SystemExit(process.returncode)


def report(directory):
    data = json.loads((directory / 'result.json').read_text())
    article = json.loads((directory / 'article.json').read_text())
    lines = ['# 기사 한 편 사용자 평가', '', f"[{article['title']}]({article['url']})", '',
             f"기사 발행: {article['published_at']} · 분석 기준일: {data['targetDate']}", '',
             '저장 원문을 고정한 단일 기사 실험이며, 해당 날짜 전체 기사나 기존 공개 결과의 재현이 아닙니다.', '',
             f"실행: {data['status']} · 이번 새 호출 비용: ${data['usage']['estimatedCostUsd']:.6f}", '',
             'SUCCESS는 구조·출처 ID·수치 검증 통과를 뜻해요. 설명의 정확성·이해 가능성과 기억의 필요성은 평가 전인 모델 초안이에요.', '',
             '## 모델의 기억 판단', '']
    if data['trace'].get('selection', {}).get('mode') == 'USER_SELECTED_FOR_REVIEW':
        lines[8:8] = ['기사 선정은 사용자가 지정했으므로 일일 기사 선별을 건너뛰었어요. 기억·질문·답변은 기존 지침으로 LLM이 생성했어요.', '']
    for decision in data['trace'].get('memoryDecisions', []):
        for i, observation in enumerate(decision['raw'].get('observations', []), 1):
            accepted = any(' '.join(o['text'].split()) == ' '.join(observation['text'].split()) for o in decision['accepted'])
            choice = ('장기 기억' if observation['remember'] else '오늘 관측만') if accepted else '코드 검증 탈락'
            lines += [f"### M{i:02d} · {choice}", '', observation['text'], '',
                      '모델 선정 이유: ' + observation['memoryReason'], '', '근거: ' + ', '.join(observation['spanIds']), '']
    lines += ['## 모델의 초보자 질문', '']
    for f, flow in enumerate(data['trace'].get('plan', []), 1):
        for q, question in enumerate(flow.get('questions', []), 1):
            lines += [f"- F{f:02d}:Q{q:02d} **{question['question']}** — {question['reason']}"]
    for flow in data.get('result', {}).get('flows', []):
        lines += ['', '## 생성한 본문', '', flow['explanation'], '', '## 질문·답변', '']
        for question in flow.get('questions', []):
            lines += ['### ' + question['question'], '', question['answer'], '']
    lines += ['', '## 사용자 피드백', '', '궁금한데 빠진 질문, 불필요한 질문, 꼭 기억할 정보, 사소한 정보를 알려주세요.',
              '원문에서 추출되지 않은 부분도 문단 번호로 지목할 수 있어요. 사용자 판단은 아직 기록되지 않았습니다.', '',
              '## 고정 원문', '']
    for i, paragraph in enumerate(filter(None, article['body'].splitlines()), 1):
        lines += [f'**P{i:03d}** {paragraph}', '']
    (directory / 'review.md').write_text('\n'.join(lines))
    feedback = directory / 'feedback.json'
    if not feedback.exists():
        feedback.write_text(json.dumps({'status': 'awaiting_user', 'questionFeedback': [], 'memoryFeedback': []}, indent=2))
    print(f"Report: {directory / 'review.md'}", flush=True)


if __name__ == '__main__':
    main()
