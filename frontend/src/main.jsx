import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import PrivacyPolicy from './pages/PrivacyPolicy'
import './index.css'

const routes = {
  '/privacy': <PrivacyPolicy />,
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {routes[window.location.pathname.replace(/\/$/, '') || '/'] || <App />}
  </React.StrictMode>,
)
