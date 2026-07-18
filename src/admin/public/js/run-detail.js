let runId = null;
let pollingInterval = null;

function getRunId() {
  const params = new URLSearchParams(window.location.search);
  return params.get('id');
}

async function init() {
  if (!requireAuth()) return;
  runId = getRunId();
  if (!runId) {
    showError(document.getElementById('content'), '실행 ID가 지정되지 않았습니다.');
    return;
  }
  await loadRun();
  await Promise.all([loadLogs(), loadItems()]);
}

async function refresh() {
  await loadRun();
  await Promise.all([loadLogs(), loadItems()]);
}

async function loadRun() {
  const container = document.getElementById('content');
  try {
    const res = await getRun(runId);
    const r = res.data;

    container.innerHTML = `
      <div class="card">
        <div class="detail-grid">
          <div class="detail-item">
            <div class="label">실행 ID</div>
            <div class="value"><code>${escapeHtml(r.id)}</code></div>
          </div>
          <div class="detail-item">
            <div class="label">상태</div>
            <div class="value">${statusBadge(r.status)}</div>
          </div>
          <div class="detail-item">
            <div class="label">실행 방식</div>
            <div class="value">${triggerTypeName(r.triggerType)}</div>
          </div>
          <div class="detail-item">
            <div class="label">현재 단계</div>
            <div class="value">${escapeHtml(r.currentStep)}</div>
          </div>
          <div class="detail-item">
            <div class="label">시작 시각</div>
            <div class="value">${formatDateTime(r.startedAt)}</div>
          </div>
          <div class="detail-item">
            <div class="label">종료 시각</div>
            <div class="value">${formatDateTime(r.finishedAt)}</div>
          </div>
          <div class="detail-item">
            <div class="label">소요시간</div>
            <div class="value">${formatDuration(r.durationMs)}</div>
          </div>
          <div class="detail-item">
            <div class="label">수집</div>
            <div class="value">${r.collectedCount}건</div>
          </div>
          <div class="detail-item">
            <div class="label">중복 제외</div>
            <div class="value">${r.duplicateCount}건</div>
          </div>
          <div class="detail-item">
            <div class="label">AI 분석</div>
            <div class="value" style="color:var(--color-success)">${r.analysisSuccessCount} 성공</div>
          </div>
          <div class="detail-item">
            <div class="label">AI 실패</div>
            <div class="value" style="color:var(--color-danger)">${r.analysisFailureCount} 실패</div>
          </div>
          <div class="detail-item">
            <div class="label">발행</div>
            <div class="value">${r.publishSuccessCount} 성공 / ${r.publishFailureCount} 실패</div>
          </div>
        </div>
        ${r.errorCode ? `
          <div style="margin-top: 16px;">
            <div class="alert alert-error">
              <strong>${escapeHtml(r.errorCode)}</strong>: ${escapeHtml(r.errorMessage || '')}
            </div>
          </div>
        ` : ''}
      </div>
    `;

    // Polling for running
    if (r.status === 'RUNNING' && !pollingInterval) {
      pollingInterval = setInterval(refresh, 5000);
    } else if (r.status !== 'RUNNING' && pollingInterval) {
      clearInterval(pollingInterval);
      pollingInterval = null;
    }
  } catch (err) {
    if (err.code === 'RUN_NOT_FOUND') {
      showError(container, '실행 기록을 찾을 수 없습니다.');
    } else {
      showError(container, '데이터 조회 실패: ' + (err.message || '알 수 없는 오류'));
    }
  }
}

async function loadLogs() {
  const section = document.getElementById('logs-section');
  const container = document.getElementById('logs-content');
  if (!runId) return;

  try {
    const level = document.getElementById('log-level').value || undefined;
    const step = document.getElementById('log-step').value || undefined;
    const res = await getRunLogs(runId, { level, step });
    const logs = res.data;
    section.style.display = 'block';

    if (logs.length === 0) {
      container.innerHTML = '<p style="color:var(--color-text-secondary);padding:20px;text-align:center">로그가 없습니다.</p>';
      return;
    }

    container.innerHTML = '<div class="timeline">' + logs.map(l => {
      const cls = l.level === 'ERROR' ? 'error' : l.level === 'WARN' ? 'warning' : '';
      let metaHtml = '';
      if (l.metadataJson) {
        const metaStr = JSON.stringify(l.metadataJson, null, 2);
        metaHtml = `<button class="metadata-toggle" onclick="this.nextElementSibling.style.display=this.nextElementSibling.style.display==='none'?'block':'none'">메타데이터 보기</button><div class="metadata-box" style="display:none">${escapeHtml(metaStr)}</div>`;
      }
      return `
        <div class="timeline-item ${cls}">
          <div class="time">${formatDateTime(l.createdAt)} | ${l.step} | ${statusBadge(l.level === 'ERROR' ? 'FAILED' : l.level === 'WARN' ? 'PARTIAL_SUCCESS' : 'SUCCESS')}</div>
          <div class="message">${escapeHtml(l.message)}</div>
          ${metaHtml}
        </div>
      `;
    }).join('') + '</div>';
  } catch (err) {
    container.innerHTML = `<div class="alert alert-error">로그 조회 실패</div>`;
  }
}

async function loadItems() {
  const section = document.getElementById('items-section');
  const tbody = document.getElementById('items-body');
  if (!runId) return;

  try {
    const analysisStatus = document.getElementById('item-analysis').value || undefined;
    const publishStatus = document.getElementById('item-publish').value || undefined;
    const res = await getRunItems(runId, { analysisStatus, publishStatus });
    const items = res.data;
    section.style.display = 'block';

    if (items.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:20px;color:var(--color-text-secondary)">처리된 뉴스가 없습니다.</td></tr>';
      return;
    }

    tbody.innerHTML = items.map(i => `
      <tr onclick="window.location.href='/admin/item-detail.html?id=${i.id}'">
        <td>${i.id}</td>
        <td>${escapeHtml(i.originalTitle || '-')}</td>
        <td>${escapeHtml(i.source || '-')}</td>
        <td>${statusBadge(i.duplicateStatus === 'DUPLICATE' ? 'FAILED' : 'SUCCESS')}</td>
        <td>${statusBadge(i.analysisStatus)}</td>
        <td>${statusBadge(i.publishStatus)}</td>
      </tr>
    `).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6"><div class="alert alert-error">뉴스 조회 실패</div></td></tr>`;
  }
}

function logout() {
  clearToken();
  window.location.href = '/admin/login.html';
}

init();
