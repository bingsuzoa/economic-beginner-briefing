import { useState } from 'react'
import s from './TermsAccordion.module.css'

export default function TermsAccordion({ terms }) {
  const [open, setOpen] = useState({})

  if (!terms.length) return null

  const toggle = (i) => setOpen((prev) => ({ ...prev, [i]: !prev[i] }))

  return (
    <div className={s.section}>
      <div className={s.title}>📖 경제 용어</div>
      {terms.map((t, i) => (
        <div className={s.item} key={i}>
          <button className={s.toggle} onClick={() => toggle(i)}>
            {t.term}
            <span className={`${s.arrow} ${open[i] ? s.arrowOpen : ''}`}>▶</span>
          </button>
          <div className={`${s.content} ${open[i] ? s.contentOpen : ''}`}>
            <div className={s.inner}>
              {t.explanation}
              {t.example && <div className={s.example}>예) {t.example}</div>}
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
