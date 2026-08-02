import s from './Sidebar.module.css'
import { menus } from '../menus'

export default function Sidebar({ activeMenu, onMenuChange }) {
  return (
    <aside className={s.sidebar}>
      <nav className={s.menu}>
        {menus.map((menu) => (
          <button
            key={menu.id}
            className={`${s.item} ${activeMenu === menu.id ? s.active : ''}`}
            onClick={() => onMenuChange(menu.id)}
          >
            <img src={menu.icon} alt="" className={s.icon} />
            <span className={s.title}>{menu.title}</span>
          </button>
        ))}
      </nav>
    </aside>
  )
}
