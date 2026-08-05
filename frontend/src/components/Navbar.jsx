import s from './Navbar.module.css'

export default function Navbar({ user, onLogout, onAccountClick }) {
  const logout = () =>
    fetch('/api/auth/logout', { method: 'POST' })
      .then(() => { onLogout(); window.location.href = '/' })

  return (
    <nav className={s.navbar}>
      <div className={s.logo}>
        <img src="/images/main-logo.png" alt="토트" className={s.chick} />
        Thoth
      </div>
      <div className={s.auth}>
        <button className={s.userName} onClick={onAccountClick}>{user.nickname || user.username}</button>
        <button className={s.logoutBtn} onClick={logout}>로그아웃</button>
      </div>
    </nav>
  )
}
