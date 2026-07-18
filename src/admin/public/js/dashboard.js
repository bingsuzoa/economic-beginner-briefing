let currentPage = 1;
let pollingInterval = null;

async function init() {
  if (!requireAuth()) return;
  await Promise.all([loadStatus(), loadRuns()]);
}

async function refresh() {
  await Promise.all([loadStatus(), loadRuns()]);
}

async function loadStatus() {
  try {
    const res = await getStatus();
    const d = res.data;

    document.getElementById('svc-status').textContent = d.dbConnected ? '정상' : 'DB 오류';
    document.getElementById('pipeline-status').innerHTML = d.pipelineRunning
      ? statusBadge('RUNNING')
      : '<span style="color: var(--color-text-secondary)">대기 중</span>';

    if (d.lastRun) {
      document.getElementById('last-run-time').textContent = formatDateTime(d.lastRun.startedAt);
      document.getElementById('last-run-status').innerHTML = statusBadge(d.lastRun.status);
    }

    // Start/stop polling based on running state
    if (d.pipelineRunning && !pollingInterval) {
      pollingInterval = setInterval(refresh, 5000);
    } else if (!d.pipelineRunning && pollingInterval) {
      clearInterval(pollingInterval);
      pollingInterval = null;
    }

    document.getElementById('runBtn').disabled = d.pipelineRunning;
  } catch (err) {
    showError(document.getElementById('alert-area'), '상태 조회 실패: ' + (err.message || '알 수 없는 오류'));
  }
}

async function loadRuns() {
  const tbody = document.getElementById('runs-body');
  const status = document.getElementById('filter-status').value;
  const triggerType = document.getElementById('filter-trigger').value;

  try {
    const res = await getRuns({ page: currentPage, size: 20, status: status || undefined, triggerType: triggerType || undefined });
    const { runs, total, page, size } = res.data;

    if (runs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="10" style="text-align:center; color: var(--color-text-secondary); padding: 40px;">실행 이력이 없습니다.</td></tr>';
      document.getElementById('pagination').innerHTML = '';
      return;
    }

    tbody.innerHTML = runs.map(r => `
      <tr onclick="window.location.href='/admin/run-detail.html?id=${escapeHtml(r.id)}'">
        <td><code style="font-size:0.8rem">${escapeHtml(r.id.slice(0, 16))}...</code></td>
        <td>${formatDateTime(r.startedAt)}</td>
        <td>${triggerTypeName(r.triggerType)}</td>
        <td>${statusBadge(r.status)}</td>
        <td>${formatDuration(r.durationMs)}</td>
        <td>${r.collectedCount}</td>
        <td>${r.analysisSuccessCount}</td>
        <td>${r.analysisFailureCount}</td>
        <td>${r.publishSuccessCount}</td>
        <td>${r.publishFailureCount}</td>
      </tr>
    `).join('');

    const totalPages = Math.ceil(total / size);
    document.getElementById('pagination').innerHTML = `
      <button class="btn btn-sm" onclick="goPage(${page - 1})" ${page <= 1 ? 'disabled' : ''}>이전</button>
      <span class="info">${page} / ${totalPages} (총 ${total}건)</span>
      <button class="btn btn-sm" onclick="goPage(${page + 1})" ${page >= totalPages ? 'disabled' : ''}>다음</button>
    `;
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="10"><div class="alert alert-error">이력 조회 실패: ${escapeHtml(err.message || '알 수 없는 오류')}</div></td></tr>`;
  }
}

function goPage(page) {
  if (page < 1) return;
  currentPage = page;
  loadRuns();
}

async function startRun() {
  const btn = document.getElementById('runBtn');
  btn.disabled = true;
  btn.textContent = '실행 중...';

  try {
    await triggerRun();
    document.getElementById('alert-area').innerHTML =
      '<div class="alert alert-info">파이프라인 실행이 시작되었습니다. 자동으로 상태가 갱신됩니다.</div>';
    setTimeout(() => { document.getElementById('alert-area').innerHTML = ''; }, 5000);
    await refresh();
  } catch (err) {
    if (err.code === 'PIPELINE_ALREADY_RUNNING') {
      document.getElementById('alert-area').innerHTML =
        '<div class="alert alert-error">파이프라인이 이미 실행 중입니다.</div>';
    } else {
      document.getElementById('alert-area').innerHTML =
        `<div class="alert alert-error">실행 실패: ${escapeHtml(err.message || '알 수 없는 오류')}</div>`;
    }
    setTimeout(() => { document.getElementById('alert-area').innerHTML = ''; }, 5000);
    btn.disabled = false;
    btn.textContent = '지금 실행';
  }
}

function logout() {
  clearToken();
  window.location.href = '/admin/login.html';
}

init();
