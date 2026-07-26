import s from './Footer.module.css'

export default function Footer() {
  return (
    <footer className={s.footer}>
      <img src="/images/main-logo.png" alt="병아리" className={s.logo} />
      병아리 경제 뉴스 — 경제 초보자를 위한 뉴스 브리핑
    </footer>
  )
}
