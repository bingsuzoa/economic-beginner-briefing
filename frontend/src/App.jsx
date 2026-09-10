import { useState, useEffect } from 'react'
import s from './App.module.css'
import Navbar from './components/Navbar'
import Sidebar from './components/Sidebar'
import DailyBriefing from './components/DailyBriefing'
import ExchangeRateSection from './components/ExchangeRateSection'
import Footer from './components/Footer'
import BottomNav from './components/BottomNav'
import LoginScreen from './components/LoginScreen'
import AccountManagement from './components/AccountManagement'
import { apiFetch } from './api'

export default function App() {
  const [user, setUser] = useState(undefined) // undefined = loading, null = not logged in
  const [briefing, setBriefing] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [activeMenu, setActiveMenu] = useState('home')

  useEffect(() => {
    apiFetch('/api/auth/me')
      .then(r => r.ok ? r.json() : null)
      .then(setUser)
      .catch(() => setUser(null))
  }, [])

  useEffect(() => {
    const titles = { home: 'Thoth - 홈', news: 'Thoth - 오늘의 경제흐름' }
    document.title = titles[activeMenu] || 'Thoth'
  }, [activeMenu])

  useEffect(() => {
    if (user === undefined || user === null) return
    apiFetch('/api/briefings/latest')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then((body) => {
        setBriefing(body)
        setLoading(false)
      })
      .catch((err) => {
        setError(err.message)
        setLoading(false)
      })
  }, [user])

  // loading auth state
  if (user === undefined) return null

  // not logged in → login screen
  if (user === null) return <LoginScreen onLoginSuccess={setUser} />

  const selectMenu = (menu) => {
    setActiveMenu(menu)
  }

  return (
    <div className={s.page}>
      <Navbar user={user} onLogout={() => setUser(null)} onAccountClick={() => selectMenu('account')} />
      <div className={s.layout}>
        <Sidebar activeMenu={activeMenu} onMenuChange={selectMenu} onAccountClick={() => selectMenu('account')} />
        <main className={s.main}>
          {activeMenu === 'account' && <AccountManagement user={user} onDeleted={() => setUser(null)} />}
          {activeMenu === 'home' && <ExchangeRateSection />}
          {activeMenu === 'news' && (
            <>
              {loading && (
                <div className={s.status}>
                  <span className={s.chick}>🐥</span>
                  토트가 경제 뉴스를 공부하고 있어요...
                </div>
              )}
              {error && (
                <div className={s.status}>
                  뉴스를 불러오지 못했어요.<br />잠시 후 다시 시도해주세요.
                </div>
              )}
              {!loading && !error && !briefing && (
                <div className={s.status}>
                  오늘의 경제흐름을 준비하고 있어요.
                </div>
              )}
              {!loading && !error && briefing && <DailyBriefing briefing={briefing} />}
            </>
          )}
        </main>
      </div>
      <Footer />
      <BottomNav activeMenu={activeMenu} onMenuChange={selectMenu} />
    </div>
  )
}
