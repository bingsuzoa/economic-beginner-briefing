import { useMemo, useState } from 'react'
import s from './ExchangeRateSection.module.css'
import { exchangeRateData, EXCHANGE_RATE_PERIODS, getExchangeRateSummary } from '../data/exchangeRate'

const formatRate = (value) => value.toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const formatPercent = (value) => `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
const formatDate = (date) => {
  const [, month, day] = date.split('-').map(Number)
  return `${month}. ${day}.`
}

const impacts = {
  weak: [
    ['✈️ 해외여행 😢', '같은 달러를 사기 위해 더 많은 원화가 필요해요.'],
    ['🛒 해외직구 😢', '달러 상품의 원화 가격 부담이 커질 수 있어요.'],
    ['⛽ 기름값 😢', '달러로 수입하는 원유의 비용 부담이 커질 수 있어요.'],
    ['🛍️ 수입제품 😢', '해외 상품과 원재료를 들여오는 비용이 높아질 수 있어요.'],
    ['📈 국내 물가 😢', '높아진 수입 비용이 반영되면 물가 상승 압력이 생길 수 있어요.'],
    ['🏭 일부 수출기업 🙂', '달러 매출을 원화로 바꿀 때 환산 금액이 커질 수 있어요.'],
  ],
  strong: [
    ['✈️ 해외여행 🙂', '같은 달러를 사는 데 필요한 원화 부담이 줄어들 수 있어요.'],
    ['🛒 해외직구 🙂', '달러 상품의 원화 가격 부담이 낮아질 수 있어요.'],
    ['⛽ 기름값 🙂', '달러로 수입하는 원유의 비용 부담이 줄어들 수 있어요.'],
    ['🛍️ 수입제품 🙂', '해외 상품과 원재료의 수입 비용이 낮아질 수 있어요.'],
    ['📉 국내 물가 🙂', '수입 비용이 낮아지면 물가 상승 압력이 완화될 수 있어요.'],
    ['🏭 일부 수출기업 😢', '달러 매출의 원화 환산 금액이 작아질 수 있어요.'],
  ],
}

function RateChart({ points, average }) {
  const width = 760
  const height = 260
  const padding = 28
  const rates = points.map(({ rate }) => rate)
  const min = Math.min(...rates, average)
  const max = Math.max(...rates, average)
  const range = max - min || 1
  const x = (index) => padding + (index / (points.length - 1)) * (width - padding * 2)
  const y = (rate) => height - padding - ((rate - min) / range) * (height - padding * 2)
  const line = points.map((point, index) => `${x(index)},${y(point.rate)}`).join(' ')
  const averageY = y(average)
  const current = points.at(-1)

  return (
    <div className={s.chartWrap}>
      <svg className={s.chart} viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`환율 추이와 평균 ${formatRate(average)}원 선`}> 
        <line x1={padding} y1={averageY} x2={width - padding} y2={averageY} className={s.averageLine} />
        <text x={padding + 4} y={averageY - 8} className={s.averageLabel}>평균 {formatRate(average)}원</text>
        <polyline points={line} className={s.rateLine} />
        <circle cx={x(points.length - 1)} cy={y(current.rate)} r="6" className={s.currentPoint} />
        <text x={x(points.length - 1) - 8} y={Math.max(18, y(current.rate) - 12)} textAnchor="end" className={s.currentLabel}>현재 {formatRate(current.rate)}원</text>
      </svg>
      <div className={s.chartDates}><span>{formatDate(points[0].date)}</span><span>{formatDate(current.date)}</span></div>
    </div>
  )
}

export default function ExchangeRateSection() {
  const [periodKey, setPeriodKey] = useState('month')
  const summary = useMemo(() => getExchangeRateSummary(exchangeRateData, periodKey), [periodKey])
  const dailyDifference = exchangeRateData.currentRate - exchangeRateData.previousRate
  const dailyPercent = (dailyDifference / exchangeRateData.previousRate) * 100
  const rising = dailyDifference >= 0
  const averagePosition = summary.averageDifference >= 0 ? '높아요' : '낮아요'
  const trend = summary.direction === 'weak' ? '원화 약세' : summary.direction === 'strong' ? '원화 강세' : '중립'
  const isNeutral = summary.direction === 'neutral'
  const exampleStart = Math.round(summary.current / 100) * 100
  const exampleEnd = summary.direction === 'strong' ? exampleStart - 80 : exampleStart + 80
  const currentImpacts = impacts[summary.direction] || impacts.weak

  return (
    <section className={s.section} aria-labelledby="exchange-rate-title">
      <header className={s.header}>
        <div>
          <p className={s.eyebrow}>🇺🇸 원/달러 환율</p>
          <h2 id="exchange-rate-title">{formatRate(exchangeRateData.currentRate)}원</h2>
          <p className={rising ? s.up : s.down}>
            {rising ? '▲ 상승' : '▼ 하락'} {formatRate(Math.abs(dailyDifference))}원 ({formatPercent(dailyPercent)}) <span>전일 대비</span>
          </p>
        </div>
        <p className={s.updated}>2026. 8. 18. 기준 · 예시 데이터</p>
      </header>

      <div className={s.periods} aria-label="환율 조회 기간">
        {Object.entries(EXCHANGE_RATE_PERIODS).map(([key, period]) => (
          <button key={key} type="button" aria-pressed={periodKey === key} onClick={() => setPeriodKey(key)}>{period.label}</button>
        ))}
      </div>

      <RateChart points={summary.points} average={summary.average} />

      <div className={s.stats} aria-live="polite">
        <div><span>최근 {summary.period.label} 변화</span><strong className={summary.periodChangePercent >= 0 ? s.up : s.down}>{formatPercent(summary.periodChangePercent)}</strong></div>
        <div><span>최근 {summary.period.label} 평균</span><strong>{formatRate(summary.average)}원</strong></div>
        <div><span>평균 대비 현재</span><strong>{formatRate(Math.abs(summary.averageDifference))}원 {averagePosition} ({formatPercent(summary.averageDifferencePercent)})</strong></div>
      </div>

      <div className={s.explainGrid}>
        <article className={s.explanation}>
          <h3>🐥 {isNeutral ? '최근에는 큰 방향성 없이 움직이고 있어요' : `지금은 ${trend} 흐름이에요`}</h3>
          <p>{summary.direction === 'strong'
            ? '1달러를 사는 데 필요한 원화가 줄고 있어요. 달러의 가치는 낮아지고, 원화의 가치는 상대적으로 올라가고 있다는 뜻이에요.'
            : isNeutral
              ? '선택한 기간의 시작과 지금을 비교했을 때 환율 변화가 0.1% 미만이에요.'
              : '1달러를 사는 데 필요한 원화가 늘고 있어요. 달러의 가치는 올라가고, 원화의 가치는 상대적으로 낮아지고 있다는 뜻이에요.'}</p>
        </article>
        <article className={s.example}>
          <h3>🐥 {trend === '중립' ? '원화 강세·약세가 뭐예요?' : `${trend}가 뭐예요?`}</h3>
          <p>예를 들어,</p>
          <strong>1달러 = {exampleStart.toLocaleString()}원 → {exampleEnd.toLocaleString()}원</strong>
          <p>이렇게 환율이 {exampleEnd > exampleStart ? '오르면 더 많은 원화를 내야 해서 달러 강세 / 원화 약세' : '내리면 더 적은 원화를 내도 돼서 달러 약세 / 원화 강세'}라고 표현해요.</p>
        </article>
      </div>

      <div className={s.impact}>
        <h3>💡 {isNeutral ? '환율이 움직이면 뭐가 달라지나요?' : `${trend}면 뭐가 달라지나요?`}</h3>
        <div className={s.impactGrid}>
          {currentImpacts.map(([title, description]) => <article key={title}><h4>{title}</h4><p>{description}</p></article>)}
        </div>
        <p className={s.caveat}>환율의 영향은 유가, 기업의 환헤지, 시장 상황 등에 따라 달라질 수 있어요.</p>
      </div>
    </section>
  )
}
