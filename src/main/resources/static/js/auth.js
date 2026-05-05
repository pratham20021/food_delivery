/* ═══════════════════════════════════════════════════════════════
   auth.js — Shared auth state, API helper, toast, nav update
   Loaded on every page via <script src="../js/auth.js">
═══════════════════════════════════════════════════════════════ */

const API_BASE = '/api';

// ── Token helpers ─────────────────────────────────────────────
function getToken()   { return localStorage.getItem('token'); }
function getUser()    { return JSON.parse(localStorage.getItem('user') || 'null'); }
function isLoggedIn() { return !!getToken(); }

function saveAuth(token, user) {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
}

function clearAuth() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
}

// ── API fetch wrapper ─────────────────────────────────────────
async function apiFetch(path, method = 'GET', body = null, auth = true) {
    const headers = { 'Content-Type': 'application/json' };
    if (auth && getToken()) headers['Authorization'] = `Bearer ${getToken()}`;

    const res = await fetch(API_BASE + path, {
        method,
        headers,
        body: body ? JSON.stringify(body) : null
    });

    const data = await res.json();

    if (res.status === 401) {
        clearAuth();
        window.location.href = getRelativePath('pages/login.html');
        return;
    }

    if (!res.ok || !data.success) {
        throw new Error(data.message || 'Request failed');
    }

    return data;
}

// ── Relative path helper (works from root and /pages/) ────────
function getRelativePath(path) {
    const isInPages = window.location.pathname.includes('/pages/');
    return isInPages ? '../' + path : path;
}

// ── Toast notifications ───────────────────────────────────────
function showToast(message, type = 'default') {
    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = `toast ${type === 'success' ? 'toast-success' : type === 'error' ? 'toast-error' : ''}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 3200);
}

// ── Update navbar based on auth state ────────────────────────
function updateNavAuth() {
    const user = getUser();
    const guestEl  = document.getElementById('guestActions');
    const userEl   = document.getElementById('userActions');
    const nameEl   = document.getElementById('navUserName');
    const ordersEl = document.getElementById('ordersNavLink');

    if (user && isLoggedIn()) {
        if (guestEl)  guestEl.style.display  = 'none';
        if (userEl)   userEl.style.display   = 'flex';
        if (nameEl)   nameEl.textContent     = `👋 ${user.name}`;
        if (ordersEl) ordersEl.style.display = 'block';
    } else {
        if (guestEl)  guestEl.style.display  = 'flex';
        if (userEl)   userEl.style.display   = 'none';
        if (ordersEl) ordersEl.style.display = 'none';
    }
}

// ── Require auth — redirect to login if not logged in ─────────
function requireAuth() {
    if (!isLoggedIn()) {
        window.location.href = getRelativePath('pages/login.html');
        return false;
    }
    return true;
}

// ── Logout ────────────────────────────────────────────────────
function logout() {
    clearAuth();
    window.location.href = getRelativePath('index.html');
}

// ── Format currency ───────────────────────────────────────────
function formatPrice(n) { return `$${Number(n).toFixed(2)}`; }

// ── Format date ───────────────────────────────────────────────
function formatDate(iso) {
    return new Date(iso).toLocaleString('en-US', {
        month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

// ── Run on every page load ────────────────────────────────────
document.addEventListener('DOMContentLoaded', updateNavAuth);
