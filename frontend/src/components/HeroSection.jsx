import s from './HeroSection.module.css'

export default function HeroSection() {
  return (
    <section className={s.hero}>
      <div className={s.text}>
        <h1>어려운 경제 뉴스, 토트가 먼저 읽어봤어요</h1>
        <p>토트는 매시간 업데이트됩니다.</p>
      </div>
      <img src="/images/hero-chick.png" alt="토트" className={s.chickBig} />
    </section>
  )
}
