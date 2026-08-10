import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import PrivacyPolicy from './pages/PrivacyPolicy'
import DeleteAccount from './pages/DeleteAccount'
import Contact from './pages/Contact'
import './index.css'

const routes = {
  '/privacy': <PrivacyPolicy />,
  '/delete-account': <DeleteAccount />,
  '/contact': <Contact />,
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {routes[window.location.pathname.replace(/\/$/, '') || '/'] || <App />}
  </React.StrictMode>,
)
