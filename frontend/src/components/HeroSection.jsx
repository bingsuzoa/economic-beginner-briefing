import s from './HeroSection.module.css'

export default function HeroSection() {
  return (
    <section className={s.hero}>
      <div className={s.text}>
        <h1>어려운 경제 뉴스, 병아리가 먼저 읽어봤어요</h1>
        <p>
          경제 뉴스가 낯설고 어렵게 느껴져도 괜찮아요.<br />
          새롭게 업데이트된 뉴스를 쉽게 풀어,<br />
          왜 일어났는지와 우리 가정에 어떤 영향이 있는지 알려드릴게요.
        </p>
      </div>
      <img src="/images/hero-chick.png" alt="병아리" className={s.chickBig} />
    </section>
  )
}
