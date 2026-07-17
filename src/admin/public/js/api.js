const API_BASE = '/api/admin';

function getToken() {
  return sessionStorage.getItem('admin_token') || '';
}

function setToken(token) {
  sessionStorage.setItem('admin_token', token);
}

function clearToken() {
  sessionStorage.removeItem('admin_token');
}

function isAuthenticated() {
  return !!getToken();
}

function requireAuth() {
  if (!isAuthenticated()) {
    window.location.href = '/admin/login.html';
    return false;
  }
  return true;
}

async function apiFetch(path, options = {}) {
  const token = getToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    clearToken();
    window.location.href = '/admin/login.html';
    throw new Error('Unauthorized');
  }

  const data = await response.json();

  if (!response.ok) {
    throw { status: response.status, ...data };
  }

  return data;
}

async function getStatus() {
  return apiFetch('/status');
}

async function getRuns(params = {}) {
  const query = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '' && v !== null) query.set(k, String(v));
  }
  const qs = query.toString();
  return apiFetch(`/runs${qs ? '?' + qs : ''}`);
}

async function getRun(runId) {
  return apiFetch(`/runs/${runId}`);
}

async function getRunLogs(runId, params = {}) {
  const query = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '' && v !== null) query.set(k, String(v));
  }
  const qs = query.toString();
  return apiFetch(`/runs/${runId}/logs${qs ? '?' + qs : ''}`);
}

async function getRunItems(runId, params = {}) {
  const query = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '' && v !== null) query.set(k, String(v));
  }
  const qs = query.toString();
  return apiFetch(`/runs/${runId}/items${qs ? '?' + qs : ''}`);
}

async function getItem(itemId) {
  return apiFetch(`/items/${itemId}`);
}

async function triggerRun() {
  return apiFetch('/runs', { method: 'POST' });
}
