import { useEffect } from 'react'
import Footer from '../components/Footer'
import s from './PrivacyPolicy.module.css'

export default function Contact() {
  useEffect(() => {
    const previousTitle = document.title
    document.title = '문의 및 운영자 정보 | 병아리 경제 뉴스'
    return () => { document.title = previousTitle }
  }, [])

  return (
    <div className={s.page}>
      <header className={s.header}>
        <a className={s.brand} href="/" aria-label="병아리 경제 뉴스 홈으로 이동">
          <img src="/images/main-logo.png" alt="" />
          <span>병아리 경제 뉴스</span>
        </a>
      </header>

      <main className={s.main}>
        <section className={s.hero}>
          <span className={s.eyebrow}>CONTACT</span>
          <h1>문의 및 운영자 정보</h1>
          <p>서비스 운영 주체와 문의 연락처를 안내합니다.</p>
        </section>

        <article className={s.policy}>
          <section>
            <h2>서비스 운영 정보</h2>
            <p>서비스 이용, 콘텐츠 및 개인정보 처리에 관한 문의는 아래 이메일로 보내주세요.</p>
            <div className={s.contactBox}>
              <strong>서비스명</strong>
              <span>Thoth(토트) · 병아리 경제 뉴스</span>
              <strong>운영자</strong>
              <span>권미경</span>
              <strong>문의 이메일</strong>
              <a href="mailto:zxc_777@naver.com">zxc_777@naver.com</a>
            </div>
          </section>
        </article>
      </main>
      <Footer />
    </div>
  )
}
