const params  = new URLSearchParams(window.location.search);
const zapId   = params.get('zapId');

const listEl    = document.getElementById('exec-list');
const loadingEl = document.getElementById('loading-msg');
const emptyEl   = document.getElementById('empty-msg');
const errorEl   = document.getElementById('error-msg');

const STATUS_CLASS = {
  SUCCESS: 'badge-active',
  FAILED:  'badge-failed',
  RUNNING: 'badge-running',
  RETRYING:'badge-retrying',
};

function execCard(exec) {
  const date = new Date(exec.triggeredAt).toLocaleString();
  const rows = (exec.actionLogs || []).map(log => `
    <tr>
      <td>Step ${log.actionOrder}</td>
      <td>${log.type}</td>
      <td><span class="badge ${STATUS_CLASS[log.status]||''}">${log.status}</span></td>
      <td class="mono">${log.response || '—'}</td>
    </tr>
  `).join('');

  return `
    <div class="exec-card">
      <div class="exec-header">
        <div>
          <p class="exec-time">${date}</p>
          <p class="exec-meta">Zap: ${exec.zapId}</p>
        </div>
        <span class="badge ${STATUS_CLASS[exec.status]||''}">${exec.status}</span>
      </div>
      ${rows ? `
        <table class="log-table">
          <thead><tr><th>Step</th><th>Type</th><th>Status</th><th>Response</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>` : ''}
    </div>
  `;
}

async function load() {
  try {
    const data = await executionApi.getByZap(zapId || 'all');
    loadingEl.classList.add('hidden');
    if (!data.length) { emptyEl.classList.remove('hidden'); return; }
    listEl.classList.remove('hidden');
    listEl.innerHTML = data.map(execCard).join('');
  } catch (e) {
    loadingEl.classList.add('hidden');
    errorEl.textContent = 'Could not load executions. Build Execution Service first.';
    errorEl.classList.remove('hidden');
  }
}

load();