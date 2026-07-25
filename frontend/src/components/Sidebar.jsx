import s from './Sidebar.module.css'

export default function Sidebar({ news }) {
  const scrollTo = (id) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
  }

  return (
    <aside className={s.sidebar}>
      <div className={s.title}>오늘의 뉴스</div>
      <ul className={s.list}>
        {news.map((n, i) => (
          <li key={n.articleId || n.id || i}>
            <button className={s.item} onClick={() => scrollTo(n.articleId || n.id)}>
              <span className={s.importance}>{'★'.repeat(n.importance || 3)}</span>
              {n.easyTitle || n.originalTitle || '제목 없음'}
            </button>
          </li>
        ))}
      </ul>
    </aside>
  )
}
