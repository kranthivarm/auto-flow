let createdZapId = null;

function updateCharCount(textarea) {
    document.getElementById('char-count').textContent =
        textarea.value.length;
}

function useExample(btn) {
    const textarea =
        document.getElementById('description');
    textarea.value = btn.textContent.trim();
    updateCharCount(textarea);
    textarea.focus();
}

async function generateZap() {
    const description =
        document.getElementById('description')
            .value.trim();

    if (!description) {
        showError('Please describe what you want to automate.');
        return;
    }

    if (description.length < 10) {
        showError('Please give a bit more detail.');
        return;
    }

    hideResult();
    hideError();

    document.getElementById('generate-btn').disabled = true;
    document.getElementById('generate-btn').textContent =
        'Creating...';
    document.getElementById('thinking').style.display =
        'block';

    try {
        const userId = localStorage.getItem('userId')
            || 'user-123';

        const response = await fetch(
            `${GATEWAY_URL}/api/copilot/create`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' +
                        (localStorage.getItem('token') || '')
                },
                body: JSON.stringify({ description, userId })
            }
        );

        const json = await response.json();

        if (!response.ok) {
            throw new Error(
                json.error || 'Server error: ' +
                response.status);
        }

        if (!json.success || !json.data ||
                !json.data.success) {
            throw new Error(
                json.data?.error ||
                json.error ||
                'Failed to create zap');
        }

        const data = json.data;
        createdZapId = data.zap.id;
        showResult(data);

    } catch (err) {
        showError(err.message);
    } finally {
        document.getElementById('generate-btn').disabled =
            false;
        document.getElementById('generate-btn').innerHTML =
            '✦ Create Zap with AI';
        document.getElementById('thinking').style.display =
            'none';
    }
}

function showResult(data) {
    const zap = data.zap;

    document.getElementById('result-message').textContent =
        data.message || 'Your automation is ready.';

    const preview =
        document.getElementById('zap-preview');

    const triggerLabel = {
        CRON: `⏰ Scheduled — ${zap.trigger.cronExpression || ''}`,
        WEBHOOK: '🔗 Webhook trigger',
        GMAIL_POLL: `📧 Gmail — ${zap.trigger.gmailSender || 'inbox'}`,
        CALENDAR_EVENT: '📅 Calendar event trigger'
    }[zap.trigger.type] || zap.trigger.type;

    const actionsHtml = zap.actions
        .sort((a, b) => a.order - b.order)
        .map(action => {
            const labels = {
                HTTP:             '🌐 HTTP Request',
                EMAIL:            '📧 Send Email',
                TELEGRAM:         '📱 Send Telegram',
                DISCORD:          '💬 Send Discord',
                STOCK_ALERT:      '📈 Stock Alert',
                NEWS:             '📰 News Digest',
                CRICKET_SCORE:    '🏏 Cricket Scores',
                CALENDAR_REMINDER:'📅 Calendar Reminder',
                CALENDAR_CREATE:  '📅 Create Event',
            };
            return `
                <div class="zap-step">
                    <span class="step-badge">
                        Action ${action.order}
                    </span>
                    <div class="step-detail">
                        <strong>
                            ${labels[action.type] || action.type}
                        </strong>
                        ${getActionSummary(action)}
                    </div>
                </div>`;
        }).join('');

    preview.innerHTML = `
        <div class="zap-preview-name">${zap.name}</div>
        <div class="zap-step">
            <span class="step-badge trigger-badge">
                Trigger
            </span>
            <div class="step-detail">
                <strong>${triggerLabel}</strong>
            </div>
        </div>
        ${actionsHtml}
    `;

    document.getElementById('result-card').style.display =
        'block';
}

function getActionSummary(action) {
    const c = action.config || {};
    switch (action.type) {
        case 'EMAIL':
            return c.to
                ? `<br>To: ${c.to}` : '';
        case 'TELEGRAM':
            return c.chatId
                ? `<br>Chat ID: ${c.chatId}` : '';
        case 'STOCK_ALERT':
            return c.symbol
                ? `<br>${c.symbol} ${c.condition} ₹${c.targetPrice}`
                : '';
        case 'NEWS':
            return c.category
                ? `<br>Category: ${c.category}` : '';
        case 'HTTP':
            return c.url
                ? `<br>${c.method || 'GET'} ${c.url.substring(0, 40)}...`
                : '';
        default:
            return '';
    }
}

async function enableZap() {
    if (!createdZapId) return;
    try {
        await zapApi.toggle(createdZapId, true);
        const btn = document.getElementById('enable-btn');
        btn.textContent = '✓ Enabled!';
        btn.style.background = '#f0fdf4';
        btn.style.color = '#16a34a';
        btn.disabled = true;
    } catch (err) {
        showError('Could not enable zap: ' + err.message);
    }
}

function showError(message) {
    const card = document.getElementById('error-card');
    card.textContent = '⚠️ ' + message;
    card.style.display = 'block';
}

function hideError() {
    document.getElementById('error-card').style.display =
        'none';
}

function hideResult() {
    document.getElementById('result-card').style.display =
        'none';
}