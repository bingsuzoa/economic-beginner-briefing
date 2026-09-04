import { useEffect, useState } from 'react'
import s from './LoginScreen.module.css'
import { apiUrl } from '../api'

const useTitle = (title) => useEffect(() => { document.title = title }, [title])
const fields = [
  ['username', '사용할 아이디를 알려주세요', '영문, 숫자, 밑줄을 사용해 4~30자로 입력해주세요.', 'text', '아이디'],
  ['email', '이메일을 입력해주세요', '비밀번호를 잊었을 때 재설정에 사용해요.', 'email', 'name@example.com'],
  ['password', '비밀번호를 만들어주세요', '안전하게 사용할 수 있도록 8자 이상 입력해주세요.', 'password', '8자 이상'],
  ['passwordConfirm', '비밀번호를 한 번 더 입력해주세요', '방금 입력한 비밀번호와 같은지 확인할게요.', 'password', '비밀번호 확인'],
  ['nickname', '어떻게 불러드릴까요?', '서비스에서 사용할 닉네임을 2~20자로 입력해주세요.', 'text', '닉네임'],
]

const validate = (name, value, form) => {
  if (name === 'username' && !/^[A-Za-z0-9_]{4,30}$/.test(value)) return '영문, 숫자, 밑줄 4~30자로 입력해주세요.'
  if (name === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return '올바른 이메일 형식을 입력해주세요.'
  if (name === 'password' && value.length < 8) return '비밀번호는 8자 이상 입력해주세요.'
  if (name === 'passwordConfirm' && value !== form.password) return '비밀번호가 일치하지 않습니다.'
  if (name === 'nickname' && (value.trim().length < 2 || value.trim().length > 20)) return '닉네임은 2~20자로 입력해주세요.'
  return ''
}

export default function LoginScreen({ onLoginSuccess }) {
  const [mode, setMode] = useState('initial')
  const [form, setForm] = useState({ username: '', email: '', password: '', passwordConfirm: '', nickname: '' })
  const [signupStep, setSignupStep] = useState(0)
  const [createdUser, setCreatedUser] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  useTitle('Thoth - 시작하기')

  const update = (name, value) => {
    setForm(current => ({ ...current, [name]: value }))
    setError(validate(name, value, { ...form, [name]: value }))
  }

  const check = async (name) => {
    const value = form[name].trim()
    try {
      const res = await fetch(apiUrl(`/api/auth/check-${name}?${name}=${encodeURIComponent(value)}`))
      const data = await res.json()
      return data.taken ? 'taken' : 'available'
    } catch { /* 다음 버튼에서 재시도 안내 */ }
    return false
  }

  const nextSignupStep = async (event) => {
    event.preventDefault()
    const [name] = fields[signupStep]
    const validationError = validate(name, form[name], form)
    if (validationError) { setError(validationError); return }

    setError('')
    setLoading(true)
    const availabilityResult = ['username', 'email', 'nickname'].includes(name) ? await check(name) : 'available'
    if (availabilityResult !== 'available') {
      setError(availabilityResult === 'taken' ? `이미 사용 중인 ${name === 'username' ? '아이디' : name === 'email' ? '이메일' : '닉네임'}입니다.` : '중복 확인에 실패했습니다. 다시 시도해주세요.')
      setLoading(false)
      return
    }
    setLoading(false)
    if (signupStep < fields.length - 1) setSignupStep(step => step + 1)
    else submitSignup()
  }

  const submitSignup = async () => {
    setLoading(true)
    try {
      const res = await fetch(apiUrl('/api/auth/signup'), {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form),
      })
      const data = await res.json()
      if (res.ok && data.success) setCreatedUser(data.user)
      else setError(data.message || '가입에 실패했습니다.')
    } catch { setError('서버에 연결할 수 없습니다.') }
    finally { setLoading(false) }
  }

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await fetch(apiUrl('/api/auth/login'), {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: form.username, password: form.password }),
      })
      const data = await res.json()
      if (res.ok && data.success) onLoginSuccess(data.user)
      else setError(data.message || '로그인에 실패했습니다.')
    } catch { setError('서버에 연결할 수 없습니다.') }
    finally { setLoading(false) }
  }

  const reset = () => {
    setMode('initial')
    setForm({ username: '', email: '', password: '', passwordConfirm: '', nickname: '' })
    setSignupStep(0)
    setCreatedUser(null)
    setError('')
  }

  if (mode === 'initial') return (
    <div className={s.page}>
      <div className={s.card}>
        <img src="/images/main-logo.png" alt="토트" className={s.logo} />
        <h1 className={s.heroTitle}>Thoth</h1>
        <p className={s.heroSub}>경제 초보자를 위한 맞춤 뉴스 브리핑</p>
        <div className={s.heroButtons}>
          <button className={`${s.btn} ${s.primary}`} onClick={() => setMode('signup')}>시작하기</button>
          <button className={`${s.btn} ${s.outline}`} onClick={() => setMode('login')}>로그인</button>
        </div>
      </div>
    </div>
  )

  if (mode === 'signup') {
    if (createdUser) return (
      <div className={s.page}>
        <div className={`${s.stepCard} ${s.authCard}`}>
          <div className={s.progressBar}><div className={s.progressFill} style={{ width: '100%' }} /></div>
          <div className={`${s.stepArea} ${s.complete}`}>
            <div className={s.completeIcon}>✓</div>
            <h2 className={s.question}>가입이 완료됐어요!</h2>
            <p className={s.description}>{createdUser.nickname}님, 병아리 경제 뉴스와 함께 시작해요.</p>
            <button className={`${s.btn} ${s.primary} ${s.fixedBtn}`} onClick={() => onLoginSuccess(createdUser)}>시작하기</button>
          </div>
        </div>
      </div>
    )

    const [name, question, description, type, placeholder] = fields[signupStep]
    const valid = !validate(name, form[name], form)
    return (
      <div className={s.page}>
        <div className={`${s.stepCard} ${s.authCard}`}>
          <div className={s.progressBar}><div className={s.progressFill} style={{ width: `${((signupStep + 1) / 6) * 100}%` }} /></div>
          <form className={s.stepArea} key={name} onSubmit={nextSignupStep}>
            <p className={s.stepCount}>{signupStep + 1} / 5</p>
            <h2 className={s.question}>{question}</h2>
            <p className={s.description}>{description}</p>
            <div className={s.inputWrap}>
              <input className={s.input} name={name} type={type} placeholder={placeholder} autoFocus
                autoComplete={name === 'passwordConfirm' ? 'new-password' : name === 'password' ? 'new-password' : name}
                value={form[name]} onChange={e => update(name, e.target.value)} />
            </div>
            {error && <p className={s.error}>{error}</p>}
            <button className={`${s.btn} ${s.primary} ${s.fixedBtn}`} disabled={loading || !valid}>
              {loading ? '확인 중...' : signupStep === fields.length - 1 ? '가입 완료' : '다음'}
            </button>
            <button type="button" className={s.backButton}
              onClick={() => { setError(''); signupStep ? setSignupStep(step => step - 1) : reset() }}>이전으로</button>
          </form>
        </div>
      </div>
    )
  }

  const visibleFields = fields.filter(([name]) => ['username', 'password'].includes(name))
  return (
    <div className={s.page}>
      <div className={`${s.stepCard} ${s.authCard}`}>
        <div className={s.progressBar}><div className={s.progressFill} style={{ width: '100%' }} /></div>
        <form className={s.stepArea} onSubmit={submit}>
          <h2 className={s.question}>{mode === 'login' ? '아이디로 로그인해요' : '간단히 회원가입해요'}</h2>
          <div className={s.formFields}>
            {visibleFields.map(([name, , , type, placeholder], index) => (
              <label className={s.field} key={name}>
                <span>{name === 'username' ? '아이디' : '비밀번호'}</span>
                <div className={s.inputWrap}>
                  <input className={s.input} name={name} type={type} placeholder={placeholder}
                    autoComplete={name === 'passwordConfirm' ? 'new-password' : name === 'password' ? (mode === 'login' ? 'current-password' : 'new-password') : name}
                    autoFocus={index === 0} value={form[name]} required
                    minLength={name === 'password' ? 8 : undefined}
                    onChange={e => update(name, e.target.value)} />
                </div>
              </label>
            ))}
          </div>
          {error && <p className={s.error}>{error}</p>}
          <button className={`${s.btn} ${s.primary} ${s.fixedBtn}`} disabled={loading}>
            {loading ? '처리 중...' : mode === 'login' ? '로그인' : '가입 완료'}
          </button>
          <p className={s.back} onClick={reset}>이전으로</p>
        </form>
      </div>
    </div>
  )
}
