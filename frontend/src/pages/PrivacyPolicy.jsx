import { useEffect } from 'react'
import Footer from '../components/Footer'
import s from './PrivacyPolicy.module.css'

const sections = [
  ['overview', '1. 총칙'],
  ['collection', '2. 수집하는 개인정보'],
  ['purpose', '3. 개인정보의 이용 목적'],
  ['retention', '4. 보유 및 이용 기간'],
  ['sharing', '5. 제3자 제공'],
  ['outsourcing', '6. 처리 위탁 및 외부 서비스'],
  ['destruction', '7. 개인정보의 파기'],
  ['rights', '8. 이용자의 권리'],
  ['cookies', '9. 쿠키 및 자동 수집 정보'],
  ['security', '10. 안전성 확보 조치'],
  ['children', '11. 만 14세 미만 아동'],
  ['contact', '12. 개인정보 보호 문의'],
  ['changes', '13. 방침의 변경'],
]

export default function PrivacyPolicy() {
  useEffect(() => {
    const previousTitle = document.title
    const existingDescription = document.querySelector('meta[name="description"]')
    const previousDescription = existingDescription?.getAttribute('content')
    const description = existingDescription || document.createElement('meta')

    document.title = '개인정보처리방침 | 병아리 경제 뉴스'
    description.setAttribute('name', 'description')
    description.setAttribute('content', '병아리 경제 뉴스 서비스의 개인정보 수집, 이용 목적, 보유 기간, 이용자 권리 및 보호 조치를 안내합니다.')
    if (!existingDescription) document.head.appendChild(description)

    return () => {
      document.title = previousTitle
      if (existingDescription) description.setAttribute('content', previousDescription || '')
      else description.remove()
    }
  }, [])

  return (
    <div className={s.page}>
      <header className={s.header}>
        <a className={s.brand} href="/" aria-label="병아리 경제 뉴스 홈으로 이동">
          <img src="/images/main-logo.png" alt="" />
          <span>병아리 경제 뉴스</span>
        </a>
      </header>

      <main className={s.main}>
        <section className={s.hero}>
          <span className={s.eyebrow}>PRIVACY POLICY</span>
          <h1>개인정보처리방침</h1>
          <p>병아리 경제 뉴스는 이용자의 개인정보를 소중히 여기며 안전하게 보호하기 위해 노력합니다.</p>
          <p className={s.updated}>시행일 및 최종 수정일: 2026년 8월 6일</p>
        </section>

        <div className={s.contentGrid}>
          <nav className={s.toc} aria-label="개인정보처리방침 목차">
            <h2>목차</h2>
            <ol>
              {sections.map(([id, title]) => <li key={id}><a href={`#${id}`}>{title}</a></li>)}
            </ol>
          </nav>

          <article className={s.policy}>
            <section id="overview">
              <h2>1. 총칙</h2>
              <p>병아리 경제 뉴스(이하 “서비스”)는 경제 뉴스 조회와 AI 기반 뉴스 요약을 제공하는 서비스입니다. 서비스 운영자는 「개인정보 보호법」 등 관계 법령을 준수하며, 이 방침을 통해 개인정보가 어떤 목적으로 수집·이용되고 어떻게 보호되는지 안내합니다.</p>
            </section>

            <section id="collection">
              <h2>2. 수집하는 개인정보</h2>
              <div className={s.tableWrap}>
                <table>
                  <thead><tr><th>구분</th><th>수집 항목</th><th>수집 방법</th></tr></thead>
                  <tbody>
                    <tr><td>회원가입·로그인</td><td>아이디, 이메일 주소, 비밀번호(단방향 암호화), 닉네임</td><td>이용자가 회원가입 과정에서 직접 입력</td></tr>
                    <tr><td>서비스 이용</td><td>회원 식별값, 가입일, 최근 로그인 일시, 뉴스 열람 기록</td><td>서비스 이용 과정에서 생성</td></tr>
                    <tr><td>자동 수집</td><td>IP 주소, 접속 일시, 브라우저·기기 정보, 쿠키, 서비스 이용 및 오류 기록</td><td>웹·앱 이용 과정에서 자동 생성·수집</td></tr>
                    <tr><td>문의</td><td>이메일 주소 및 문의 내용에 이용자가 포함한 정보</td><td>이메일 문의 시 수집</td></tr>
                  </tbody>
                </table>
              </div>
              <p className={s.note}>이메일 주소는 암호화하여 보관하며, 중복 확인과 계정 조회에는 정규화된 이메일의 별도 해시값을 사용합니다. 비밀번호 확인값은 저장하지 않습니다.</p>
            </section>

            <section id="purpose">
              <h2>3. 개인정보의 이용 목적</h2>
              <ul>
                <li>회원가입 의사 확인, 회원 식별, 로그인 및 이메일을 이용한 비밀번호 재설정 제공</li>
                <li>경제 뉴스, AI 뉴스 요약 및 회원 맞춤 기능 제공</li>
                <li>부정 이용 방지, 서비스 안정성 확보 및 오류 분석</li>
                <li>문의·민원 처리와 처리 결과 안내</li>
                <li>이용 통계 분석 및 서비스 품질 개선</li>
              </ul>
            </section>

            <section id="retention">
              <h2>4. 보유 및 이용 기간</h2>
              <p>개인정보는 원칙적으로 회원 탈퇴 또는 수집·이용 목적 달성 시 지체 없이 삭제합니다. 다만 관계 법령에 따라 보관해야 하거나 분쟁 처리에 필요한 경우에는 해당 기간 동안 분리하여 보관한 뒤 파기합니다.</p>
              <div className={s.tableWrap}>
                <table>
                  <thead><tr><th>보관 정보</th><th>보관 기간</th><th>근거</th></tr></thead>
                  <tbody>
                    <tr><td>소비자의 불만 또는 분쟁 처리 기록</td><td>3년</td><td>전자상거래 등에서의 소비자보호에 관한 법률(해당 시)</td></tr>
                    <tr><td>웹사이트 접속 기록</td><td>3개월</td><td>통신비밀보호법</td></tr>
                  </tbody>
                </table>
              </div>
              <p>회원 탈퇴 시 아이디, 암호화된 이메일, 이메일 조회용 해시, 비밀번호 해시, 닉네임, 프로필 정보, 가입·로그인 정보, 뉴스 열람 기록 및 인증 토큰은 즉시 삭제합니다. 접속 기록은 최대 3개월, 문의·분쟁 처리 이메일은 관계 법령이 적용되는 경우 최대 3년간 계정 정보와 분리하여 보관한 뒤 파기합니다.</p>
            </section>

            <section id="sharing">
              <h2>5. 제3자 제공</h2>
              <p>서비스는 이용자의 개인정보를 원칙적으로 제3자에게 제공하지 않습니다. 다만 이용자가 사전에 동의한 경우, 법령에 특별한 규정이 있거나 수사기관 등이 적법한 절차에 따라 요청한 경우, 생명이나 신체의 급박한 위험을 해소하기 위해 필요한 경우에는 예외로 합니다.</p>
              <p>서비스 인프라 운영을 위해 외부 업체가 정보를 처리하는 경우는 아래의 처리 위탁에 해당하며, 그 목적 외로 이용하지 않도록 관리·감독합니다.</p>
            </section>

            <section id="outsourcing">
              <h2>6. 처리 위탁 및 외부 서비스</h2>
              <div className={s.tableWrap}>
                <table>
                  <thead><tr><th>수탁자·서비스</th><th>업무 및 처리 정보</th><th>보유 기간</th></tr></thead>
                  <tbody>
                    <tr><td>OpenAI API</td><td>경제 뉴스의 분류·요약·분석(뉴스 기사 데이터)</td><td>서비스 제공에 필요한 기간 또는 제공자의 정책에 따른 기간</td></tr>
                  </tbody>
                </table>
              </div>
              <p>OpenAI API에는 뉴스 기사 데이터만 전송하며, 아이디·이메일 주소·비밀번호·닉네임 등 회원 개인정보를 의도적으로 전송하지 않습니다. 해외 사업자가 개인정보를 처리하게 되는 기능을 추가하는 경우에는 이전 국가, 이전 시기와 방법, 수령자, 이용 목적과 보유 기간 및 이전 거부 방법을 관계 법령에 따라 별도로 고지하고 필요한 동의를 받겠습니다.</p>
              <p>서비스는 자체 Spring Boot 서버와 데이터베이스를 사용하며, 안정적인 제공을 위해 호스팅·네트워크 등 클라우드 또는 인프라 서비스를 이용할 수 있습니다. 개인정보 처리 위탁 업체가 추가되거나 변경되면 이 방침을 통해 공개합니다.</p>
            </section>

            <section id="destruction">
              <h2>7. 개인정보의 파기</h2>
              <p>보유 기간이 지나거나 처리 목적이 달성된 개인정보는 복구 또는 재생되지 않도록 지체 없이 파기합니다. 전자 파일은 복구하기 어려운 방식으로 삭제하고, 종이 문서가 있는 경우에는 분쇄하거나 소각합니다. 법령에 따라 계속 보관해야 하는 정보는 다른 개인정보와 분리하여 보관합니다.</p>
              <p>회원은 앱 내부의 계정 관리 메뉴 또는 공개 <a href="/delete-account">계정 삭제 페이지</a>에서 본인 확인 후 탈퇴할 수 있습니다. 계정과 연결된 개인정보 및 뉴스 열람 기록은 확인 즉시 삭제되며, 삭제된 계정은 복구할 수 없습니다.</p>
            </section>

            <section id="rights">
              <h2>8. 이용자의 권리</h2>
              <p>이용자는 자신의 개인정보에 대해 열람, 정정, 삭제, 처리정지 및 동의 철회를 요구할 수 있으며 언제든지 회원 탈퇴를 요청할 수 있습니다. 앱 내부 계정 관리 또는 공개 계정 삭제 페이지에서 아이디와 비밀번호로 본인을 확인하면 즉시 처리됩니다. 비밀번호를 잊은 경우 등록한 이메일 주소에서 아래 이메일로 요청해 주세요.</p>
              <p>법정대리인이나 위임받은 사람도 적법한 위임 관계를 확인한 뒤 권리를 행사할 수 있습니다. 법령에 따라 요청이 제한되는 경우에는 그 사유를 안내합니다.</p>
            </section>

            <section id="cookies">
              <h2>9. 쿠키 및 자동 수집 정보</h2>
              <p>서비스는 로그인 상태 유지, 보안 및 안정적인 서비스 제공을 위해 쿠키(세션 쿠키 포함)를 사용할 수 있습니다. 이용자는 브라우저 또는 기기 설정에서 쿠키 저장을 거부하거나 삭제할 수 있으나, 이 경우 로그인 등 일부 기능 이용이 제한될 수 있습니다.</p>
            </section>

            <section id="security">
              <h2>10. 안전성 확보 조치</h2>
              <ul>
                <li>HTTPS를 적용하여 네트워크 구간에서 전송되는 정보를 보호합니다.</li>
                <li>개인정보 및 인증 정보에 대한 접근 권한을 업무상 필요한 범위로 제한합니다.</li>
                <li>비밀번호는 안전한 단방향 해시로, 이메일은 복호화 가능한 암호화 방식으로 저장하고 암호화 키를 데이터와 분리합니다.</li>
                <li>접속 기록 관리, 보안 업데이트, 이상 접근 점검 등 기술적·관리적 보호 조치를 시행합니다.</li>
              </ul>
            </section>

            <section id="children">
              <h2>11. 만 14세 미만 아동</h2>
              <p>서비스는 만 14세 미만 아동을 대상으로 회원가입을 제공하지 않으며, 법정대리인의 동의 없이 만 14세 미만 아동의 개인정보를 수집하지 않습니다. 해당 정보가 수집된 사실을 알게 된 경우 확인 후 지체 없이 삭제합니다.</p>
            </section>

            <section id="contact">
              <h2>12. 개인정보 보호 문의</h2>
              <p>개인정보 처리와 관련한 문의, 불만 처리, 권리 행사 및 회원 탈퇴 요청은 아래 연락처로 보내 주세요.</p>
              <div className={s.contactBox}>
                <strong>개인정보 보호 담당자</strong>
                <span>Thoth(토트) 운영자 권미경</span>
                <a href="mailto:zxc_777@naver.com">zxc_777@naver.com</a>
              </div>
              <p>개인정보 침해에 대한 상담이 필요한 경우 개인정보침해신고센터(국번 없이 118), 개인정보분쟁조정위원회(1833-6972) 등 관계 기관에 도움을 요청할 수 있습니다.</p>
            </section>

            <section id="changes">
              <h2>13. 방침의 변경</h2>
              <p>이 방침의 내용이 변경되는 경우 시행 전에 서비스 내 공지사항 또는 이 페이지를 통해 변경 내용과 시행일을 안내합니다. 이용자 권리에 중대한 변경이 있는 경우에는 관계 법령에 따라 필요한 절차를 진행합니다.</p>
            </section>
          </article>
        </div>
      </main>
      <Footer />
    </div>
  )
}
