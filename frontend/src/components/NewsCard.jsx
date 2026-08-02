import { useRef, useEffect, useState } from 'react'
import s from './NewsCard.module.css'
import SummaryBox from './SummaryBox'
import FlowSection from './FlowSection'
import ImpactSection from './ImpactSection'
import TermsAccordion from './TermsAccordion'
import SourceList from './SourceList'

// Keys match the NewsCategory / NewsEvidenceStatus enums. Both serialise through @JsonValue as
// name().toLowerCase(), so the API always sends lowercase and lookups are normalised below --
// the previous uppercase-only lookup could never hit, and every badge rendered the raw code
// ('loan' instead of '대출'). The old ECONOMY / REAL_ESTATE / FINANCE keys were not enum values
// at all, and PROJECTED / UNCERTAIN are not evidence statuses; all of them are dropped.
const CATEGORY_LABELS = {
  INTEREST_RATE: '금리', DEPOSIT_SAVING: '예적금', LOAN: '대출',
  HOUSING: '주택', JEONSE_MONTHLY_RENT: '전월세', SUBSCRIPTION: '청약',
  TAX: '세금', PENSION: '연금', INSURANCE: '보험',
  COST_OF_LIVING: '생활물가', EXCHANGE_RATE: '환율', INVESTMENT: '투자',
  GOVERNMENT_SUPPORT: '정부지원', EMPLOYMENT_INCOME: '고용·소득',
  HOUSEHOLD_DEBT: '가계부채', OTHER: '기타',
}

const EVIDENCE_LABELS = {
  CONFIRMED: '확정', PROPOSED: '검토중', EXPECTED: '예상',
}

const labelFor = (map, value, fallback) =>
  (value && map[String(value).toUpperCase()]) || value || fallback

const formatRelativeTime = (timestamp) => {
  if (!timestamp) return ''
  const now = new Date()
  const readTime = new Date(timestamp)
  const diffMs = now - readTime
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '방금'
  if (diffMins < 60) return `${diffMins}분 전`
  if (diffHours < 24) {
    const hours = readTime.getHours()
    const mins = readTime.getMinutes()
    const period = hours < 12 ? '오전' : '오후'
    const displayHour = hours % 12 || 12
    return `오늘 ${period} ${displayHour}:${String(mins).padStart(2, '0')}`
  }
  if (diffDays === 1) return '어제'
  if (diffDays < 7) return `${diffDays}일 전`
  return readTime.toLocaleDateString('ko-KR')
}

const markAsRead = async (articleId) => {
  try {
    await fetch(`/api/briefing/articles/${articleId}/mark-read`, { method: 'POST' })
  } catch (err) {
    // Silent fail - reading history is not critical
  }
}

export default function NewsCard({ news, onMarkRead }) {
  const evidenceStatus = news.evidenceStatus || 'EXPECTED'
  const evidenceClass = s[evidenceStatus.toLowerCase()] || ''
  const summary = news.threeLineSummary || []
  const affectedPeople = news.affectedPeople || []
  const uncertainties = news.uncertainties || []
  const actionItems = news.actionItems || []
  const terms = news.terms || []
  const sources = news.sources || []

  const cardRef = useRef(null)
  const [isRead, setIsRead] = useState(!!news.readAt)
  const [readAt, setReadAt] = useState(news.readAt)
  const visibilityTimerRef = useRef(null)

  useEffect(() => {
    // Skip if already read
    if (isRead) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          // Start 3-second timer when card enters viewport
          visibilityTimerRef.current = setTimeout(async () => {
            const now = new Date().toISOString()
            setIsRead(true)
            setReadAt(now)
            await markAsRead(news.articleId)
            if (onMarkRead) onMarkRead(news.articleId, now)
          }, 3000)
        } else {
          // Clear timer if card leaves viewport before 3 seconds
          if (visibilityTimerRef.current) {
            clearTimeout(visibilityTimerRef.current)
            visibilityTimerRef.current = null
          }
        }
      },
      { threshold: 0.5 } // 50% of card must be visible
    )

    if (cardRef.current) {
      observer.observe(cardRef.current)
    }

    return () => {
      if (visibilityTimerRef.current) {
        clearTimeout(visibilityTimerRef.current)
      }
      observer.disconnect()
    }
  }, [news.articleId, isRead, onMarkRead])

  return (
    <article
      ref={cardRef}
      className={`${s.card} ${isRead ? s.read : ''}`}
      id={news.articleId || news.id}>
      <div className={s.header}>
        {isRead && readAt && (
          <span className={s.readBadge}>
            읽음 · {formatRelativeTime(readAt)}
          </span>
        )}
        <span className={s.category}>{labelFor(CATEGORY_LABELS, news.category, '기타')}</span>
        <span className={`${s.evidence} ${evidenceClass}`}>
          {labelFor(EVIDENCE_LABELS, evidenceStatus, '예상')}
        </span>
        {news.teacherLabel && (
          <span className={s.teacherBadge}>
            Teacher: {news.teacherLabel}
            {news.teacherConfidence != null && ` (${(news.teacherConfidence * 100).toFixed(0)}%)`}
          </span>
        )}
      </div>

      <h3 className={s.title}>{news.easyTitle || news.originalTitle || '제목 없음'}</h3>

      {summary.length > 0 && <SummaryBox lines={summary} />}

      {news.whatHappened && <FlowSection icon="📰" heading="무슨 일이 있었나요?">{news.whatHappened}</FlowSection>}
      {news.whyItHappened && <FlowSection icon="🤔" heading="왜 이런 일이 생겼나요?">{news.whyItHappened}</FlowSection>}
      {news.economicImpact && <FlowSection icon="📊" heading="경제에 어떤 영향이 있나요?">{news.economicImpact}</FlowSection>}
      {news.householdImpact && <FlowSection icon="🏠" heading="우리 가정에는요?">{news.householdImpact}</FlowSection>}

      {affectedPeople.length > 0 && (
        <div className={s.affected}>
          {affectedPeople.map((p) => <span key={p} className={s.tag}>{p}</span>)}
        </div>
      )}

      {(news.positiveImpact || news.negativeImpact) && (
        <ImpactSection
          positive={news.positiveImpact || '정보 없음'}
          negative={news.negativeImpact || '정보 없음'}
        />
      )}

      {uncertainties.length > 0 && (
        <div className={s.uncertainties}>
          <strong>⚠️ 아직 확정되지 않은 부분</strong>
          {uncertainties.map((u, i) => <div key={i}>{u}</div>)}
        </div>
      )}

      {actionItems.length > 0 && (
        <div className={s.actions}>
          <h4>✅ 체크해볼 것</h4>
          <ul>
            {actionItems.map((a, i) => <li key={i}>{a}</li>)}
          </ul>
        </div>
      )}

      {terms.length > 0 && <TermsAccordion terms={terms} />}

      <SourceList
        sources={sources}
        originalUrl={news.originalUrl}
        sourceName={news.sourceName}
        originalTitle={news.originalTitle}
        author={news.author}
        publishedAt={news.publishedAt}
        modelName={news.modelName}
        analyzedAt={news.analyzedAt}
      />
    </article>
  )
}
