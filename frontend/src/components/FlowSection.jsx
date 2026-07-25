import s from './FlowSection.module.css'

export default function FlowSection({ icon, heading, children }) {
  return (
    <div className={s.section}>
      <h4 className={s.heading}>
        <span className={s.icon}>{icon}</span>
        {heading}
      </h4>
      <div className={s.body}>{children}</div>
    </div>
  )
}
