import s from './BottomNav.module.css'
import { menus } from '../menus'

export default function BottomNav({ activeMenu, onMenuChange }) {
  return (
    <nav className={s.nav}>
      {menus.map((menu) => (
        <button
          key={menu.id}
          className={`${s.item} ${activeMenu === menu.id ? s.active : ''}`}
          onClick={() => onMenuChange(menu.id)}
        >
          <img src={menu.icon} alt="" className={s.icon} />
          <span className={s.label}>{menu.shortTitle || menu.title}</span>
        </button>
      ))}
    </nav>
  )
}
