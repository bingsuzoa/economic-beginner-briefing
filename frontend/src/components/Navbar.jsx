import s from './Navbar.module.css'

export default function Navbar() {
  return (
    <nav className={s.navbar}>
      <div className={s.logo}>
        <span className={s.chick}>🐥</span>
        병아리 경제 뉴스
      </div>
    </nav>
  )
}
