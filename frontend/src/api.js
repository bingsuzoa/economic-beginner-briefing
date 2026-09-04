const baseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

export const apiUrl = (path) => !baseUrl ? path
  : baseUrl.endsWith('/api') && path.startsWith('/api/') ? `${baseUrl}${path.slice(4)}`
  : `${baseUrl}${path}`

export const apiFetch = (path, options) =>
  fetch(apiUrl(path), { ...options, credentials: 'include' })
