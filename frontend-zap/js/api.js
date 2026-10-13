// Single source of truth for all API calls.
// All other JS files use this — never call fetch() directly.
// Change GATEWAY_URL here and it updates everywhere.
const GATEWAY_URL = 'http://localhost:8080';

function getAuthHeaders() {
  const token = localStorage.getItem('token');
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return headers;
}

async function request(method, path, body = null) {
  const options = {
    method,
    headers: getAuthHeaders(),
  };
  if (body) options.body = JSON.stringify(body);

  const res = await fetch(`${GATEWAY_URL}${path}`, options);

  if (res.status === 401) {
    const onAuthPage = window.location.pathname.endsWith('login.html') ||
                       window.location.pathname.endsWith('register.html');
    if (!onAuthPage) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = 'login.html';
      return;
    }
  }

  const json = await res.json();

  if (!res.ok || !json.success) {
    throw new Error(json.error || `Request failed: ${res.status}`);
  }
  return json.data;
}

const authApi = {
  login:    (email, password) =>
    request('POST', '/api/auth/login', { email, password }),
  register: (name, email, password) =>
    request('POST', '/api/auth/register', { name, email, password }),
};

const zapApi = {
  getAll:  (userId) => request('GET', `/api/zaps?userId=${userId}`),
  getById: (id)     => request('GET', `/api/zaps/${id}`),
  create:  (zap)    => request('POST', '/api/zaps', zap),
  update:  (id, zap) => request('PUT', `/api/zaps/${id}`, zap),
  toggle:  (id, active) =>
    request('PATCH', `/api/zaps/${id}/toggle?active=${active}`),
  remove:  (id)     => request('DELETE', `/api/zaps/${id}`),
};

const executionApi = {
  getAll: () =>
    fetch(`${GATEWAY_URL}/api/executions`, { headers: getAuthHeaders() }).then(r => r.json()),
  getByZap: (zapId) =>
    fetch(`${GATEWAY_URL}/api/executions?zapId=${zapId}`, { headers: getAuthHeaders() }).then(r => r.json()),
};
