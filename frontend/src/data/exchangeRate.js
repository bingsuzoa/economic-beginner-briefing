export const EXCHANGE_RATE_PERIODS = {
  week: { label: '1주', days: 7 },
  month: { label: '1개월', days: 30 },
  quarter: { label: '3개월', days: 90 },
  year: { label: '1년', days: 365 },
}

export const NEUTRAL_CHANGE_PERCENT = 0.1

const endDate = new Date('2026-08-18T00:00:00Z')
const history = Array.from({ length: 366 }, (_, index) => {
  const date = new Date(endDate)
  date.setUTCDate(date.getUTCDate() - (365 - index))
  const trend = index * 0.18
  const wave = Math.sin(index / 13) * 18 + Math.cos(index / 31) * 9
  return { date: date.toISOString().slice(0, 10), rate: Number((1302 + trend + wave).toFixed(2)) }
})

history[history.length - 2].rate = 1367.9
history[history.length - 1].rate = 1380.2

// Mock service response: replace this export with the real API response later.
export const exchangeRateData = {
  currentRate: history.at(-1).rate,
  previousRate: history.at(-2).rate,
  history,
}

export function getExchangeRateSummary(data, periodKey) {
  const period = EXCHANGE_RATE_PERIODS[periodKey]
  const points = data.history.slice(-(period.days + 1))
  const current = points.at(-1).rate
  const start = points[0].rate
  const average = points.reduce((sum, point) => sum + point.rate, 0) / points.length
  const periodChangePercent = ((current - start) / start) * 100
  const averageDifference = current - average
  const averageDifferencePercent = (averageDifference / average) * 100
  const direction = Math.abs(periodChangePercent) < NEUTRAL_CHANGE_PERCENT
    ? 'neutral'
    : periodChangePercent > 0 ? 'weak' : 'strong'

  return { period, points, current, average, periodChangePercent, averageDifference, averageDifferencePercent, direction }
}
