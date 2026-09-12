import { useState, useEffect, useCallback } from 'react'
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
    const titles = { home: 'Thoth - 홈', news: 'Thoth - 데일리' }
    document.title = titles[activeMenu] || 'Thoth'
  }, [activeMenu])

  const loadBriefing = useCallback((path) => {
    setLoading(true)
    setError(null)
    return apiFetch(path)
      .then((res) => {
        if (!res.ok) throw new Error(res.status === 404 ? '아직 토트를 찾지 못했어요.' : `HTTP ${res.status}`)
        return res.json()
      })
      .then(setBriefing)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (user === undefined || user === null) return
    loadBriefing('/api/briefings/latest')
  }, [user, loadBriefing])

  // loading auth state
  if (user === undefined) return null

  // not logged in → login screen
  if (user === null) return <LoginScreen onLoginSuccess={setUser} />

  const selectMenu = (menu) => {
    setActiveMenu(menu)
  }

  const showPreviousBriefing = () => {
    if (!briefing || loading) return
    const date = new Date(`${briefing.targetDate}T12:00:00+09:00`)
    date.setUTCDate(date.getUTCDate() - 1)
    loadBriefing(`/api/briefings/${date.toISOString().slice(0, 10)}`)
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
              {loading && !briefing && (
                <div className={s.status}>
                  <span className={s.chick}>🐥</span>
                  토트가 경제 뉴스를 공부하고 있어요...
                </div>
              )}
              {error && !briefing && (
                <div className={s.status}>
                  뉴스를 불러오지 못했어요.<br />잠시 후 다시 시도해주세요.
                </div>
              )}
              {!loading && !error && !briefing && (
                <div className={s.status}>
                  오늘의 토트를 준비하고 있어요.
                </div>
              )}
              {briefing && <DailyBriefing briefing={briefing} onPrevious={showPreviousBriefing}
                previousLoading={loading} previousError={error} />}
            </>
          )}
        </main>
      </div>
      <Footer />
      <BottomNav activeMenu={activeMenu} onMenuChange={selectMenu} />
    </div>
  )
}
