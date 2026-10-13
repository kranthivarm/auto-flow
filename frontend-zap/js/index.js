const USER_ID = 'user-123';

const zapListEl  = document.getElementById('zap-list');
const loadingEl  = document.getElementById('loading-msg');
const emptyEl    = document.getElementById('empty-msg');
const errorEl    = document.getElementById('error-msg');

function showError(msg) {
  errorEl.textContent = msg;
  errorEl.classList.remove('hidden');
}

async function loadZaps() {
  try {
    const zaps = await zapApi.getAll(USER_ID);
    loadingEl.classList.add('hidden');

    if (!zaps.length) {
      emptyEl.classList.remove('hidden');
      return;
    }

    zapListEl.classList.remove('hidden');
    zapListEl.innerHTML = zaps.map(zapCard).join('');
  } catch (e) {
    loadingEl.classList.add('hidden');
    showError('Could not load zaps. Is the backend running on port 8081?');
  }
}

function zapCard(zap) {
    const triggerLabel = {
        CRON: `Cron: ${zap.trigger.cronExpression}`,
        WEBHOOK: 'Webhook trigger',
        GMAIL_POLL: 'Gmail poll',
    }[zap.trigger.type] || zap.trigger.type;

    const actionLabel = zap.actions
        .sort((a, b) => a.order - b.order)
        .map(a => a.type)
        .join(' → ');

    return `
        <div class="zap-card">
            <div class="zap-info">
                <p class="zap-name">${zap.name}</p>
                <p class="zap-meta">${triggerLabel} &nbsp;·&nbsp; ${actionLabel}</p>
            </div>
            <div class="zap-actions">
                <span class="badge ${zap.active ? 'badge-active' : 'badge-inactive'}">
                    ${zap.active ? 'Active' : 'Inactive'}
                </span>
                <button class="btn btn-sm ${zap.active ? 'btn-secondary' : 'btn-primary'}"
                    onclick="toggle('${zap.id}', ${!zap.active})">
                    ${zap.active ? 'Disable' : 'Enable'}
                </button>
                <a href="create-zap.html?editId=${zap.id}"
                    class="btn btn-sm btn-secondary">Edit</a>
                <a href="executions.html?zapId=${zap.id}"
                    class="btn btn-sm btn-secondary">Logs</a>
                <button class="btn btn-sm btn-danger"
                    onclick="remove('${zap.id}')">Delete</button>
            </div>
        </div>
    `;
}

async function toggle(id, active) {
  try {
    await zapApi.toggle(id, active);
    await loadZaps();
  } catch (e) {
    showError('Toggle failed: ' + e.message);
  }
}

async function remove(id) {
  if (!confirm('Delete this zap?')) return;
  try {
    await zapApi.remove(id);
    await loadZaps();
  } catch (e) {
    showError('Delete failed: ' + e.message);
  }
}

loadZaps();