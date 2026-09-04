export const EXCHANGE_RATE_PERIODS = {
  week: { label: '1주', apiValue: '1W' },
  month: { label: '1개월', apiValue: '1M' },
  quarter: { label: '3개월', apiValue: '3M' },
  year: { label: '1년', apiValue: '1Y' },
}

export async function fetchExchangeRateHistory(currency, periodKey) {
  const response = await fetch(`/api/exchange-rate/history/${currency}?period=${EXCHANGE_RATE_PERIODS[periodKey].apiValue}`)
  const body = await response.json()
  if (!response.ok || !body.success) throw new Error(body.message || '환율 정보를 불러오지 못했어요.')
  return body.data
}

export async function fetchCurrentExchangeRate(currency) {
  const response = await fetch(`/api/exchange-rate/current/${currency}`)
  const body = await response.json()
  if (!response.ok || !body.success) throw new Error(body.message || '현재 환율을 불러오지 못했어요.')
  return body.data
}
