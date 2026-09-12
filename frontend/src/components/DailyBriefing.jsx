import s from './DailyBriefing.module.css'

const dateLabel = (value) => new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', weekday: 'short',
}).format(new Date(`${value}T12:00:00+09:00`))

export default function DailyBriefing({ briefing }) {
  const flows = briefing.flows || []
  return (
    <section className={s.page} aria-labelledby="daily-briefing-title">
      <header className={s.header}>
        <p>🐥 토트의 아침 경제 지도</p>
        <h1 id="daily-briefing-title">{briefing.title || '오늘의 경제흐름'}</h1>
        <span>{dateLabel(briefing.targetDate)} · 오전 5시까지의 뉴스</span>
      </header>

      {flows.length === 0 && <p className={s.empty}>오늘은 하나의 흐름으로 묶을 만한 경제 변화가 확인되지 않았어요.</p>}
      <div className={s.flows}>
        {flows.map((flow, index) => <article className={s.flow} key={flow.id || index}>
          <p className={s.number}>흐름 {index + 1}</p>
          <h2>{flow.title}</h2>
          <p className={s.explanation}>{flow.explanation}</p>

          {(flow.questions || []).map((item, questionIndex) => <section className={s.question} key={item.id || questionIndex}>
            <h3>{item.question}</h3>
            <p className={s.explanation}>{item.answer}</p>
            {(item.sources?.length > 0 || item.principles?.length > 0) && <details className={s.answerSources}>
              <summary>이 설명의 근거 보기</summary>
              {(item.sources || []).map((source, sourceIndex) => <div className={s.source} key={`${source.articleId}-${sourceIndex}`}>
                <a href={source.url} target="_blank" rel="noreferrer">{source.title}</a>
                <p className={s.meta}>{source.publishedAt && new Date(source.publishedAt).toLocaleString('ko-KR')}</p>
                {(source.observations || []).flatMap(observation => observation.evidence || []).map((span, spanIndex) =>
                  <blockquote key={`${span.spanId}-${spanIndex}`}>{span.text}</blockquote>)}
              </div>)}
              {(item.principles || []).map(principle => <p key={principle.chunkId}>{principle.source} · {principle.section}</p>)}
            </details>}
          </section>)}

          <details className={s.sources}>
            <summary>근거 기사와 원문 보기 ({flow.sources?.length || 0})</summary>
            {(flow.sources || []).map((source, sourceIndex) => <div className={s.source} key={`${source.articleId}-${sourceIndex}`}>
              <a href={source.url} target="_blank" rel="noreferrer">{source.title}</a>
              <p className={s.meta}>{source.source} · {source.publishedAt && new Date(source.publishedAt).toLocaleString('ko-KR')}</p>
              {(source.observations || []).map((observation, observationIndex) => <div key={observationIndex}>
                <p><strong>관측:</strong> {observation.text}</p>
                {observation.evidence?.map(span => <blockquote key={span.spanId}>{span.text}</blockquote>)}
              </div>)}
            </div>)}
          </details>
        </article>)}
      </div>
    </section>
  )
}
