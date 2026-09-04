import { lazy, Suspense, useState, useEffect } from 'react'
import s from './App.module.css'
import Navbar from './components/Navbar'
import Sidebar from './components/Sidebar'
import HeroSection from './components/HeroSection'
import NewsCard from './components/NewsCard'
import Footer from './components/Footer'
import BottomNav from './components/BottomNav'
import LoginScreen from './components/LoginScreen'
import AccountManagement from './components/AccountManagement'
import { apiUrl } from './api'

const EconomicNetwork = lazy(() => import('./components/EconomicNetwork'))

const API_BASE = apiUrl('/api/briefing')

const STOCK_CATEGORIES = ['investment']
const REALESTATE_CATEGORIES = ['housing', 'jeonse_monthly_rent', 'subscription']

export default function App() {
  const [user, setUser] = useState(undefined) // undefined = loading, null = not logged in
  const [news, setNews] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [activeMenu, setActiveMenu] = useState('news')

  useEffect(() => {
    fetch(apiUrl('/api/auth/me'))
      .then(r => r.ok ? r.json() : null)
      .then(setUser)
      .catch(() => setUser(null))
  }, [])

  useEffect(() => {
    const titles = { news: 'Thoth - 오늘의 토트', stock: 'Thoth - 주식', realestate: 'Thoth - 부동산', network: 'Thoth - 토트 경제망', loan: 'Thoth - 대출계산기' }
    document.title = titles[activeMenu] || 'Thoth'
  }, [activeMenu])

  useEffect(() => {
    if (user === undefined || user === null) return
    fetch(`${API_BASE}/articles`)
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then((body) => {
        setNews(body.data || [])
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

  return (
    <>
      <Navbar user={user} onLogout={() => setUser(null)} onAccountClick={() => setActiveMenu('account')} />
      <div className={s.layout}>
        <Sidebar activeMenu={activeMenu} onMenuChange={setActiveMenu} onAccountClick={() => setActiveMenu('account')} />
        <main className={s.main}>
          {activeMenu === 'account' && <AccountManagement user={user} onDeleted={() => setUser(null)} />}
          {activeMenu === 'network' && <Suspense fallback={<div className={s.status}>3D 경제망을 준비하고 있어요...</div>}>
            <EconomicNetwork />
          </Suspense>}
          {activeMenu === 'news' && <HeroSection />}
          {activeMenu === 'stock' && (
            <section className={s.loanHero}>
              <img src="/images/stock-hero.png" alt="주식" className={s.loanImage} />
            </section>
          )}
          {activeMenu === 'realestate' && (
            <section className={s.loanHero}>
              <img src="/images/realestate-hero.png" alt="부동산" className={s.loanImage} />
            </section>
          )}
          {activeMenu === 'loan' && (
            <div className={s.loanHero}>
              <img src="/images/loan-hero.png" alt="대출계산기" className={s.loanImage} />
              <p className={s.loanMessage}>대출계산기는 업데이트중이예요.</p>
            </div>
          )}
          {['news', 'stock', 'realestate'].includes(activeMenu) && (
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
              {!loading && !error && news.length === 0 && (
                <div className={s.status}>
                  아직 분석이 완료된 경제 뉴스가 없어요.<br />잠시 후 다시 확인해주세요.
                </div>
              )}
              {news.filter((n) => {
                const cat = (n.category || '').toLowerCase()
                if (activeMenu === 'stock') return STOCK_CATEGORIES.includes(cat)
                if (activeMenu === 'realestate') return REALESTATE_CATEGORIES.includes(cat)
                return !STOCK_CATEGORIES.includes(cat) && !REALESTATE_CATEGORIES.includes(cat)
              }).map((n, i) => (
                <NewsCard key={n.articleId || n.id || i} news={n} />
              ))}
            </>
          )}
        </main>
      </div>
      <Footer />
      <BottomNav activeMenu={activeMenu} onMenuChange={setActiveMenu} />
    </>
  )
}
