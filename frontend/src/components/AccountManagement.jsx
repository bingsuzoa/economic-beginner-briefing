import { useState } from 'react'
import accountLogo from '../assets/account-logo.png'
import { apiFetch } from '../api'
import s from './AccountManagement.module.css'

export default function AccountManagement({ user, onDeleted }) {
  const [confirming, setConfirming] = useState(false)
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const close = () => { setConfirming(false); setPassword(''); setError('') }
  const deleteAccount = async (event) => {
    event.preventDefault()
    setLoading(true); setError('')
    try {
      const res = await apiFetch('/api/auth/me', {
        method: 'DELETE', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      })
      const data = await res.json().catch(() => ({}))
      if (res.ok && data.success) onDeleted()
      else setError(data.message || '계정을 삭제하지 못했습니다.')
    } catch { setError('서버에 연결할 수 없습니다.') }
    finally { setLoading(false) }
  }

  return (
    <section className={s.page}>
      <div className={s.heading}>
        <img src={accountLogo} alt="" />
        <div><h1>계정 관리</h1><p>내 계정 정보와 탈퇴를 관리할 수 있어요.</p></div>
      </div>
      <div className={s.card}>
        <dl>
          <div><dt>아이디</dt><dd>{user.username}</dd></div>
          <div><dt>이메일</dt><dd>{user.email || '등록 정보 없음'}</dd></div>
          <div><dt>닉네임</dt><dd>{user.nickname}</dd></div>
        </dl>
      </div>
      <div className={`${s.card} ${s.dangerZone}`}>
        <h2>계정 삭제</h2>
        <p>계정 정보, 뉴스 열람 기록과 인증 토큰이 영구 삭제되며 복구할 수 없어요.</p>
        <p>공용 뉴스와 법령상 보관이 필요한 접속·문의 기록은 안내된 기간 동안만 보관될 수 있어요.</p>
        <button className={s.deleteLink} onClick={() => setConfirming(true)}>계정 삭제</button>
      </div>

      {confirming && <div className={s.overlay} role="presentation" onMouseDown={e => e.target === e.currentTarget && close()}>
        <form className={s.modal} role="dialog" aria-modal="true" aria-labelledby="delete-title" onSubmit={deleteAccount}>
          <img src={accountLogo} alt="" />
          <h2 id="delete-title">정말 계정을 삭제할까요?</h2>
          <p>마지막으로 본인 확인을 위해 현재 비밀번호를 입력해주세요.</p>
          <input type="password" autoFocus autoComplete="current-password" placeholder="현재 비밀번호"
            value={password} onChange={e => setPassword(e.target.value)} />
          {error && <p className={s.error}>{error}</p>}
          <div className={s.actions}>
            <button type="button" className={s.cancel} onClick={close}>계속 사용할게요</button>
            <button className={s.delete} disabled={loading || !password}>
              {loading ? '삭제 중...' : '영구 삭제'}
            </button>
          </div>
        </form>
      </div>}
    </section>
  )
}
