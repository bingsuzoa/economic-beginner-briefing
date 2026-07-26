import s from './Navbar.module.css'

export default function Navbar() {
  return (
    <nav className={s.navbar}>
      <div className={s.logo}>
        <img src="/images/main-logo.png" alt="병아리" className={s.chick} />
        병아리 경제 뉴스
      </div>
    </nav>
  )
}
