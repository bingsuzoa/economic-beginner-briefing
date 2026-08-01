import { useState, useEffect, useRef } from 'react'
import s from './LoginScreen.module.css'

const useTitle = (t) => useEffect(() => { document.title = t }, [])

const SIGNUP_STEPS = ['phone', 'sms', 'name', 'birth', 'nickname']
const LOGIN_STEPS = ['phone', 'sms']

export default function LoginScreen({ onLoginSuccess }) {
  const [step, setStep] = useState('initial')
  const [intent, setIntent] = useState('signup')
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [birthDigits, setBirthDigits] = useState('')
  const [genderCode, setGenderCode] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [timer, setTimer] = useState(0)
  const [canResend, setCanResend] = useState(false)
  const [nicknameOk, setNicknameOk] = useState(null)
  const [toast, setToast] = useState('')
  const genderRef = useRef(null)
  const timerRef = useRef(null)
  useTitle('Thoth - 시작하기')

  // Timer
  useEffect(() => {
    if (timer <= 0) { clearInterval(timerRef.current); return }
    timerRef.current = setInterval(() => setTimer(t => t - 1), 1000)
    return () => clearInterval(timerRef.current)
  }, [timer > 0])

  const steps = intent === 'signup' ? SIGNUP_STEPS : LOGIN_STEPS
  const stepIdx = steps.indexOf(step)
  const progress = step === 'initial' ? 0
    : step === 'checking' ? 85
    : stepIdx >= 0 ? Math.round(((stepIdx + 1) / steps.length) * 100) : 0

  const fmtTimer = () => `${Math.floor(timer / 60)}:${String(timer % 60).padStart(2, '0')}`
  const fmtPhone = p => p.length === 11 ? `${p.slice(0,3)}-${p.slice(3,7)}-${p.slice(7)}` : p

  const deriveBirthInfo = () => {
    const gc = parseInt(genderCode)
    const century = gc <= 2 ? 1900 : 2000
    const y = century + parseInt(birthDigits.substring(0, 2))
    const m = birthDigits.substring(2, 4)
    const d = birthDigits.substring(4, 6)
    return { birthDate: `${y}-${m}-${d}`, gender: gc % 2 === 1 ? '남성' : '여성' }
  }

  const resetAll = () => {
    setStep('initial'); setPhone(''); setCode(''); setName('')
    setBirthDigits(''); setGenderCode(''); setNickname('')
    setError(''); setNicknameOk(null); setTimer(0); setCanResend(false)
  }

  const startTimer = () => { setTimer(180); setCanResend(false); setTimeout(() => setCanResend(true), 60000) }

  // ── Handlers ──

  const handleSendCode = async () => {
    setError(''); setLoading(true)
    try {
      const res = await fetch('/api/auth/sms/send', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone }),
      })
      const data = await res.json()
      if (res.ok && data.success) {
        setStep('sms'); startTimer(); setCode('')
        if (data.devCode) {
          // Dev mode: auto-verify
          setCode(data.devCode)
          setTimeout(() => handleVerify(data.devCode), 300)
        }
      } else setError(data.message || '발송에 실패했습니다.')
    } catch { setError('서버에 연결할 수 없습니다.') }
    finally { setLoading(false) }
  }

  const handleVerify = async (codeOverride) => {
    setError(''); setLoading(true)
    try {
      const res = await fetch('/api/auth/sms/verify', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone, code: codeOverride || code }),
      })
      const data = await res.json()
      if (!res.ok || !data.success) { setError(data.message || '인증에 실패했습니다.'); return }

      if (intent === 'login') {
        // Auto-login
        const loginRes = await fetch('/api/auth/login', { method: 'POST' })
        const loginData = await loginRes.json()
        if (loginData.success) onLoginSuccess(loginData.user)
        else setError(loginData.message || '로그인에 실패했습니다.')
      } else {
        setStep('name')
      }
    } catch { setError('서버에 연결할 수 없습니다.') }
    finally { setLoading(false) }
  }

  const handleNameNext = () => {
    if (!name.trim()) { setError('이름을 입력해주세요.'); return }
    setError(''); setStep('birth')
  }

  const handleBirthNext = async () => {
    if (birthDigits.length !== 6 || !genderCode) { setError('생년월일과 성별을 입력해주세요.'); return }
    setError(''); setStep('checking'); setLoading(true)
    try {
      const res = await fetch('/api/auth/check-member', { method: 'POST' })
      const data = await res.json()
      if (data.exists) {
        setToast('이미 가입된 계정입니다. 로그인 화면으로 이동합니다.')
        setTimeout(() => { setToast(''); resetAll() }, 2500)
      } else {
        setStep('nickname')
      }
    } catch { setError('서버에 연결할 수 없습니다.'); setStep('birth') }
    finally { setLoading(false) }
  }

  const checkNickname = async (nick) => {
    if (!nick || nick.length < 2) { setNicknameOk(null); return }
    try {
      const res = await fetch(`/api/auth/check-nickname?nickname=${encodeURIComponent(nick)}`)
      const data = await res.json()
      setNicknameOk(!data.taken)
    } catch { setNicknameOk(null) }
  }

  const handleSignup = async () => {
    if (!nickname.trim()) { setError('닉네임을 입력해주세요.'); return }
    if (nicknameOk === false) { setError('이미 사용 중인 닉네임입니다.'); return }
    setError(''); setLoading(true)
    const { birthDate, gender } = deriveBirthInfo()
    try {
      const res = await fetch('/api/auth/signup', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, nickname, birthDate, gender }),
      })
      const data = await res.json()
      if (data.success) onLoginSuccess(data.user)
      else setError(data.message || '가입에 실패했습니다.')
    } catch { setError('서버에 연결할 수 없습니다.') }
    finally { setLoading(false) }
  }

  const onKey = (handler) => (e) => { if (e.key === 'Enter') handler() }

  // ── Step checks for completed display ──
  const past = (target) => {
    const all = [...SIGNUP_STEPS, 'checking']
    return all.indexOf(step) > all.indexOf(target)
  }

  // ── Initial ──
  if (step === 'initial') {
    return (
      <div className={s.page}>
        <div className={s.card}>
          <img src="/images/main-logo.png" alt="토트" className={s.logo} />
          <h1 className={s.heroTitle}>Thoth</h1>
          <p className={s.heroSub}>경제 초보자를 위한 맞춤 뉴스 브리핑</p>
          <div className={s.heroButtons}>
            <button className={`${s.btn} ${s.primary}`}
              onClick={() => { setIntent('signup'); setStep('phone'); setError('') }}>시작하기</button>
            <button className={`${s.btn} ${s.outline}`}
              onClick={() => { setIntent('login'); setStep('phone'); setError('') }}>로그인</button>
          </div>
        </div>
        {toast && <div className={s.toast}>{toast}</div>}
      </div>
    )
  }

  // ── Step screens ──
  return (
    <div className={s.page}>
      <div className={s.stepCard}>
        {/* Progress */}
        <div className={s.progressBar}><div className={s.progressFill} style={{ width: `${progress}%` }} /></div>

        {/* Completed steps */}
        <div className={s.completed}>
          {past('phone') && (
            <div className={s.done}><span className={s.doneIcon}>✓</span><span className={s.doneLabel}>휴대폰</span><span className={s.doneValue}>{fmtPhone(phone)}</span></div>
          )}
          {past('sms') && (
            <div className={s.done}><span className={s.doneIcon}>✓</span><span className={s.doneLabel}>인증</span><span className={s.doneValue}>완료</span></div>
          )}
          {past('name') && (
            <div className={s.done}><span className={s.doneIcon}>✓</span><span className={s.doneLabel}>이름</span><span className={s.doneValue}>{name}</span></div>
          )}
          {past('birth') && (
            <div className={s.done}><span className={s.doneIcon}>✓</span><span className={s.doneLabel}>생년월일</span><span className={s.doneValue}>{birthDigits.slice(0,2)}.{birthDigits.slice(2,4)}.{birthDigits.slice(4)} / {deriveBirthInfo().gender}</span></div>
          )}
        </div>

        {/* Current step */}
        <div className={s.stepArea} key={step}>

          {step === 'phone' && (
            <>
              <h2 className={s.question}>휴대폰 번호를<br/>입력해주세요</h2>
              <input type="tel" inputMode="numeric" placeholder="01012345678" autoFocus
                value={phone} onChange={e => setPhone(e.target.value.replace(/\D/g, ''))}
                onKeyDown={onKey(handleSendCode)} className={s.input} maxLength={11} />
              {error && <p className={s.error}>{error}</p>}
              <button className={`${s.btn} ${s.primary} ${s.fixedBtn}`}
                disabled={loading || phone.length < 10} onClick={handleSendCode}>
                {loading ? '발송 중...' : '인증번호 받기'}
              </button>
              <p className={s.back} onClick={resetAll}>이전으로</p>
            </>
          )}

          {step === 'sms' && (
            <>
              <h2 className={s.question}>인증번호 6자리를<br/>입력해주세요</h2>
              {timer > 0
                ? <p className={s.timer}>{fmtTimer()}</p>
                : <p className={s.timerExpired}>인증번호가 만료되었습니다</p>}
              <input type="text" inputMode="numeric" maxLength={6} placeholder="000000" autoFocus
                value={code} onChange={e => setCode(e.target.value.replace(/\D/g, ''))}
                onKeyDown={onKey(handleVerify)} className={`${s.input} ${s.codeInput}`} />
              {error && <p className={s.error}>{error}</p>}
              <button className={`${s.btn} ${s.primary} ${s.fixedBtn}`}
                disabled={loading || code.length !== 6 || timer <= 0} onClick={handleVerify}>
                {loading ? '확인 중...' : '다음'}
              </button>
              {canResend && (
                <button className={`${s.btn} ${s.outline} ${s.resendBtn}`}
                  onClick={handleSendCode} disabled={loading}>재전송</button>
              )}
              <p className={s.back} onClick={() => { setStep('phone'); setError(''); setCode('') }}>번호 변경</p>
            </>
          )}

          {step === 'name' && (
            <>
              <h2 className={s.question}>이름을<br/>입력해주세요</h2>
              <input type="text" placeholder="홍길동" autoFocus
                value={name} onChange={e => setName(e.target.value)}
                onKeyDown={onKey(handleNameNext)} className={s.input} />
              {error && <p className={s.error}>{error}</p>}
              <button className={`${s.btn} ${s.primary} ${s.fixedBtn}`}
                disabled={!name.trim()} onClick={handleNameNext}>다음</button>
            </>
          )}

          {step === 'birth' && (
            <>
              <h2 className={s.question}>주민등록번호 앞 7자리를<br/>입력해주세요</h2>
              <div className={s.rrnRow}>
                <input type="text" inputMode="numeric" maxLength={6} placeholder="생년월일" autoFocus
                  value={birthDigits} onChange={e => {
                    const v = e.target.value.replace(/\D/g, '')
                    setBirthDigits(v)
                    if (v.length === 6) genderRef.current?.focus()
                  }} className={`${s.input} ${s.rrnBirth}`} />
                <span className={s.rrnDash}>—</span>
                <input type="text" inputMode="numeric" maxLength={1} placeholder="●" ref={genderRef}
                  value={genderCode} onChange={e => setGenderCode(e.target.value.replace(/[^1-4]/g, ''))}
                  onKeyDown={onKey(handleBirthNext)} className={`${s.input} ${s.rrnGender}`} />
                <span className={s.rrnDots}>●●●●●●</span>
              </div>
              {error && <p className={s.error}>{error}</p>}
              <button className={`${s.btn} ${s.primary} ${s.fixedBtn}`}
                disabled={birthDigits.length !== 6 || !genderCode} onClick={handleBirthNext}>다음</button>
            </>
          )}

          {step === 'checking' && (
            <div className={s.checkingArea}>
              <div className={s.spinner} />
              <p className={s.checkingText}>확인 중...</p>
            </div>
          )}

          {step === 'nickname' && (
            <>
              <h2 className={s.question}>사용할 닉네임을<br/>입력해주세요</h2>
              <div className={s.nicknameWrap}>
                <input type="text" placeholder="닉네임" autoFocus
                  value={nickname} onChange={e => { setNickname(e.target.value); setNicknameOk(null) }}
                  onBlur={e => checkNickname(e.target.value)}
                  onKeyDown={onKey(handleSignup)} className={s.input} />
                {nicknameOk === true && <span className={s.nickOk}>사용 가능</span>}
                {nicknameOk === false && <span className={s.nickBad}>이미 사용 중</span>}
              </div>
              {error && <p className={s.error}>{error}</p>}
              <button className={`${s.btn} ${s.primary} ${s.fixedBtn}`}
                disabled={loading || !nickname.trim() || nicknameOk === false} onClick={handleSignup}>
                {loading ? '가입 중...' : '가입 완료'}
              </button>
            </>
          )}
        </div>
      </div>
      {toast && <div className={s.toast}>{toast}</div>}
    </div>
  )
}
