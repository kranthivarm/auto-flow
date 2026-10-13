let actionIndex = 0;
const params = new URLSearchParams(window.location.search);
const editId = params.get('editId');
const isEditMode = !!editId;

const FIELDS = {
    HTTP: [
        { key: 'url', label: 'URL', placeholder: 'https://api.openweathermap.org/...' },
        { key: 'method', label: 'Method', type: 'select', options: ['GET','POST','PUT','DELETE'] },
    ],
   EMAIL: [
    { key: 'to', label: 'To', placeholder: 'you@gmail.com' },
    { key: 'subject', label: 'Subject', placeholder: 'Weather update' },
    {
        key: 'bodyTemplate',
        label: 'Body (use {{step1.response}} to include previous step data)',
        placeholder: 'Weather today: {{step1.response}}',
        type: 'textarea'
    },
],
   TELEGRAM: [
    {
        key: 'botToken',
        label: 'Bot Token',
        placeholder: '7123456789:AAH-xxx'
    },
    {
        key: 'chatId',
        label: 'Chat ID',
        placeholder: '987654321'
    },
    {
        key: 'messageTemplate',
        label: 'Message template',
        placeholder: 'Good morning!\n\n{{step1.summary}}',
        type: 'textarea'
    },
],
    GOOGLE_CALENDAR: [
        { key: 'calendarId', label: 'Calendar ID', placeholder: 'primary' },
        { key: 'accessToken', label: 'Access Token', placeholder: 'ya29.xxx' },
        { key: 'reminderMessage', label: 'Reminder prefix', placeholder: 'Upcoming event:' },
    ],

    STOCK_ALERT: [
    { key: 'symbol',      label: 'Stock symbol',   placeholder: 'RELIANCE.BSE or INFY.BSE' },
    { key: 'apiKey',      label: 'Alpha Vantage API key', placeholder: 'your_key_here' },
    { key: 'targetPrice', label: 'Target price (₹)', placeholder: '2500' },
    { key: 'condition',   label: 'Alert when price is',
      type: 'select', options: ['ABOVE', 'BELOW'] },
    ],
    NEWS: [
        { key: 'apiKey', label: 'NewsAPI key',
          placeholder: 'get free key at newsapi.org' },
        { key: 'category', label: 'Category',
          type: 'select',
          options: ['general', 'technology', 'sports',
                    'business', 'entertainment',
                    'health', 'science'] },
        { key: 'country', label: 'Country code',
          placeholder: 'in' },
        { key: 'count', label: 'Number of headlines',
          placeholder: '5' }
    ],

    CALENDAR_REMINDER: [
        {
            key: 'messageTemplate',
            label: 'Message template',
            type: 'textarea',
            placeholder: 'You have {{trigger.summary}} in 30 minutes!\nTime: {{trigger.startTime}}\nLocation: {{trigger.location}}\nMeet: {{trigger.meetLink}}'
        }
    ],

    CALENDAR_CREATE: [
        { key: 'title',          label: 'Event title',           placeholder: 'Team Standup' },
        { key: 'startDateTime',  label: 'Start date and time',   placeholder: '2026-04-27T15:00:00 or tomorrow at 3pm' },
        { key: 'durationMinutes',label: 'Duration (minutes)',    placeholder: '60' },
        { key: 'description',    label: 'Description',           type: 'textarea', placeholder: 'Meeting agenda...' },
        { key: 'location',       label: 'Location (optional)',   placeholder: 'Conference Room B' },
        { key: 'attendeeEmails', label: 'Attendees (optional)',  placeholder: 'john@gmail.com, jane@gmail.com' },
        { key: 'addMeetLink',    label: 'Add Google Meet link',  type: 'select', options: ['true', 'false'] }
    ],
};

// On page load — if edit mode, load existing zap data
window.addEventListener('DOMContentLoaded', async () => {
    if (isEditMode) {
        document.getElementById('page-title').textContent = 'Edit Zap';
        document.getElementById('save-btn').textContent = 'Update Zap';
        await loadZapForEdit(editId);
    } else {
        addAction('HTTP');
    }
});

async function loadZapForEdit(zapId) {
    try {
        const zap = await zapApi.getById(zapId);

        // Fill name
        document.getElementById('zap-name').value = zap.name;

        // Fill trigger
        document.getElementById('trigger-type').value = zap.trigger.type;
        onTriggerChange();

        if (zap.trigger.type === 'CRON') {
            document.getElementById('cron-expr').value =
                zap.trigger.cronExpression || '';
        }
        if (zap.trigger.type === 'GMAIL_POLL') {
            document.getElementById('gmail-sender').value =
            zap.trigger.gmailSender || '';
}
        if (zap.trigger.type === 'CALENDAR_EVENT') {
            document.getElementById('calendar-reminder-minutes').value =
                zap.trigger.reminderMinutesBefore || 30;
        }

        // Fill actions
        const sorted = zap.actions.sort((a, b) => a.order - b.order);
        sorted.forEach(action => {
            addAction(action.type, action.config);
        });

    } catch (e) {
        showError('Failed to load zap: ' + e.message);
    }
}

function onTriggerChange() {
    const type = document.getElementById('trigger-type').value;
    document.getElementById('cron-fields')
        .classList.toggle('hidden', type !== 'CRON');
    document.getElementById('webhook-fields')
        .classList.toggle('hidden', type !== 'WEBHOOK');
    document.getElementById('gmail-fields')
        .classList.toggle('hidden', type !== 'GMAIL_POLL');
    document.getElementById('calendar-fields')
        .classList.toggle('hidden', type !== 'CALENDAR_EVENT');
}

function addAction(defaultType = 'HTTP', existingConfig = null) {
    actionIndex++;
    const idx = actionIndex;
    const container = document.getElementById('actions-container');

    const block = document.createElement('div');
    block.className = 'action-block';
    block.id = `action-block-${idx}`;
    block.innerHTML = `
        <div class="action-header">
            <strong>Action ${idx}</strong>
            <button class="btn btn-sm btn-danger"
                onclick="removeAction(${idx})">Remove</button>
        </div>
        <div class="form-group">
            <label>Action type</label>
            <select onchange="renderFields(${idx}, this.value)">
                <option value="HTTP">HTTP Request</option>
                <option value="EMAIL">Send Email</option>
                <option value="TELEGRAM">Send Telegram</option>
                <option value="GOOGLE_CALENDAR">Google Calendar</option>
                <option value="NEWS">News Digest</option>
                <option value="STOCK_ALERT">Stock Price Alert</option>
                <option value="CALENDAR_REMINDER">Calendar Reminder</option>
                <option value="CALENDAR_CREATE">Create Calendar Event</option>
            </select>
        </div>

      

        <div id="fields-${idx}"></div>
    `;
    container.appendChild(block);

    // Set the correct action type in dropdown
    const select = block.querySelector('select');
    select.value = defaultType;

    renderFields(idx, defaultType, existingConfig);
}

function renderFields(idx, type, existingConfig = null) {
    const container = document.getElementById(`fields-${idx}`);
    container.innerHTML = (FIELDS[type] || []).map(f => {
        const existingValue = existingConfig ? (existingConfig[f.key] || '') : '';

        if (f.type === 'select') {
            return `<div class="form-group">
                <label>${f.label}</label>
                <select data-action="${idx}" data-key="${f.key}">
                    ${f.options.map(o =>
                        `<option ${existingValue === o ? 'selected' : ''}>${o}</option>`
                    ).join('')}
                </select>
            </div>`;
        }
        if (f.type === 'textarea') {
    return `<div class="form-group">
        <label>${f.label}</label>
        <textarea data-action="${idx}" data-key="${f.key}"
            placeholder="${f.placeholder}"
            rows="3">${existingValue}</textarea>
        <small class="hint">
            Use {{step1.response}} to include output from Action 1,
            {{step2.response}} for Action 2, and so on.
        </small>
    </div>`;
}
if (f.type === 'textarea') {
    return `<div class="form-group">
        <label>${f.label}</label>
        <textarea data-action="${idx}" data-key="${f.key}"
            placeholder="${f.placeholder}"
            rows="3">${existingValue}</textarea>
        <small class="hint" style="color:#888;font-size:12px">
            Available variables: 
            {{step1.summary}} — full weather summary with outfit tip,
            {{step1.temp}} — temperature only,
            {{step1.condition}} — weather condition,
            {{step1.outfit}} — outfit suggestion only,
            {{step1.response}} — raw API response
        </small>
    </div>`;
}

        return `<div class="form-group">
            <label>${f.label}</label>
            <input type="text"
                data-action="${idx}"
                data-key="${f.key}"
                placeholder="${f.placeholder}"
                value="${existingValue}">
        </div>`;
    }).join('');
}

function removeAction(idx) {
    document.getElementById(`action-block-${idx}`).remove();
}

function collectActions() {
    const actions = [];
    document.querySelectorAll('.action-block').forEach((block, i) => {
        const type = block.querySelector('select').value;
        const config = {};
        block.querySelectorAll('[data-key]').forEach(el => {
            config[el.dataset.key] = el.value;
        });
        actions.push({ order: i + 1, type, config });
    });
    return actions;
}

async function saveZap() {
    const errorEl   = document.getElementById('error-msg');
    const successEl = document.getElementById('success-msg');
    errorEl.classList.add('hidden');
    successEl.classList.add('hidden');

    const name = document.getElementById('zap-name').value.trim();
    if (!name) {
        errorEl.textContent = 'Please enter a zap name.';
        errorEl.classList.remove('hidden');
        return;
    }

    const triggerType = document.getElementById('trigger-type').value;
    const trigger = { type: triggerType };
if (triggerType === 'CRON') {
    trigger.cronExpression =
        document.getElementById('cron-expr').value.trim();
}
if (triggerType === 'GMAIL_POLL') {
    trigger.gmailSender =
        document.getElementById('gmail-sender').value.trim();
}
if (triggerType === 'CALENDAR_EVENT') {
    trigger.reminderMinutesBefore =
        parseInt(document.getElementById(
            'calendar-reminder-minutes').value, 10) || 30;
}

    const actions = collectActions();
    if (!actions.length) {
        errorEl.textContent = 'Add at least one action.';
        errorEl.classList.remove('hidden');
        return;
    }

    const zapData = { name, userId: 'user-123', trigger, actions };

    try {
        if (isEditMode) {
            await zapApi.update(editId, zapData);
            successEl.textContent = 'Zap updated! Redirecting...';
        } else {
            await zapApi.create(zapData);
            successEl.textContent = 'Zap created! Redirecting...';
        }
        successEl.classList.remove('hidden');
        setTimeout(() => window.location.href = 'index.html', 1500);
    } catch (e) {
        errorEl.textContent = 'Failed: ' + e.message;
        errorEl.classList.remove('hidden');
    }
}