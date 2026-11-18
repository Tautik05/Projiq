/**
 * Projiq - Multi-Tenant Workspace & Project Management SaaS
 * Frontend Application Client (Vanilla JavaScript ES6+)
 */

// ==========================================
// 1. STATE & CONSTANTS
// ==========================================
const API_BASE = '/api';

const state = {
    accessToken: null,
    refreshToken: null,
    user: null,
    organizations: [],
    currentOrg: null,
    projects: [],
    currentProject: null,
    members: [],
    roles: [],
    permissions: [],
    tasks: [],
    activeTaskId: null,
    isRefreshingToken: false,
    refreshSubscribers: [],
    devPollingInterval: null,
    notifPollingInterval: null
};

// ==========================================
// 2. UTILITY & TOAST NOTIFICATIONS
// ==========================================
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    const colors = {
        success: 'bg-emerald-600 text-white',
        error: 'bg-rose-600 text-white',
        warning: 'bg-amber-600 text-white',
        info: 'bg-slate-800 text-white'
    };

    toast.className = `px-4 py-3 rounded-xl shadow-xl text-xs font-semibold flex items-center justify-between gap-3 transform transition-all duration-300 opacity-0 translate-y-2 ${colors[type] || colors.info}`;
    toast.innerHTML = `
        <div class="flex items-center gap-2">
            <span>${escapeHtml(message)}</span>
        </div>
        <button class="opacity-70 hover:opacity-100 text-sm ml-2">✕</button>
    `;

    toast.querySelector('button').addEventListener('click', () => {
        toast.remove();
    });

    container.appendChild(toast);

    // Trigger enter animation
    requestAnimationFrame(() => {
        toast.classList.remove('opacity-0', 'translate-y-2');
    });

    setTimeout(() => {
        toast.classList.add('opacity-0', 'translate-y-2');
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function formatDate(isoStr) {
    if (!isoStr) return '-';
    try {
        const d = new Date(isoStr);
        return d.toLocaleDateString(undefined, {
            month: 'short',
            day: 'numeric',
            year: 'numeric'
        });
    } catch (e) {
        return isoStr;
    }
}

function formatTimeAgo(isoStr) {
    if (!isoStr) return '';
    try {
        const diff = (Date.now() - new Date(isoStr).getTime()) / 1000;
        if (diff < 60) return 'just now';
        if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
        if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
        return `${Math.floor(diff / 86400)}d ago`;
    } catch (e) {
        return '';
    }
}

// ==========================================
// 3. API CLIENT WITH TOKEN AUTO-REFRESH
// ==========================================
async function apiCall(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;
    const headers = {
        'Content-Type': 'application/json',
        ...(options.headers || {})
    };

    if (state.accessToken && !headers['Authorization']) {
        headers['Authorization'] = `Bearer ${state.accessToken}`;
    }

    if (state.currentOrg && !headers['X-Organization-Id'] && !options.skipTenant) {
        headers['X-Organization-Id'] = state.currentOrg.id;
    }

    try {
        const response = await fetch(url, {
            ...options,
            headers
        });

        // Handle 401 Unauthorized by trying to refresh access token
        if (response.status === 401 && state.refreshToken && !options._isRetry) {
            const refreshed = await refreshAccessToken();
            if (refreshed) {
                const retryOptions = { ...options, _isRetry: true };
                if (retryOptions.headers) {
                    delete retryOptions.headers['Authorization'];
                }
                return apiCall(endpoint, retryOptions);
            } else {
                logout();
                throw new Error('Session expired. Please log in again.');
            }
        }

        const data = await response.json().catch(() => null);

        if (!response.ok) {
            const errorMsg = (data && (data.message || data.error)) || `Request failed with status ${response.status}`;
            throw new Error(errorMsg);
        }

        return data;
    } catch (err) {
        throw err;
    }
}

async function refreshAccessToken() {
    if (state.isRefreshingToken) {
        return new Promise((resolve) => {
            state.refreshSubscribers.push(resolve);
        });
    }

    state.isRefreshingToken = true;
    try {
        const response = await fetch(`${API_BASE}/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: state.refreshToken })
        });

        if (!response.ok) {
            state.isRefreshingToken = false;
            state.refreshSubscribers.forEach(cb => cb(false));
            state.refreshSubscribers = [];
            return false;
        }

        const resData = await response.json();
        const payload = resData.data;
        saveTokens(payload.accessToken, payload.refreshToken);

        state.isRefreshingToken = false;
        state.refreshSubscribers.forEach(cb => cb(true));
        state.refreshSubscribers = [];
        return true;
    } catch (e) {
        state.isRefreshingToken = false;
        state.refreshSubscribers.forEach(cb => cb(false));
        state.refreshSubscribers = [];
        return false;
    }
}

function saveTokens(accessToken, refreshToken) {
    state.accessToken = accessToken;
    state.refreshToken = refreshToken;
    localStorage.setItem('projiq_tokens', JSON.stringify({ accessToken, refreshToken }));
}

function loadTokens() {
    const raw = localStorage.getItem('projiq_tokens');
    if (raw) {
        try {
            const parsed = JSON.parse(raw);
            state.accessToken = parsed.accessToken;
            state.refreshToken = parsed.refreshToken;
        } catch (e) {
            localStorage.removeItem('projiq_tokens');
        }
    }
}

function clearTokens() {
    state.accessToken = null;
    state.refreshToken = null;
    state.user = null;
    state.organizations = [];
    state.currentOrg = null;
    localStorage.removeItem('projiq_tokens');
    localStorage.removeItem('projiq_last_org');
}

// ==========================================
// 4. AUTH & SESSION LIFECYCLE
// ==========================================
async function initApp() {
    bindEventListeners();
    loadTokens();

    if (state.accessToken) {
        try {
            const userRes = await apiCall('/auth/me', { skipTenant: true });
            state.user = userRes.data;
            await enterWorkspace();
        } catch (err) {
            console.warn('Initial session check failed:', err);
            showAuthView();
        }
    } else {
        showAuthView();
    }

    // Pre-fill email if returning from verification link
    const urlParams = new URLSearchParams(window.location.search);
    const urlEmail = urlParams.get('email');
    if (urlEmail) {
        const loginEmailInput = document.getElementById('login-email');
        if (loginEmailInput) loginEmailInput.value = urlEmail;
        const loginPwdInput = document.getElementById('login-password');
        if (loginPwdInput) loginPwdInput.focus();
    }

    // Start Dev Token Poller (Development Mode Simulator)
    startDevPolling();
}

function showAuthView() {
    document.getElementById('auth-view').classList.remove('hidden');
    document.getElementById('app-view').classList.add('hidden');
}

function showAppView() {
    document.getElementById('auth-view').classList.add('hidden');
    document.getElementById('app-view').classList.remove('hidden');

    if (state.user) {
        document.getElementById('user-display-name').textContent = state.user.fullName || 'User';
        document.getElementById('user-display-email').textContent = state.user.email || '';
        document.getElementById('user-avatar').textContent = (state.user.fullName || 'U').charAt(0).toUpperCase();
    }
}

async function loginUser(email, password) {
    try {
        const res = await apiCall('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password }),
            skipTenant: true
        });

        const authData = res.data;
        saveTokens(authData.accessToken, authData.refreshToken);
        state.user = authData.user;

        showToast(`Welcome back, ${state.user.fullName}!`, 'success');
        await enterWorkspace();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function registerUser(fullName, email, password) {
    try {
        await apiCall('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ fullName, email, password }),
            skipTenant: true
        });

        showToast('Account created! Please check your verification link in the Dev Email Inbox (or terminal).', 'success');

        // Switch to the Sign In tab and pre-fill email
        const tabLogin = document.getElementById('tab-login');
        if (tabLogin) tabLogin.click();

        const loginEmail = document.getElementById('login-email');
        if (loginEmail) loginEmail.value = email;

        const loginPassword = document.getElementById('login-password');
        if (loginPassword) {
            loginPassword.value = '';
            loginPassword.focus();
        }

        // Refresh dev tokens and pulse the dev drawer button
        await pollDevTokens();
        const devBtn = document.getElementById('dev-drawer-btn');
        if (devBtn) {
            devBtn.classList.add('ring-4', 'ring-amber-300', 'animate-bounce');
            setTimeout(() => {
                devBtn.classList.remove('ring-4', 'ring-amber-300', 'animate-bounce');
            }, 3000);
        }
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function logout() {
    if (state.refreshToken) {
        try {
            await apiCall('/auth/logout', {
                method: 'POST',
                body: JSON.stringify({ refreshToken: state.refreshToken }),
                skipTenant: true
            });
        } catch (e) {
            // Ignore logout failures
        }
    }

    if (state.notifPollingInterval) {
        clearInterval(state.notifPollingInterval);
        state.notifPollingInterval = null;
    }

    clearTokens();
    showAuthView();
    showToast('Signed out successfully', 'info');
}

async function enterWorkspace() {
    showAppView();
    await loadOrganizations();

    // Start in-app notifications poller
    loadUnreadNotificationsCount();
    if (!state.notifPollingInterval) {
        state.notifPollingInterval = setInterval(loadUnreadNotificationsCount, 15000);
    }
}

// ==========================================
// 5. WORKSPACES & MULTI-TENANCY
// ==========================================
async function loadOrganizations() {
    try {
        const res = await apiCall('/organizations/my', { skipTenant: true });
        state.organizations = res.data || [];

        const savedOrgId = localStorage.getItem('projiq_last_org');
        let selected = state.organizations.find(o => o.id === savedOrgId);

        if (!selected && state.organizations.length > 0) {
            selected = state.organizations[0];
        }

        if (selected) {
            await switchOrganization(selected.id);
        } else {
            // User does not belong to any organization yet
            document.getElementById('current-org-name').textContent = 'No Workspace';
            document.getElementById('current-user-role-badge').textContent = 'None';
            openModal('create-org-modal');
        }

        renderOrgDropdown();
    } catch (err) {
        showToast('Failed to load workspaces: ' + err.message, 'error');
    }
}

async function switchOrganization(orgId) {
    const org = state.organizations.find(o => o.id === orgId);
    if (!org) return;

    state.currentOrg = org;
    localStorage.setItem('projiq_last_org', org.id);

    document.getElementById('current-org-name').textContent = org.name;
    const role = org.roleName || org.userRole || 'Owner';
    const roleBadge = document.getElementById('current-user-role-badge');
    if (roleBadge) {
        roleBadge.textContent = role;
        if (role === 'Owner') {
            roleBadge.className = 'text-xs bg-purple-100 text-purple-700 font-semibold px-2 py-0.5 rounded-full border border-purple-200';
        } else if (role === 'Admin') {
            roleBadge.className = 'text-xs bg-blue-100 text-blue-700 font-semibold px-2 py-0.5 rounded-full border border-blue-200';
        } else {
            roleBadge.className = 'text-xs bg-slate-100 text-slate-700 font-medium px-2 py-0.5 rounded-full border border-slate-200';
        }
    }

    // Hide dropdown
    document.getElementById('org-dropdown').classList.add('hidden');

    // Reload tenant-scoped data
    await Promise.all([
        loadProjects(),
        loadMembers(),
        loadRoles(),
        loadAuditLogs()
    ]);
}

function renderOrgDropdown() {
    const list = document.getElementById('org-list');
    if (!list) return;

    if (state.organizations.length === 0) {
        list.innerHTML = `<p class="px-3 py-2 text-xs text-slate-400">No organizations found</p>`;
        return;
    }

    list.innerHTML = state.organizations.map(org => {
        const isCurrent = state.currentOrg && state.currentOrg.id === org.id;
        const role = org.roleName || org.userRole || 'Owner';
        const roleClass = role === 'Owner'
            ? 'bg-purple-100 text-purple-700 font-semibold border border-purple-200'
            : (role === 'Admin' ? 'bg-blue-100 text-blue-700 font-semibold border border-blue-200' : 'bg-slate-100 text-slate-600 font-medium');
        return `
            <button class="org-item w-full text-left px-3 py-2 text-xs flex items-center justify-between hover:bg-slate-50 transition ${isCurrent ? 'bg-brand-50/70 text-brand-700 font-bold' : 'text-slate-700'}"
                    data-org-id="${org.id}">
                <span class="truncate">${escapeHtml(org.name)}</span>
                <span class="text-[10px] px-1.5 py-0.5 rounded ${roleClass}">${escapeHtml(role)}</span>
            </button>
        `;
    }).join('');

    list.querySelectorAll('.org-item').forEach(btn => {
        btn.addEventListener('click', () => {
            const orgId = btn.dataset.orgId;
            switchOrganization(orgId);
        });
    });
}

async function createOrganization(name, slug, description) {
    try {
        const payload = { name };
        if (slug && slug.trim().length > 0) payload.slug = slug.trim();
        if (description && description.trim().length > 0) payload.description = description.trim();

        const res = await apiCall('/organizations', {
            method: 'POST',
            body: JSON.stringify(payload),
            skipTenant: true
        });

        showToast(`Workspace "${res.data.name}" created!`, 'success');
        closeModal('create-org-modal');

        // Reload user's organizations and switch to newly created one
        await loadOrganizations();
        await switchOrganization(res.data.id);
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ==========================================
// 6. PROJECTS & TASKS (KANBAN BOARD)
// ==========================================
async function loadProjects() {
    if (!state.currentOrg) return;

    try {
        const res = await apiCall('/projects');
        state.projects = res.data || [];

        renderProjectSelector();

        if (state.projects.length > 0) {
            state.currentProject = state.projects[0];
            document.getElementById('project-selector').value = state.currentProject.id;
            updateProjectStatusBadge(state.currentProject.status);
            await loadTasks();
        } else {
            state.currentProject = null;
            clearKanbanColumns();
            document.getElementById('project-status-badge').textContent = 'No Projects';
        }
    } catch (err) {
        showToast('Failed to load projects: ' + err.message, 'error');
    }
}

function renderProjectSelector() {
    const sel = document.getElementById('project-selector');
    if (!sel) return;

    if (state.projects.length === 0) {
        sel.innerHTML = '<option value="">No projects available</option>';
        return;
    }

    sel.innerHTML = state.projects.map(p => `
        <option value="${p.id}">${escapeHtml(p.name)}</option>
    `).join('');
}

function updateProjectStatusBadge(status) {
    const badge = document.getElementById('project-status-badge');
    if (!badge) return;

    const styles = {
        ACTIVE: 'bg-emerald-50 text-emerald-600',
        PLANNING: 'bg-blue-50 text-blue-600',
        ON_HOLD: 'bg-amber-50 text-amber-600',
        COMPLETED: 'bg-purple-50 text-purple-600'
    };

    badge.className = `text-xs px-2.5 py-1 rounded-full font-medium ${styles[status] || styles.ACTIVE}`;
    badge.textContent = status || 'ACTIVE';
}

async function loadTasks() {
    if (!state.currentOrg || !state.currentProject) {
        clearKanbanColumns();
        return;
    }

    try {
        const statusFilter = document.getElementById('filter-task-status')?.value;
        let url = `/tasks?projectId=${state.currentProject.id}`;
        if (statusFilter) {
            url += `&status=${statusFilter}`;
        }

        const res = await apiCall(url);
        state.tasks = res.data || [];
        renderKanbanBoard();
    } catch (err) {
        showToast('Failed to load tasks: ' + err.message, 'error');
    }
}

function clearKanbanColumns() {
    ['todo', 'in-progress', 'in-review', 'completed'].forEach(col => {
        const el = document.getElementById(`tasks-${col}`);
        if (el) el.innerHTML = `<p class="text-xs text-slate-400 text-center py-6">No tasks</p>`;
        const count = document.getElementById(`count-${col}`);
        if (count) count.textContent = '0';
    });
}

function renderKanbanBoard() {
    const columns = {
        TODO: { el: document.getElementById('tasks-todo'), count: document.getElementById('count-todo'), items: [] },
        IN_PROGRESS: { el: document.getElementById('tasks-in-progress'), count: document.getElementById('count-in-progress'), items: [] },
        IN_REVIEW: { el: document.getElementById('tasks-in-review'), count: document.getElementById('count-in-review'), items: [] },
        COMPLETED: { el: document.getElementById('tasks-completed'), count: document.getElementById('count-completed'), items: [] }
    };

    state.tasks.forEach(task => {
        if (columns[task.status]) {
            columns[task.status].items.push(task);
        }
    });

    Object.keys(columns).forEach(statusKey => {
        const col = columns[statusKey];
        if (!col.el) return;

        col.count.textContent = col.items.length;

        if (col.items.length === 0) {
            col.el.innerHTML = `<div class="border-2 border-dashed border-slate-200/60 rounded-xl p-4 text-center text-xs text-slate-400">Empty</div>`;
            return;
        }

        col.el.innerHTML = col.items.map(task => renderTaskCard(task)).join('');
    });

    // Attach card event listeners
    document.querySelectorAll('.task-card').forEach(card => {
        card.addEventListener('click', (e) => {
            if (e.target.closest('.status-changer')) return; // ignore status select changes
            const taskId = card.dataset.taskId;
            openTaskDetail(taskId);
        });
    });

    // Attach fast status select changer listeners
    document.querySelectorAll('.status-changer').forEach(select => {
        select.addEventListener('change', async (e) => {
            const taskId = select.dataset.taskId;
            const newStatus = select.value;
            await updateTaskStatusQuick(taskId, newStatus);
        });
    });
}

function renderTaskCard(task) {
    const priorityColors = {
        URGENT: 'bg-rose-100 text-rose-700 border-rose-200',
        HIGH: 'bg-amber-100 text-amber-700 border-amber-200',
        MEDIUM: 'bg-blue-100 text-blue-700 border-blue-200',
        LOW: 'bg-slate-100 text-slate-600 border-slate-200'
    };

    return `
        <div class="task-card bg-white p-3.5 rounded-xl border border-slate-200/80 shadow-sm hover:shadow-md hover:border-brand-200 cursor-pointer transition space-y-2.5"
             data-task-id="${task.id}">
            <div class="flex items-center justify-between gap-2">
                <span class="text-[10px] font-bold px-2 py-0.5 rounded-full border ${priorityColors[task.priority] || priorityColors.MEDIUM}">
                    ${escapeHtml(task.priority)}
                </span>
                <select class="status-changer text-[11px] font-semibold bg-slate-50 border border-slate-200 rounded px-1.5 py-0.5 text-slate-600 focus:outline-none"
                        data-task-id="${task.id}">
                    <option value="TODO" ${task.status === 'TODO' ? 'selected' : ''}>TODO</option>
                    <option value="IN_PROGRESS" ${task.status === 'IN_PROGRESS' ? 'selected' : ''}>IN_PROGRESS</option>
                    <option value="IN_REVIEW" ${task.status === 'IN_REVIEW' ? 'selected' : ''}>IN_REVIEW</option>
                    <option value="COMPLETED" ${task.status === 'COMPLETED' ? 'selected' : ''}>COMPLETED</option>
                </select>
            </div>

            <h4 class="font-bold text-xs text-slate-900 leading-snug">${escapeHtml(task.title)}</h4>

            ${task.description ? `<p class="text-[11px] text-slate-500 line-clamp-2">${escapeHtml(task.description)}</p>` : ''}

            <div class="flex items-center justify-between pt-2 border-t border-slate-100 text-[11px] text-slate-400">
                <div class="flex items-center gap-1.5">
                    <div class="w-5 h-5 rounded-full bg-brand-100 text-brand-700 font-bold flex items-center justify-center text-[9px]">
                        ${(task.assigneeName || 'U').charAt(0).toUpperCase()}
                    </div>
                    <span class="truncate max-w-[90px] text-slate-600 font-medium">${escapeHtml(task.assigneeName || 'Unassigned')}</span>
                </div>
                <span>${formatDate(task.dueDate || task.createdAt)}</span>
            </div>
        </div>
    `;
}

async function updateTaskStatusQuick(taskId, status) {
    try {
        await apiCall(`/tasks/${taskId}`, {
            method: 'PATCH',
            body: JSON.stringify({ status })
        });
        showToast('Task updated', 'success');
        await loadTasks();
    } catch (err) {
        showToast(err.message, 'error');
        await loadTasks();
    }
}

async function openTaskDetail(taskId) {
    state.activeTaskId = taskId;
    const task = state.tasks.find(t => t.id === taskId);
    if (!task) return;

    document.getElementById('detail-task-title').textContent = task.title;
    document.getElementById('detail-task-priority').textContent = task.priority;
    document.getElementById('detail-task-desc').textContent = task.description || 'No description provided.';

    // Populate status dropdown
    const statusSel = document.getElementById('detail-task-status-select');
    statusSel.value = task.status;

    // Populate assignee dropdown from organization members
    const assigneeSel = document.getElementById('detail-task-assignee-select');
    assigneeSel.innerHTML = `
        <option value="">Unassigned</option>
        ${state.members.map(m => `
            <option value="${m.userId}" ${task.assigneeId === m.userId ? 'selected' : ''}>${escapeHtml(m.fullName)}</option>
        `).join('')}
    `;

    openModal('task-detail-modal');
    await loadTaskCommentsAndActivities(taskId);
}

async function loadTaskCommentsAndActivities(taskId) {
    const list = document.getElementById('task-comments-list');
    list.innerHTML = `<p class="text-xs text-slate-400 text-center py-2">Loading timeline...</p>`;

    try {
        const [commentsRes, activitiesRes] = await Promise.all([
            apiCall(`/tasks/${taskId}/comments`),
            apiCall(`/tasks/${taskId}/activities`)
        ]);

        const comments = (commentsRes.data || []).map(c => ({ ...c, isComment: true }));
        const activities = (activitiesRes.data || []).map(a => ({ ...a, isActivity: true }));

        const timeline = [...comments, ...activities].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));

        if (timeline.length === 0) {
            list.innerHTML = `<p class="text-xs text-slate-400 text-center py-3">No activity or comments yet</p>`;
            return;
        }

        list.innerHTML = timeline.map(item => {
            if (item.isComment) {
                return `
                    <div class="flex gap-2.5 items-start text-xs bg-slate-50 p-2.5 rounded-xl border border-slate-100">
                        <div class="w-6 h-6 rounded-full bg-brand-600 text-white font-bold flex items-center justify-center text-[10px] shrink-0">
                            ${(item.userName || 'U').charAt(0).toUpperCase()}
                        </div>
                        <div class="flex-1">
                            <div class="flex items-center justify-between mb-0.5">
                                <span class="font-bold text-slate-800">${escapeHtml(item.userName)}</span>
                                <span class="text-[10px] text-slate-400">${formatTimeAgo(item.createdAt)}</span>
                            </div>
                            <p class="text-slate-700 text-xs">${escapeHtml(item.content)}</p>
                        </div>
                    </div>
                `;
            } else {
                return `
                    <div class="flex items-center gap-2 text-[11px] text-slate-500 py-1 px-2 border-l-2 border-slate-300">
                        <span class="font-semibold text-slate-700">${escapeHtml(item.actorName || 'System')}</span>
                        <span>${escapeHtml(item.action)}: ${escapeHtml(item.details || '')}</span>
                        <span class="text-[10px] text-slate-400 ml-auto">${formatTimeAgo(item.createdAt)}</span>
                    </div>
                `;
            }
        }).join('');
    } catch (err) {
        list.innerHTML = `<p class="text-xs text-rose-500 text-center py-2">Error loading timeline</p>`;
    }
}

// ==========================================
// 7. TEAM & DYNAMIC RBAC
// ==========================================
async function loadMembers() {
    if (!state.currentOrg) return;

    try {
        const res = await apiCall(`/organizations/${state.currentOrg.id}/members`);
        state.members = res.data || [];
        renderMembersTable();
        populateAssigneeAndManagerDropdowns();
    } catch (err) {
        showToast('Failed to load team members: ' + err.message, 'error');
    }
}

function renderMembersTable() {
    const tbody = document.getElementById('members-table-body');
    if (!tbody) return;

    if (state.members.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="py-6 text-center text-xs text-slate-400">No members found</td></tr>`;
        return;
    }

    tbody.innerHTML = state.members.map(m => `
        <tr class="hover:bg-slate-50/50 transition">
            <td class="py-3 pr-4">
                <div class="flex items-center gap-2.5">
                    <div class="w-7 h-7 rounded-full bg-brand-100 text-brand-700 font-bold flex items-center justify-center text-xs">
                        ${(m.fullName || 'U').charAt(0).toUpperCase()}
                    </div>
                    <span class="font-semibold text-slate-800 text-xs">${escapeHtml(m.fullName)}</span>
                </div>
            </td>
            <td class="py-3 px-4 text-xs text-slate-500">${escapeHtml(m.email)}</td>
            <td class="py-3 px-4">
                <select class="member-role-select text-xs font-semibold bg-slate-50 border border-slate-200 rounded-lg px-2 py-1 text-slate-700"
                        data-user-id="${m.userId}">
                    ${state.roles.map(r => `
                        <option value="${r.id}" ${r.id === m.roleId ? 'selected' : ''}>${escapeHtml(r.name)}</option>
                    `).join('')}
                </select>
            </td>
            <td class="py-3 px-4 text-xs text-slate-400">${formatDate(m.joinedAt)}</td>
            <td class="py-3 pl-4 text-right">
                <button class="btn-remove-member text-xs text-rose-500 hover:text-rose-700 hover:underline font-semibold"
                        data-user-id="${m.userId}" data-user-name="${escapeHtml(m.fullName)}">
                    Remove
                </button>
            </td>
        </tr>
    `).join('');

    // Attach role change listeners
    tbody.querySelectorAll('.member-role-select').forEach(sel => {
        sel.addEventListener('change', async () => {
            const userId = sel.dataset.userId;
            const newRoleId = sel.value;
            await updateMemberRole(userId, newRoleId);
        });
    });

    // Attach member removal listeners
    tbody.querySelectorAll('.btn-remove-member').forEach(btn => {
        btn.addEventListener('click', async () => {
            const userId = btn.dataset.userId;
            const userName = btn.dataset.userName;
            if (confirm(`Are you sure you want to remove ${userName} from this workspace?`)) {
                await removeMember(userId);
            }
        });
    });
}

function populateAssigneeAndManagerDropdowns() {
    const mgrSel = document.getElementById('new-proj-manager');
    const taskAssigneeSel = document.getElementById('new-task-assignee');

    const options = `
        <option value="">Unassigned</option>
        ${state.members.map(m => `<option value="${m.userId}">${escapeHtml(m.fullName)} (${escapeHtml(m.email)})</option>`).join('')}
    `;

    if (mgrSel) mgrSel.innerHTML = options;
    if (taskAssigneeSel) taskAssigneeSel.innerHTML = options;
}

async function updateMemberRole(userId, roleId) {
    if (!state.currentOrg) return;
    try {
        await apiCall(`/organizations/${state.currentOrg.id}/members/${userId}/role`, {
            method: 'PATCH',
            body: JSON.stringify({ roleId })
        });
        showToast('Member role updated', 'success');
        await loadMembers();
    } catch (err) {
        showToast(err.message, 'error');
        await loadMembers();
    }
}

async function removeMember(userId) {
    if (!state.currentOrg) return;
    try {
        await apiCall(`/organizations/${state.currentOrg.id}/members/${userId}`, {
            method: 'DELETE'
        });
        showToast('Member removed from workspace', 'success');
        await loadMembers();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function loadRoles() {
    if (!state.currentOrg) return;

    try {
        const [rolesRes, permsRes] = await Promise.all([
            apiCall(`/organizations/${state.currentOrg.id}/roles`),
            apiCall('/permissions')
        ]);

        state.roles = rolesRes.data || [];
        state.permissions = permsRes.data || [];

        renderRolesCards();
        renderInviteRoleSelect();
        renderPermissionsCheckboxes();
    } catch (err) {
        showToast('Failed to load roles: ' + err.message, 'error');
    }
}

function renderRolesCards() {
    const container = document.getElementById('roles-cards-container');
    if (!container) return;

    if (state.roles.length === 0) {
        container.innerHTML = `<p class="col-span-full text-xs text-slate-400 text-center py-6">No roles defined</p>`;
        return;
    }

    container.innerHTML = state.roles.map(role => {
        const permsList = Array.from(role.permissions || []);
        return `
            <div class="bg-slate-50/70 p-4 rounded-xl border border-slate-200/80 flex flex-col justify-between">
                <div>
                    <div class="flex items-center justify-between mb-2">
                        <h4 class="font-bold text-sm text-slate-900">${escapeHtml(role.name)}</h4>
                        <span class="text-[10px] px-2 py-0.5 rounded-full font-bold uppercase ${role.systemRole ? 'bg-slate-200 text-slate-700' : 'bg-brand-100 text-brand-700'}">
                            ${role.systemRole ? 'System' : 'Custom'}
                        </span>
                    </div>
                    <p class="text-xs text-slate-500 mb-3">${escapeHtml(role.description || 'No description')}</p>
                    <div class="flex flex-wrap gap-1 max-h-24 overflow-y-auto pr-1">
                        ${permsList.map(p => `
                            <span class="text-[9px] bg-white border border-slate-200 px-1.5 py-0.5 rounded text-slate-600 font-mono">
                                ${escapeHtml(p)}
                            </span>
                        `).join('')}
                    </div>
                </div>

                ${!role.systemRole ? `
                    <div class="mt-4 pt-2 border-t border-slate-200/50 flex justify-end">
                        <button class="btn-delete-role text-xs text-rose-500 hover:text-rose-700 hover:underline font-semibold"
                                data-role-id="${role.id}">
                            Delete Role
                        </button>
                    </div>
                ` : ''}
            </div>
        `;
    }).join('');

    container.querySelectorAll('.btn-delete-role').forEach(btn => {
        btn.addEventListener('click', async () => {
            const roleId = btn.dataset.roleId;
            if (confirm('Are you sure you want to delete this custom role?')) {
                await deleteRole(roleId);
            }
        });
    });
}

async function deleteRole(roleId) {
    if (!state.currentOrg) return;
    try {
        await apiCall(`/organizations/${state.currentOrg.id}/roles/${roleId}`, {
            method: 'DELETE'
        });
        showToast('Role deleted', 'success');
        await loadRoles();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function renderInviteRoleSelect() {
    const sel = document.getElementById('invite-role-select');
    if (!sel) return;

    sel.innerHTML = state.roles.map(r => `
        <option value="${r.id}">${escapeHtml(r.name)} ${r.systemRole ? '(System)' : ''}</option>
    `).join('');
}

function renderPermissionsCheckboxes() {
    const container = document.getElementById('role-permissions-checkboxes');
    if (!container) return;

    container.innerHTML = state.permissions.map(perm => `
        <label class="flex items-center gap-2 p-1.5 bg-slate-50 rounded-lg hover:bg-slate-100 cursor-pointer">
            <input type="checkbox" name="role-perm" value="${escapeHtml(perm)}" class="rounded text-brand-600 focus:ring-brand-500">
            <span class="font-mono text-slate-700">${escapeHtml(perm)}</span>
        </label>
    `).join('');
}

async function inviteMember(email, roleId) {
    if (!state.currentOrg) return;
    try {
        await apiCall(`/organizations/${state.currentOrg.id}/invitations`, {
            method: 'POST',
            body: JSON.stringify({ email, roleId })
        });
        showToast(`Invitation sent! The invitation link is available in Dev Email Inbox.`, 'success');
        closeModal('invite-member-modal');
        await pollDevTokens();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function createCustomRole(name, description, permissions) {
    if (!state.currentOrg) return;
    try {
        await apiCall(`/organizations/${state.currentOrg.id}/roles`, {
            method: 'POST',
            body: JSON.stringify({ name, description, permissions })
        });
        showToast(`Custom role "${name}" created!`, 'success');
        closeModal('create-role-modal');
        await loadRoles();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ==========================================
// 8. AUDIT LOGS
// ==========================================
async function loadAuditLogs() {
    if (!state.currentOrg) return;

    const tbody = document.getElementById('audit-table-body');
    if (!tbody) return;

    try {
        const resourceFilter = document.getElementById('filter-audit-resource')?.value;
        let url = `/audit-logs?page=0&size=50`;
        if (resourceFilter) {
            url += `&resourceType=${resourceFilter}`;
        }

        const res = await apiCall(url);
        const pagedData = res.data;
        const logs = pagedData.content || [];

        if (logs.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" class="py-6 text-center text-xs text-slate-400">No audit logs recorded</td></tr>`;
            return;
        }

        tbody.innerHTML = logs.map(log => `
            <tr class="hover:bg-slate-50/50 transition">
                <td class="py-2.5 pr-3 font-bold text-slate-800">${escapeHtml(log.action)}</td>
                <td class="py-2.5 px-3">
                    <span class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[10px] font-semibold">${escapeHtml(log.resourceType)}</span>
                </td>
                <td class="py-2.5 px-3 text-slate-600">${escapeHtml(log.actorName || 'System')}</td>
                <td class="py-2.5 px-3 text-slate-500 truncate max-w-xs" title="${escapeHtml(log.details)}">${escapeHtml(log.details || '-')}</td>
                <td class="py-2.5 pl-3 text-slate-400 text-right whitespace-nowrap">${formatDate(log.createdAt)}</td>
            </tr>
        `).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" class="py-4 text-center text-xs text-rose-500">Failed to load audit logs: ${escapeHtml(err.message)}</td></tr>`;
    }
}

// ==========================================
// 9. IN-APP NOTIFICATIONS
// ==========================================
async function loadUnreadNotificationsCount() {
    if (!state.accessToken) return;

    try {
        const res = await apiCall('/notifications/unread-count', { skipTenant: true });
        const count = res.data || 0;
        state.unreadNotificationsCount = count;

        const badge = document.getElementById('notif-badge');
        if (badge) {
            if (count > 0) {
                badge.textContent = count > 9 ? '9+' : count;
                badge.classList.remove('hidden');
            } else {
                badge.classList.add('hidden');
            }
        }
    } catch (e) {
        // Suppress background poll errors
    }
}

async function loadNotificationsList() {
    const list = document.getElementById('notif-items');
    if (!list) return;

    list.innerHTML = `<p class="text-xs text-slate-400 text-center py-4">Loading notifications...</p>`;

    try {
        const res = await apiCall('/notifications', { skipTenant: true });
        const notifications = res.data || [];

        if (notifications.length === 0) {
            list.innerHTML = `<p class="text-xs text-slate-400 text-center py-4">No notifications</p>`;
            return;
        }

        list.innerHTML = notifications.map(n => `
            <div class="p-2.5 rounded-xl border transition ${n.read ? 'bg-white border-slate-100 opacity-75' : 'bg-brand-50/50 border-brand-100 font-semibold'}">
                <div class="flex items-center justify-between mb-1">
                    <span class="text-xs text-slate-900">${escapeHtml(n.title)}</span>
                    <span class="text-[10px] text-slate-400">${formatTimeAgo(n.createdAt)}</span>
                </div>
                <p class="text-[11px] text-slate-600">${escapeHtml(n.message)}</p>
            </div>
        `).join('');
    } catch (err) {
        list.innerHTML = `<p class="text-xs text-rose-500 text-center py-4">Error loading notifications</p>`;
    }
}

async function markAllNotificationsRead() {
    try {
        await apiCall('/notifications/mark-all-read', { method: 'PUT', skipTenant: true });
        await loadUnreadNotificationsCount();
        await loadNotificationsList();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ==========================================
// 10. DEV EMAIL INBOX & LINK SIMULATOR
// ==========================================
function startDevPolling() {
    pollDevTokens();
    if (!state.devPollingInterval) {
        state.devPollingInterval = setInterval(pollDevTokens, 5000);
    }
}

async function pollDevTokens() {
    try {
        const res = await apiCall('/dev/tokens', { skipTenant: true });
        const tokens = res.data || [];

        const badge = document.getElementById('dev-links-badge');
        if (badge) badge.textContent = tokens.length;

        renderDevLinksList(tokens);
    } catch (e) {
        // Development token endpoint might be disabled in non-dev profiles
    }
}

function renderDevLinksList(tokens) {
    const list = document.getElementById('dev-links-list');
    if (!list) return;

    if (!tokens || tokens.length === 0) {
        list.innerHTML = `<p class="text-xs text-slate-400 text-center py-8">No links captured yet</p>`;
        return;
    }

    const typeBadges = {
        EMAIL_VERIFICATION: 'bg-blue-100 text-blue-700 border-blue-200',
        PASSWORD_RESET: 'bg-amber-100 text-amber-700 border-amber-200',
        INVITATION: 'bg-purple-100 text-purple-700 border-purple-200'
    };

    list.innerHTML = tokens.map(item => `
        <div class="bg-slate-50 p-3 rounded-xl border border-slate-200 text-xs space-y-2">
            <div class="flex items-center justify-between">
                <span class="text-[10px] font-bold px-2 py-0.5 rounded-full border ${typeBadges[item.type] || 'bg-slate-100'}">
                    ${escapeHtml(item.type)}
                </span>
                <span class="text-[10px] text-slate-400">${formatTimeAgo(item.createdAt)}</span>
            </div>

            <div class="text-slate-800">
                <div class="font-bold">${escapeHtml(item.subject)}</div>
                <div class="text-slate-500 text-[11px]">To: ${escapeHtml(item.recipientEmail)}</div>
            </div>

            <div class="flex items-center gap-2 pt-1 border-t border-slate-200/60">
                <a href="${item.link}" target="_blank"
                   class="flex-1 text-center py-1.5 px-3 bg-brand-600 hover:bg-brand-700 text-white font-semibold rounded-lg text-xs transition">
                    Open Action Link ↗
                </a>
                <button class="copy-token-btn px-2.5 py-1.5 bg-white border border-slate-200 rounded-lg text-slate-700 hover:bg-slate-100"
                        data-token="${escapeHtml(item.rawToken || '')}" title="Copy Raw Token">
                    📋
                </button>
            </div>
        </div>
    `).join('');

    list.querySelectorAll('.copy-token-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const token = btn.dataset.token;
            navigator.clipboard.writeText(token).then(() => {
                showToast('Token copied to clipboard!', 'info');
            });
        });
    });
}

async function clearDevInbox() {
    try {
        await apiCall('/dev/tokens', { method: 'DELETE', skipTenant: true });
        showToast('Dev inbox cleared', 'info');
        await pollDevTokens();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ==========================================
// 11. MODAL HELPERS
// ==========================================
function openModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.classList.remove('hidden');
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.classList.add('hidden');
}

// ==========================================
// 12. EVENT LISTENERS BINDING
// ==========================================
function bindEventListeners() {
    // Auth Tab switching
    const tabLogin = document.getElementById('tab-login');
    const tabRegister = document.getElementById('tab-register');
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');

    if (tabLogin && tabRegister) {
        tabLogin.addEventListener('click', () => {
            tabLogin.className = 'flex-1 pb-3 text-sm font-semibold text-brand-600 border-b-2 border-brand-600';
            tabRegister.className = 'flex-1 pb-3 text-sm font-semibold text-slate-400 hover:text-slate-600 border-b-2 border-transparent';
            loginForm.classList.remove('hidden');
            registerForm.classList.add('hidden');
        });

        tabRegister.addEventListener('click', () => {
            tabRegister.className = 'flex-1 pb-3 text-sm font-semibold text-brand-600 border-b-2 border-brand-600';
            tabLogin.className = 'flex-1 pb-3 text-sm font-semibold text-slate-400 hover:text-slate-600 border-b-2 border-transparent';
            registerForm.classList.remove('hidden');
            loginForm.classList.add('hidden');
        });
    }

    // Forms
    loginForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('login-email').value.trim();
        const pwd = document.getElementById('login-password').value;
        await loginUser(email, pwd);
    });

    registerForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const name = document.getElementById('reg-name').value.trim();
        const email = document.getElementById('reg-email').value.trim();
        const pwd = document.getElementById('reg-password').value;
        await registerUser(name, email, pwd);
    });

    // Forgot password
    document.getElementById('open-forgot-pwd')?.addEventListener('click', () => openModal('forgot-modal'));
    document.getElementById('close-forgot')?.addEventListener('click', () => closeModal('forgot-modal'));
    document.getElementById('forgot-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('forgot-email').value.trim();
        try {
            await apiCall('/auth/forgot-password', {
                method: 'POST',
                body: JSON.stringify({ email }),
                skipTenant: true
            });
            showToast('Password reset link sent to Dev Inbox!', 'success');
            closeModal('forgot-modal');
            await pollDevTokens();
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    // Logout
    document.getElementById('btn-logout')?.addEventListener('click', logout);

    // Organization Switcher
    const orgSwitcherBtn = document.getElementById('org-switcher-btn');
    const orgDropdown = document.getElementById('org-dropdown');
    orgSwitcherBtn?.addEventListener('click', (e) => {
        e.stopPropagation();
        orgDropdown.classList.toggle('hidden');
    });

    document.addEventListener('click', (e) => {
        if (!orgDropdown?.contains(e.target) && !orgSwitcherBtn?.contains(e.target)) {
            orgDropdown?.classList.add('hidden');
        }
    });

    document.getElementById('btn-open-create-org')?.addEventListener('click', () => {
        orgDropdown.classList.add('hidden');
        openModal('create-org-modal');
    });

    document.getElementById('create-org-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const name = document.getElementById('new-org-name').value.trim();
        const slug = document.getElementById('new-org-slug').value.trim();
        const desc = document.getElementById('new-org-desc').value.trim();
        await createOrganization(name, slug, desc);
    });

    // Workspace Navigation Tabs
    document.querySelectorAll('.nav-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('.nav-tab').forEach(t => {
                t.className = 'nav-tab py-3 text-sm font-semibold border-b-2 border-transparent text-slate-500 hover:text-slate-800';
            });
            tab.className = 'nav-tab active-tab py-3 text-sm font-semibold border-b-2 border-brand-600 text-brand-600';

            const targetView = tab.dataset.view;
            document.querySelectorAll('.workspace-section').forEach(sec => sec.classList.add('hidden'));
            document.getElementById(targetView)?.classList.remove('hidden');

            if (targetView === 'team-view') {
                loadMembers();
                loadRoles();
            } else if (targetView === 'audit-view') {
                loadAuditLogs();
            } else if (targetView === 'projects-view') {
                loadTasks();
            }
        });
    });

    // Project selection
    document.getElementById('project-selector')?.addEventListener('change', async (e) => {
        const projId = e.target.value;
        state.currentProject = state.projects.find(p => p.id === projId) || null;
        if (state.currentProject) {
            updateProjectStatusBadge(state.currentProject.status);
        }
        await loadTasks();
    });

    // Create Project
    document.getElementById('btn-open-create-project')?.addEventListener('click', () => openModal('create-project-modal'));
    document.getElementById('create-project-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const name = document.getElementById('new-proj-name').value.trim();
        const description = document.getElementById('new-proj-desc').value.trim();
        const status = document.getElementById('new-proj-status').value;
        const managerId = document.getElementById('new-proj-manager').value || null;

        try {
            const res = await apiCall('/projects', {
                method: 'POST',
                body: JSON.stringify({ name, description, status, managerId })
            });
            showToast(`Project "${res.data.name}" created!`, 'success');
            closeModal('create-project-modal');
            await loadProjects();
            state.currentProject = res.data;
            document.getElementById('project-selector').value = res.data.id;
            updateProjectStatusBadge(res.data.status);
            await loadTasks();
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    // Create Task
    document.getElementById('btn-open-create-task')?.addEventListener('click', () => {
        if (!state.currentProject) {
            showToast('Please create or select a project first', 'warning');
            return;
        }
        openModal('create-task-modal');
    });

    document.getElementById('create-task-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (!state.currentProject) return;

        const title = document.getElementById('new-task-title').value.trim();
        const description = document.getElementById('new-task-desc').value.trim();
        const status = document.getElementById('new-task-status').value;
        const priority = document.getElementById('new-task-priority').value;
        const assigneeId = document.getElementById('new-task-assignee').value || null;

        try {
            await apiCall('/tasks', {
                method: 'POST',
                body: JSON.stringify({
                    projectId: state.currentProject.id,
                    title,
                    description,
                    status,
                    priority,
                    assigneeId
                })
            });
            showToast('Task created successfully', 'success');
            closeModal('create-task-modal');
            document.getElementById('create-task-form').reset();
            await loadTasks();
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    // Task Detail Controls
    document.getElementById('detail-task-status-select')?.addEventListener('change', async (e) => {
        if (!state.activeTaskId) return;
        await updateTaskStatusQuick(state.activeTaskId, e.target.value);
    });

    document.getElementById('detail-task-assignee-select')?.addEventListener('change', async (e) => {
        if (!state.activeTaskId) return;
        const assigneeId = e.target.value || null;
        try {
            await apiCall(`/tasks/${state.activeTaskId}`, {
                method: 'PATCH',
                body: JSON.stringify({ assigneeId })
            });
            showToast('Assignee updated', 'success');
            await loadTasks();
            await loadTaskCommentsAndActivities(state.activeTaskId);
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    // Add Comment Form
    document.getElementById('add-comment-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (!state.activeTaskId) return;

        const input = document.getElementById('new-comment-input');
        const content = input.value.trim();
        if (!content) return;

        try {
            await apiCall(`/tasks/${state.activeTaskId}/comments`, {
                method: 'POST',
                body: JSON.stringify({ content })
            });
            input.value = '';
            await loadTaskCommentsAndActivities(state.activeTaskId);
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    // Filter Task Status
    document.getElementById('filter-task-status')?.addEventListener('change', loadTasks);

    // Filter Audit Resource
    document.getElementById('filter-audit-resource')?.addEventListener('change', loadAuditLogs);

    // Team & Roles Modals
    document.getElementById('btn-open-invite')?.addEventListener('click', () => openModal('invite-member-modal'));
    document.getElementById('invite-member-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('invite-email').value.trim();
        const roleId = document.getElementById('invite-role-select').value;
        await inviteMember(email, roleId);
    });

    document.getElementById('btn-open-create-role')?.addEventListener('click', () => openModal('create-role-modal'));
    document.getElementById('create-role-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const name = document.getElementById('custom-role-name').value.trim();
        const description = document.getElementById('custom-role-desc').value.trim();
        const checkboxes = document.querySelectorAll('input[name="role-perm"]:checked');
        const permissions = Array.from(checkboxes).map(c => c.value);

        if (permissions.length === 0) {
            showToast('Please select at least one permission for this role', 'warning');
            return;
        }

        await createCustomRole(name, description, permissions);
    });

    // Notifications Dropdown
    const notifBtn = document.getElementById('notif-btn');
    const notifDropdown = document.getElementById('notif-dropdown');
    notifBtn?.addEventListener('click', async (e) => {
        e.stopPropagation();
        notifDropdown.classList.toggle('hidden');
        if (!notifDropdown.classList.contains('hidden')) {
            await loadNotificationsList();
        }
    });

    document.addEventListener('click', (e) => {
        if (!notifDropdown?.contains(e.target) && !notifBtn?.contains(e.target)) {
            notifDropdown?.classList.add('hidden');
        }
    });

    document.getElementById('btn-mark-all-read')?.addEventListener('click', markAllNotificationsRead);

    // Dev Drawer Controls
    const devDrawerBtn = document.getElementById('dev-drawer-btn');
    const devDrawerModal = document.getElementById('dev-drawer-modal');
    const closeDevDrawer = document.getElementById('close-dev-drawer');

    devDrawerBtn?.addEventListener('click', () => {
        devDrawerModal.classList.remove('hidden');
        pollDevTokens();
    });

    closeDevDrawer?.addEventListener('click', () => {
        devDrawerModal.classList.add('hidden');
    });

    document.getElementById('refresh-dev-links')?.addEventListener('click', pollDevTokens);
    document.getElementById('clear-dev-links')?.addEventListener('click', clearDevInbox);

    // Generic Modal Close buttons
    document.querySelectorAll('.btn-close-modal').forEach(btn => {
        btn.addEventListener('click', () => {
            btn.closest('[id$="-modal"]')?.classList.add('hidden');
        });
    });
}

// Start application when DOM is ready
document.addEventListener('DOMContentLoaded', initApp);
