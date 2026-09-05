import { lazy, Suspense, useState, useEffect } from 'react'
import s from './App.module.css'
import Navbar from './components/Navbar'
import Sidebar from './components/Sidebar'
import HeroSection from './components/HeroSection'
import NewsCard, { categoryLabel, formatRelativeTime, markAsRead } from './components/NewsCard'
import ExchangeRateSection from './components/ExchangeRateSection'
import Footer from './components/Footer'
import BottomNav from './components/BottomNav'
import LoginScreen from './components/LoginScreen'
import AccountManagement from './components/AccountManagement'
import { apiFetch } from './api'

const EconomicNetwork = lazy(() => import('./components/EconomicNetwork'))

export default function App() {
  const [user, setUser] = useState(undefined) // undefined = loading, null = not logged in
  const [news, setNews] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [activeMenu, setActiveMenu] = useState('home')
  const [selectedArticle, setSelectedArticle] = useState(null)

  useEffect(() => {
    apiFetch('/api/auth/me')
      .then(r => r.ok ? r.json() : null)
      .then(setUser)
      .catch(() => setUser(null))
  }, [])

  useEffect(() => {
    const titles = { home: 'Thoth - 홈', news: 'Thoth - 오늘의 토트', network: 'Thoth - 토트 경제망' }
    document.title = titles[activeMenu] || 'Thoth'
  }, [activeMenu])

  useEffect(() => {
    if (user === undefined || user === null) return
    apiFetch('/api/briefing/articles')
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

  const selectMenu = (menu) => {
    setActiveMenu(menu)
    if (menu !== 'news') setSelectedArticle(null)
  }

  const markArticleRead = (articleId, readAt) => {
    setNews(current => current.map(article => article.articleId === articleId ? { ...article, readAt } : article))
  }

  const openArticle = (article) => {
    const readAt = article.readAt || new Date().toISOString()
    setSelectedArticle({ ...article, readAt })
    if (!article.readAt) {
      markArticleRead(article.articleId, readAt)
      markAsRead(article.articleId)
    }
  }

  return (
    <div className={s.page}>
      <Navbar user={user} onLogout={() => setUser(null)} onAccountClick={() => selectMenu('account')} />
      <div className={s.layout}>
        <Sidebar activeMenu={activeMenu} onMenuChange={selectMenu} onAccountClick={() => selectMenu('account')} />
        <main className={s.main}>
          {activeMenu === 'account' && <AccountManagement user={user} onDeleted={() => setUser(null)} />}
          {activeMenu === 'home' && <ExchangeRateSection />}
          {activeMenu === 'network' && <Suspense fallback={<div className={s.status}>3D 경제망을 준비하고 있어요...</div>}>
            <EconomicNetwork />
          </Suspense>}
          {activeMenu === 'news' && (
            <>
              {!selectedArticle && <HeroSection />}
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
                  토트가 열심히 기사를 찾고 있어요.
                </div>
              )}
              {!loading && !error && selectedArticle && <>
                <button className={s.backToList} onClick={() => setSelectedArticle(null)}>← 오늘의 토트 목록</button>
                <NewsCard news={selectedArticle} onMarkRead={markArticleRead} showReadState={false} />
              </>}
              {!loading && !error && !selectedArticle && <section className={s.articleList} aria-label="오늘의 토트 기사 목록">
                {news.map((article, index) => (
                  <button key={article.articleId || article.id || index} className={`${s.articleRow} ${article.readAt ? s.articleRead : ''}`} onClick={() => openArticle(article)}>
                    <span className={s.articleMeta}>{article.readAt ? `읽음 · ${formatRelativeTime(article.readAt)}` : `${categoryLabel(article.category)} · 발행 ${new Date(article.publishedAt).toLocaleString('ko-KR')}`}</span>
                    <strong>{article.easyTitle || article.originalTitle || '제목 없음'}</strong>
                  </button>
                ))}
              </section>}
            </>
          )}
        </main>
      </div>
      <Footer />
      <BottomNav activeMenu={activeMenu} onMenuChange={selectMenu} />
    </div>
  )
}
