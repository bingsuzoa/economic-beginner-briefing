import s from './DailyBriefing.module.css'

const dateLabel = (value) => new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul', month: 'long', day: 'numeric',
}).format(new Date(`${value}T12:00:00+09:00`))

function Explanation({ text }) {
  const paragraphs = (text || '').replace(/\r\n?/g, '\n').split(/\n\s*\n/).map(value => value.trim()).filter(Boolean)
  return <div className={s.explanation}>{paragraphs.map((paragraph, index) => <p key={index}>{paragraph}</p>)}</div>
}

export default function DailyBriefing({ briefing, onPrevious, onNext, onSelectFlow, onReturnToList, selectedFlowIndex, navigationLoading, previousAvailable, nextAvailable, previousError }) {
  const flows = briefing.flows || []
  const selectedFlow = selectedFlowIndex === null ? null : flows[selectedFlowIndex]
  const heading = (
    <header className={s.header}>
      <p><img src="/images/news-icon.png" alt="" /> 데일리</p>
      <h1 id="daily-briefing-title">{dateLabel(briefing.targetDate)} 토트</h1>
    </header>
  )

  return (
    <section className={s.page} aria-labelledby="daily-briefing-title">
      {selectedFlow ? <>
        <nav className={s.navigation} aria-label="토트 목록 이동">
          <button className={s.back} onClick={onReturnToList}>목록으로 돌아가기</button>
        </nav>
        {heading}
        <article className={s.flow}>
          <p className={s.number}>토트 {selectedFlowIndex + 1}</p>
          <h2>{selectedFlow.title}</h2>
          <Explanation text={selectedFlow.explanation} />

          {(selectedFlow.questions || []).map((item, questionIndex) => <section className={s.question} key={item.id || questionIndex}>
            <h3>{item.question}</h3>
            <Explanation text={item.answer} />
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
            <summary>근거 기사와 원문 보기 ({selectedFlow.sources?.length || 0})</summary>
            {(selectedFlow.sources || []).map((source, sourceIndex) => <div className={s.source} key={`${source.articleId}-${sourceIndex}`}>
              <a href={source.url} target="_blank" rel="noreferrer">{source.title}</a>
              <p className={s.meta}>{source.source} · {source.publishedAt && new Date(source.publishedAt).toLocaleString('ko-KR')}</p>
              {(source.observations || []).map((observation, observationIndex) => <div key={observationIndex}>
                <p><strong>관측:</strong> {observation.text}</p>
                {observation.evidence?.map(span => <blockquote key={span.spanId}>{span.text}</blockquote>)}
              </div>)}
            </div>)}
          </details>
        </article>
      </> : <>
        <nav className={s.navigation} aria-label="토트 날짜 이동">
          <button className={s.previous} onClick={onPrevious} disabled={navigationLoading || !previousAvailable}>
            {navigationLoading ? '토트를 불러오는 중이에요' : previousAvailable ? '이전 토트' : '이전 토트가 없어요'}
          </button>
          <button className={s.next} onClick={onNext} disabled={navigationLoading || !nextAvailable}>
            {navigationLoading ? '토트를 불러오는 중이에요' : nextAvailable ? '다음 토트' : '다음 토트가 없어요'}
          </button>
        </nav>
        {heading}
        {previousError && <p className={s.previousError}>{previousError}</p>}
        {flows.length === 0 && <p className={s.empty}>오늘은 하나의 흐름으로 묶을 만한 경제 변화가 확인되지 않았어요.</p>}
        <div className={s.flowList}>
          {flows.map((flow, index) => <button className={s.flowRow} type="button" key={flow.id || index}
            onClick={() => onSelectFlow(index)} aria-label={`토트 ${index + 1}: ${flow.title} 본문 보기`}>
            <span className={s.number}>토트 {index + 1}</span>
            <strong>{flow.title}</strong>
          </button>)}
        </div>
      </>}
    </section>
  )
}
