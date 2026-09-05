import s from './SourceList.module.css'

export default function SourceList({ originalUrl, sourceName, originalTitle, author, publishedAt, modelName, analyzedAt }) {
  return (
    <div className={s.section}>
      <div className={s.title}>출처 및 정보</div>

      {/* Original article source */}
      {originalUrl && (
        <div className={s.original}>
          <div className={s.row}>
            <span className={s.label}>언론사:</span> {sourceName || '알 수 없음'}
          </div>
          {originalTitle && (
            <div className={s.row}>
              <span className={s.label}>원문 제목:</span> {originalTitle}
            </div>
          )}
          {author && (
            <div className={s.row}>
              <span className={s.label}>기자:</span> {author}
            </div>
          )}
          {publishedAt && (
            <div className={s.row}>
              <span className={s.label}>발행:</span> {new Date(publishedAt).toLocaleString('ko-KR')}
            </div>
          )}
          <div className={s.row}>
            <span className={s.label}>원문:</span>{' '}
            <a href={originalUrl} target="_blank" rel="noopener noreferrer">{originalUrl}</a>
          </div>
          {modelName && (
            <div className={s.row}>
              <span className={s.label}>분석 모델:</span> {modelName}
            </div>
          )}
          {analyzedAt && (
            <div className={s.row}>
              <span className={s.label}>분석 시각:</span> {new Date(analyzedAt).toLocaleString('ko-KR')}
            </div>
          )}
        </div>
      )}

    </div>
  )
}
