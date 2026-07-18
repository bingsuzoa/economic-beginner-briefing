function formatDateTime(dateStr) {
  if (!dateStr) return '-';
  const d = new Date(dateStr);
  return d.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' });
}

function formatDuration(ms) {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms}ms`;
  const s = Math.floor(ms / 1000);
  if (s < 60) return `${s}초`;
  const m = Math.floor(s / 60);
  const rs = s % 60;
  return `${m}분 ${rs}초`;
}

function statusBadge(status) {
  const map = {
    'RUNNING': { cls: 'badge-running', icon: '⟳', text: '실행 중' },
    'SUCCESS': { cls: 'badge-success', icon: '✓', text: '성공' },
    'PARTIAL_SUCCESS': { cls: 'badge-partial', icon: '△', text: '부분 성공' },
    'FAILED': { cls: 'badge-failed', icon: '✕', text: '실패' },
    'PENDING': { cls: 'badge-pending', icon: '○', text: '대기' },
    'SKIPPED': { cls: 'badge-skipped', icon: '-', text: '건너뜀' },
  };
  const info = map[status] || { cls: 'badge-pending', icon: '?', text: status };
  return `<span class="badge ${info.cls}">${info.icon} ${info.text}</span>`;
}

function triggerTypeName(type) {
  const map = {
    'MANUAL': '수동 실행',
    'SCHEDULER': '스케줄러',
    'GITHUB_ACTIONS': 'GitHub Actions',
    'LOCAL': '로컬',
  };
  return map[type] || type;
}

function escapeHtml(str) {
  if (!str) return '';
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function showError(container, message) {
  container.innerHTML = `<div class="alert alert-error">${escapeHtml(message)}</div>`;
}

function showLoading(container) {
  container.innerHTML = '<div class="loading"><div class="spinner"></div><p>로딩 중...</p></div>';
}

function showEmpty(container, message) {
  container.innerHTML = `<div class="empty-state"><div class="icon">📋</div><p>${escapeHtml(message)}</p></div>`;
}
