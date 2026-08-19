# 연합뉴스 수집 정확도 테스트

운영 Scheduler, DB, OpenAI 없이 실제 연합뉴스 RSS를 고정 시간 범위로 관찰하는 수동 테스트다.

```bash
YONHAP_LIVE_TEST=true ./gradlew test \
  --tests com.economicbriefing.collector.source.YonhapCollectionAccuracyTest
```

IDE에서 `capturesLiveYonhapCollectionFunnel`을 실행할 때도 환경변수
`YONHAP_LIVE_TEST=true`를 지정한다. 결과는 Git에서 제외된
`pipeline-debug/yonhap-20260814-1900/`에 생성된다.

## 고정 범위와 현재 경계조건

```text
start = 2026-08-14T19:00:00+09:00
end   = 2026-08-14T20:00:00+09:00
```

현재 `parseItem`과 `DateFilter`는 시작과 끝을 모두 포함한다.
즉 실제 조건은 `start <= publishedAt <= end`이며, 정확히 20:00:00인 기사도 포함된다.
테스트는 이 동작을 변경하지 않고 `summary.json`에 명시한다.

`YonhapSourceAdapter.parseItem`이 먼저 같은 시간 검사를 하므로, 그 뒤 실행되는
`DateFilter`의 입력에는 이미 범위 밖 기사가 없다. 범위 밖 RSS 항목은
`02-articles.json`의 `BEFORE_START`/`AFTER_END`에서 확인한다.

## 출력

```text
pipeline-debug/yonhap-20260814-1900/
├── 01-rss-items.json
├── 02-articles.json
├── 03-date-filter.json
├── 04-quality-filter.json
├── 05-deduplication.json
├── 06-final-articles.json
└── summary.json
```

`QualityValidator`의 현재 제거 조건은 제목 5자 미만, HTTP(S)가 아닌 URL,
발행일 누락, 제목의 광고/제휴/이벤트/홍보/협찬 키워드다.

## Ground Truth 권장 형식

실제 정답은 만들지 않는다. 사람이 확인한 뒤 다음 구조로 별도 작성하면 이후
`url`을 기본 키로 자동 비교하기 쉽다.

```json
{
  "range": {
    "start": "2026-08-14T19:00:00+09:00",
    "end": "2026-08-14T20:00:00+09:00"
  },
  "articles": [
    {
      "feed": "economy",
      "title": "사람이 확인한 제목",
      "url": "https://www.yna.co.kr/view/...",
      "publishedAt": "2026-08-14T19:10:00+09:00",
      "expected": true,
      "eventGroup": null,
      "isDuplicate": false,
      "representativeUrl": null
    }
  ]
}
```

## Fixture 회귀 테스트 후속 작업

현재 `RssParser`는 URL을 직접 HTTP 호출하므로 로컬 XML fixture를 받을 수 없다.
fixture가 준비되면 `parse(InputStream)` 하나를 추출하고 기존 `parse(String)`이 이를
호출하게 만드는 최소 변경이면 충분하다. fixture가 없는 현재는 구현하지 않는다.
