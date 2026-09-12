import s from './Footer.module.css'

export default function Footer() {
  return (
    <footer className={s.footer}>
      <img src="/images/main-logo.png" alt="토트" className={s.logo} />
      <span>Thoth — 경제 초보자를 위한 뉴스 브리핑</span>
      <a href="/privacy">개인정보처리방침</a>
      <a href="/delete-account">계정 삭제</a>
      <a href="/contact">문의 및 운영자 정보</a>
    </footer>
  )
}
