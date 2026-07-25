import s from './ImpactSection.module.css'

export default function ImpactSection({ positive, negative }) {
  return (
    <div className={s.grid}>
      <div className={`${s.card} ${s.positive}`}>
        <div className={s.label}>😊 긍정적 영향</div>
        {positive}
      </div>
      <div className={`${s.card} ${s.negative}`}>
        <div className={s.label}>😥 부정적 영향</div>
        {negative}
      </div>
    </div>
  )
}
