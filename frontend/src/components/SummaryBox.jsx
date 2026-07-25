import s from './SummaryBox.module.css'

export default function SummaryBox({ lines }) {
  return (
    <div className={s.box}>
      <ul>
        {lines.map((line, i) => <li key={i}>{line}</li>)}
      </ul>
    </div>
  )
}
