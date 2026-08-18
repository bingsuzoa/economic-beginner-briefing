import test from 'node:test'
import assert from 'node:assert/strict'
import { exchangeRateData, getExchangeRateSummary } from './exchangeRate.js'

test('기간별 데이터와 평균, 원화 방향을 함께 계산한다', () => {
  const week = getExchangeRateSummary(exchangeRateData, 'week')
  const month = getExchangeRateSummary(exchangeRateData, 'month')

  assert.equal(week.points.length, 8)
  assert.equal(month.points.length, 31)
  assert.equal(month.current, exchangeRateData.currentRate)
  assert.equal(month.points.at(-1).date, '2026-08-18')
  assert.ok(Number.isFinite(month.average))
  assert.equal(month.direction, month.periodChangePercent > 0 ? 'weak' : 'strong')
})
