export const EXCHANGE_RATE_PERIODS = {
  week: { label: '1주', apiValue: '1W' },
  month: { label: '1개월', apiValue: '1M' },
  quarter: { label: '3개월', apiValue: '3M' },
  year: { label: '1년', apiValue: '1Y' },
}

export async function fetchExchangeRate(periodKey) {
  const response = await fetch(`/api/exchange-rate/usd-krw?period=${EXCHANGE_RATE_PERIODS[periodKey].apiValue}`)
  const body = await response.json()
  if (!response.ok || !body.success) throw new Error(body.message || '환율 정보를 불러오지 못했어요.')
  return body.data
}
