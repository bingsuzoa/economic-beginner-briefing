import s from './HeroSection.module.css'

export default function HeroSection() {
  return (
    <section className={s.hero}>
      <div className={s.text}>
        <h1>경제 뉴스, 쉽게 읽어요</h1>
        <p>
          어려운 경제 뉴스를 초보자도 이해할 수 있게 풀어드립니다.
          무엇이 바뀌었는지, 왜 바뀌었는지, 우리 가정에 어떤 영향이 있는지 알려드려요.
        </p>
      </div>
      <div className={s.chickBig}>🐥</div>
    </section>
  )
}
