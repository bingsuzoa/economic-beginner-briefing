import { useState } from 'react'
import s from './Sidebar.module.css'

const menus = [
  {
    id: 'news',
    icon: '/images/news-icon.png',
    title: '오늘의 병아리 뉴스',
    path: '/',
    isImage: true
  },
  {
    id: 'loan',
    icon: '/images/loan-icon.png',
    title: '대출계산기',
    path: '/loan',
    isImage: true
  }
]

export default function Sidebar({ onMenuChange }) {
  const [activeMenu, setActiveMenu] = useState('news')

  const handleMenuClick = (menu) => {
    setActiveMenu(menu.id)
    if (onMenuChange) {
      onMenuChange(menu.id)
    }
  }

  return (
    <aside className={s.sidebar}>
      <nav className={s.menu}>
        {menus.map((menu) => (
          <button
            key={menu.id}
            className={`${s.item} ${activeMenu === menu.id ? s.active : ''}`}
            onClick={() => handleMenuClick(menu)}
          >
            {menu.isImage ? (
              <img src={menu.icon} alt="" className={s.icon} />
            ) : (
              <span className={s.icon}>{menu.icon}</span>
            )}
            <span className={s.title}>{menu.title}</span>
          </button>
        ))}
      </nav>
    </aside>
  )
}
