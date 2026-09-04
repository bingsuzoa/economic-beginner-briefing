import { useEffect, useState } from 'react'
import Footer from '../components/Footer'
import { apiFetch } from '../api'
import s from './DeleteAccount.module.css'

export default function DeleteAccount() {
  const [form, setForm] = useState({ username: '', password: '' })
  const [confirming, setConfirming] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [complete, setComplete] = useState(false)

  useEffect(() => { document.title = '계정 삭제 | Thoth(토트)' }, [])

  const submit = async () => {
    setLoading(true); setError('')
    try {
      const res = await apiFetch('/api/auth/delete-account', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form),
      })
      const data = await res.json().catch(() => ({}))
      if (res.ok && data.success) { setComplete(true); setConfirming(false); setForm({ username: '', password: '' }) }
      else { setError(data.message || '계정을 삭제하지 못했습니다.'); setConfirming(false) }
    } catch { setError('서버에 연결할 수 없습니다.'); setConfirming(false) }
    finally { setLoading(false) }
  }

  return (
    <div className={s.page}>
      <header className={s.header}><a href="/" className={s.brand}><img src="/images/main-logo.png" alt="" />Thoth(토트)</a></header>
      <main className={s.main}>
        <section className={s.hero}>
          <span>ACCOUNT DELETION</span>
          <h1>계정 삭제 요청</h1>
          <p>Thoth(토트), 병아리 경제 뉴스의 계정과 연결된 개인정보를 삭제할 수 있어요.</p>
        </section>

        {complete ? <section className={`${s.card} ${s.complete}`}>
          <img src="/images/main-logo.png" alt="" />
          <h2>계정 삭제가 완료됐어요</h2>
          <p>계정과 연결된 개인정보 및 뉴스 열람 기록이 즉시 삭제됐습니다.</p>
        </section> : <section className={s.card}>
          <h2>웹에서 바로 삭제하기</h2>
          <p>본인 확인을 위해 가입한 아이디와 현재 비밀번호를 입력해주세요.</p>
          <form onSubmit={e => { e.preventDefault(); setError(''); setConfirming(true) }}>
            <label>아이디<input value={form.username} autoComplete="username" required
              onChange={e => setForm({ ...form, username: e.target.value })} /></label>
            <label>비밀번호<input type="password" value={form.password} autoComplete="current-password" required
              onChange={e => setForm({ ...form, password: e.target.value })} /></label>
            {error && <p className={s.error}>{error}</p>}
            <button disabled={!form.username || !form.password}>삭제 내용 확인</button>
          </form>
        </section>}

        <section className={s.card}>
          <h2>삭제되는 데이터</h2>
          <ul><li>아이디, 암호화된 이메일, 비밀번호 해시, 닉네임과 프로필 정보</li><li>가입일, 최근 로그인 일시와 뉴스 열람 기록</li><li>비밀번호 재설정 토큰과 로그인 세션</li></ul>
          <h2>별도 보관되는 데이터</h2>
          <ul><li>보안 목적의 접속 기록: 최대 3개월</li><li>문의·분쟁 처리 이메일: 관계 법령 적용 시 최대 3년</li></ul>
          <p>보관 자료는 계정을 복구하거나 다시 로그인하는 데 사용하지 않습니다. 현재 결제·구독 정보는 수집하지 않습니다.</p>
        </section>

        <section className={s.card}>
          <h2>처리 절차와 문의</h2>
          <p>아이디와 비밀번호 확인 후 즉시 삭제됩니다. 비밀번호를 잊었다면 등록한 이메일 주소에서 아래 문의처로 아이디와 삭제 요청을 보내주세요. 본인 확인 후 지체 없이 처리합니다.</p>
          <dl><div><dt>서비스</dt><dd>Thoth(토트) · 병아리 경제 뉴스</dd></div><div><dt>운영자</dt><dd>권미경</dd></div><div><dt>문의</dt><dd><a href="mailto:zxc_777@naver.com?subject=Thoth 계정 삭제 요청">zxc_777@naver.com</a></dd></div></dl>
          <p>로그인할 수 있다면 앱 내부의 <strong>계정 관리 → 계정 삭제</strong>에서도 같은 절차를 이용할 수 있습니다.</p>
        </section>
      </main>
      <Footer />

      {confirming && <div className={s.overlay} role="presentation">
        <div className={s.modal} role="dialog" aria-modal="true" aria-labelledby="final-delete-title">
          <img src="/images/main-logo.png" alt="" />
          <h2 id="final-delete-title">정말 영구 삭제할까요?</h2>
          <p>삭제한 계정과 개인정보는 복구할 수 없습니다.</p>
          <div><button className={s.cancel} onClick={() => setConfirming(false)}>취소</button><button className={s.delete} disabled={loading} onClick={submit}>{loading ? '삭제 중...' : '영구 삭제'}</button></div>
        </div>
      </div>}
    </div>
  )
}
