const baseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

export const apiUrl = (path) => baseUrl && path.startsWith('/api/')
  ? `${baseUrl}${path.slice(4)}`
  : path
