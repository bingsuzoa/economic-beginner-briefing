import { useState, useEffect } from 'react'
import s from './App.module.css'
import Navbar from './components/Navbar'
import Sidebar from './components/Sidebar'
import HeroSection from './components/HeroSection'
import NewsCard from './components/NewsCard'
import Footer from './components/Footer'

const API_BASE = '/api/briefing'

export default function App() {
  const [news, setNews] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
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
  }, [])

  return (
    <>
      <Navbar />
      <div className={s.layout}>
        {news.length > 0 && <Sidebar news={news} />}
        <main className={s.main}>
          <HeroSection />
          {loading && (
            <div className={s.status}>
              <span className={s.chick}>🐥</span>
              병아리가 경제 뉴스를 공부하고 있어요...
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
          {news.map((n, i) => (
            <NewsCard key={n.articleId || n.id || i} news={n} />
          ))}
        </main>
      </div>
      <Footer />
    </>
  )
}
