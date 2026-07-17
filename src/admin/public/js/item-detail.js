async function init() {
  if (!requireAuth()) return;
  const params = new URLSearchParams(window.location.search);
  const itemId = params.get('id');
  if (!itemId) {
    showError(document.getElementById('content'), '뉴스 ID가 지정되지 않았습니다.');
    return;
  }
  await loadItem(itemId);
}

async function loadItem(itemId) {
  const container = document.getElementById('content');
  try {
    const res = await getItem(itemId);
    const i = res.data;

    let analysisHtml = '<p style="color:var(--color-text-secondary)">AI 분석 결과가 없습니다.</p>';
    if (i.analysisResultJson) {
      const r = i.analysisResultJson;
      analysisHtml = `
        <div class="detail-grid">
          ${r.representativeTitle ? `<div class="detail-item" style="grid-column:1/-1"><div class="label">AI 제목</div><div class="value">${escapeHtml(r.representativeTitle)}</div></div>` : ''}
          ${r.oneLineSummary ? `<div class="detail-item" style="grid-column:1/-1"><div class="label">한줄 요약</div><div class="value">${escapeHtml(r.oneLineSummary)}</div></div>` : ''}
          ${r.whatHappened ? `<div class="detail-item" style="grid-column:1/-1"><div class="label">무슨 일이 발생했는지</div><div class="value">${escapeHtml(r.whatHappened)}</div></div>` : ''}
          ${r.previousSituation ? `<div class="detail-item" style="grid-column:1/-1"><div class="label">기존 상황</div><div class="value">${escapeHtml(r.previousSituation)}</div></div>` : ''}
          ${r.whatChanged ? `<div class="detail-item" style="grid-column:1/-1"><div class="label">변경사항</div><div class="value">${escapeHtml(r.whatChanged)}</div></div>` : ''}
          ${r.whyItChanged ? `<div class="detail-item" style="grid-column:1/-1"><div class="label">변경 이유</div><div class="value">${escapeHtml(r.whyItChanged)}</div></div>` : ''}
          ${r.householdImpact ? `<div class="detail-item"><div class="label">가정 영향</div><div class="value">${escapeHtml(r.householdImpact)}</div></div>` : ''}
          ${r.newlywedHousingImpact ? `<div class="detail-item"><div class="label">신혼부부 영향</div><div class="value">${escapeHtml(r.newlywedHousingImpact)}</div></div>` : ''}
          ${r.importance ? `<div class="detail-item"><div class="label">중요도</div><div class="value">${r.importance}/5</div></div>` : ''}
          ${r.category ? `<div class="detail-item"><div class="label">카테고리</div><div class="value">${escapeHtml(r.category)}</div></div>` : ''}
        </div>
        <button class="metadata-toggle" style="margin-top:12px" onclick="this.nextElementSibling.style.display=this.nextElementSibling.style.display==='none'?'block':'none'">전체 JSON 보기</button>
        <div class="metadata-box" style="display:none">${escapeHtml(JSON.stringify(i.analysisResultJson, null, 2))}</div>
      `;
    }

    container.innerHTML = `
      <!-- 원문 영역 -->
      <div class="card">
        <div class="card-title">원문 정보</div>
        <div class="detail-grid">
          <div class="detail-item" style="grid-column:1/-1">
            <div class="label">제목</div>
            <div class="value">${escapeHtml(i.originalTitle || '-')}</div>
          </div>
          <div class="detail-item">
            <div class="label">출처</div>
            <div class="value">${escapeHtml(i.source || '-')}</div>
          </div>
          <div class="detail-item">
            <div class="label">발행일</div>
            <div class="value">${formatDateTime(i.publishedAt)}</div>
          </div>
          <div class="detail-item">
            <div class="label">카테고리</div>
            <div class="value">${escapeHtml(i.category || '-')}</div>
          </div>
          <div class="detail-item">
            <div class="label">중복 상태</div>
            <div class="value">${statusBadge(i.duplicateStatus === 'DUPLICATE' ? 'FAILED' : 'SUCCESS')}</div>
          </div>
          ${i.articleUrl ? `<div class="detail-item" style="grid-column:1/-1"><div class="label">원문 URL</div><div class="value"><a href="${escapeHtml(i.articleUrl)}" target="_blank" rel="noopener">${escapeHtml(i.articleUrl)}</a></div></div>` : ''}
          ${i.originalSummary ? `<div class="detail-item" style="grid-column:1/-1"><div class="label">요약</div><div class="value">${escapeHtml(i.originalSummary)}</div></div>` : ''}
        </div>
      </div>

      <!-- AI 분석 영역 -->
      <div class="card">
        <div class="card-title">AI 분석 결과 ${statusBadge(i.analysisStatus)}</div>
        ${i.analysisErrorMessage ? `<div class="alert alert-error" style="margin-bottom:12px">${escapeHtml(i.analysisErrorMessage)}</div>` : ''}
        ${analysisHtml}
      </div>

      <!-- Notion 영역 -->
      <div class="card">
        <div class="card-title">Notion 발행 ${statusBadge(i.publishStatus)}</div>
        <div class="detail-grid">
          <div class="detail-item">
            <div class="label">발행 상태</div>
            <div class="value">${statusBadge(i.publishStatus)}</div>
          </div>
          <div class="detail-item">
            <div class="label">Notion Page ID</div>
            <div class="value">${escapeHtml(i.notionPageId || '-')}</div>
          </div>
          ${i.notionPageUrl ? `<div class="detail-item" style="grid-column:1/-1"><div class="label">Notion URL</div><div class="value"><a href="${escapeHtml(i.notionPageUrl)}" target="_blank" rel="noopener">${escapeHtml(i.notionPageUrl)}</a></div></div>` : ''}
          ${i.publishErrorMessage ? `<div class="detail-item" style="grid-column:1/-1"><div class="label">오류</div><div class="value"><div class="alert alert-error">${escapeHtml(i.publishErrorMessage)}</div></div></div>` : ''}
        </div>
      </div>
    `;
  } catch (err) {
    if (err.code === 'ITEM_NOT_FOUND') {
      showError(container, '뉴스 항목을 찾을 수 없습니다.');
    } else {
      showError(container, '데이터 조회 실패: ' + (err.message || '알 수 없는 오류'));
    }
  }
}

function logout() {
  clearToken();
  window.location.href = '/admin/login.html';
}

init();
