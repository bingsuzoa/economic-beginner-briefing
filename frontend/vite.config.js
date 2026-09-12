import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

const WEB_API = 'https://dev-api.economic-beginner.com'
const APP_PROD_API = 'https://economic-beginner.com/api'

export default defineConfig(({ mode }) => {
  const { VITE_API_BASE_URL: apiBaseUrl } = loadEnv(mode, process.cwd(), '')
  if (mode === 'web' && apiBaseUrl !== WEB_API) throw new Error('Web build must use the DEV API')
  if (mode === 'app-prod' && apiBaseUrl !== APP_PROD_API) throw new Error('APP PROD build cannot use DEV API')

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': `http://localhost:${process.env.API_PORT || 3000}`,
      },
    },
  }
})
