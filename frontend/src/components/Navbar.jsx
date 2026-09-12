import s from './Navbar.module.css'
import { apiFetch } from '../api'

export default function Navbar({ user, onLogout, onAccountClick, onLogoClick, logoLabel }) {
  const logout = () =>
    apiFetch('/api/auth/logout', { method: 'POST' })
      .then(() => { onLogout(); window.location.href = '/' })

  return (
    <nav className={s.navbar}>
      <button type="button" className={s.logo} onClick={onLogoClick} aria-label={logoLabel}>
        <img src="/images/main-logo.png" alt="토트" className={s.chick} />
        Thoth
      </button>
      <div className={s.auth}>
        <button className={s.userName} onClick={onAccountClick}>{user.nickname || user.username}</button>
        <button className={s.logoutBtn} onClick={logout}>로그아웃</button>
      </div>
    </nav>
  )
}
