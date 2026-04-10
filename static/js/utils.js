window.AppState = window.AppState || {
  portfolio: [],
  quotes: [],
  countdown: 60,
  cdTimer: null,
  editCode: null,
  wlTab: 'holding',
  prevPrices: {},
  trumpTimer: null,
};

const AppState = window.AppState;

const fmt = n => n == null ? '--' : '\u00a5' + Math.round(n).toLocaleString('ja-JP');
const fmtK = n => {
  if (n == null) return '--';
  const abs = Math.abs(n);
  if (abs >= 1e8) return (n / 1e8).toFixed(2) + '\u4ebf';
  if (abs >= 1e4) return (n / 1e4).toFixed(1) + '\u4e07';
  return Math.round(n).toLocaleString('ja-JP');
};

function getApiBase() {
  const raw = window.__APP_API_BASE__ || document.body?.dataset?.apiBase || '';
  return String(raw || '').replace(/\/+$/, '');
}

function apiUrl(url) {
  if (typeof url !== 'string') return url;
  if (!url.startsWith('/api/')) return url;
  const base = getApiBase();
  return base ? `${base}${url}` : url;
}

function camelToSnake(key) {
  return String(key || '')
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/-/g, '_')
    .toLowerCase();
}

function addCompatibilityAliases(value) {
  if (Array.isArray(value)) return value.map(addCompatibilityAliases);
  if (!value || typeof value !== 'object') return value;

  const next = {};
  Object.entries(value).forEach(([key, raw]) => {
    const normalized = addCompatibilityAliases(raw);
    next[key] = normalized;
    const snakeKey = camelToSnake(key);
    if (!(snakeKey in next)) next[snakeKey] = normalized;
  });
  return next;
}

function unwrapApiPayload(payload, options = {}) {
  const { legacyAliases = true } = options;
  if (payload && typeof payload === 'object' && typeof payload.success === 'boolean') {
    if (!payload.success) {
      throw new Error(payload.message || payload.code || '请求处理失败');
    }
    return legacyAliases ? addCompatibilityAliases(payload.data) : payload.data;
  }
  return legacyAliases ? addCompatibilityAliases(payload) : payload;
}

function sparkPath(closes) {
  const vals = closes.filter(v => v != null);
  if (vals.length < 2) return '';
  const min = Math.min(...vals);
  const max = Math.max(...vals);
  const range = max - min || 1;
  const W = 170;
  const H = 30;
  const pts = vals.map((v, i) => {
    const x = (i / (vals.length - 1)) * W;
    const y = H - ((v - min) / range) * H;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  return `M ${pts.join(' L ')}`;
}

async function fetchJson(url, options = {}) {
  const { legacyAliases = true, ...fetchOptions } = options || {};
  const res = await fetch(apiUrl(url), fetchOptions);
  const text = await res.text();
  const ct = (res.headers.get('content-type') || '').toLowerCase();

  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const data = text ? JSON.parse(text) : null;
      if (data && (data.error || data.message || data.code)) {
        message = data.error || data.message || data.code;
      }
    } catch (_) {
      if (text) message = text.slice(0, 120);
    }
    throw new Error(message);
  }

  if (!ct.includes('application/json')) {
    if (text.trim().startsWith('<')) {
      throw new Error('\u8fd4\u56de\u4e86 HTML \u800c\u4e0d\u662f JSON\uff0c\u8bf7\u786e\u8ba4\u670d\u52a1\u5df2\u6b63\u786e\u542f\u52a8\u3002');
    }
    throw new Error('\u8fd4\u56de\u5185\u5bb9\u4e0d\u662f JSON \u683c\u5f0f');
  }

  try {
    return unwrapApiPayload(text ? JSON.parse(text) : null, { legacyAliases });
  } catch (_) {
    const preview = text ? text.trim().slice(0, 120) : '';
    throw new Error(preview ? `JSON 解析失败: ${preview}` : 'JSON 解析失败');
  }
}

function fetchApiJson(url, options = {}) {
  return fetchJson(url, { ...options, legacyAliases: false });
}

function applyGlobalTheme(theme, options = {}) {
  const { persist = true } = options;
  const nextTheme = theme === 'light' ? 'light' : 'dark';
  document.documentElement.dataset.theme = nextTheme;
  document.body.dataset.theme = nextTheme;
  document.querySelectorAll('[data-global-theme-toggle]').forEach(btn => {
    btn.setAttribute('aria-pressed', nextTheme === 'light' ? 'true' : 'false');
  });
  if (persist) {
    try {
      window.localStorage.setItem('global-theme', nextTheme);
    } catch (_) {}
  }
  window.dispatchEvent(new CustomEvent('global-theme:change', { detail: { theme: nextTheme } }));
}

function initGlobalThemeToggle() {
  const toggles = Array.from(document.querySelectorAll('[data-global-theme-toggle]'));
  let nextTheme = document.body.dataset.theme || 'dark';
  try {
    nextTheme = window.localStorage.getItem('global-theme') || nextTheme;
  } catch (_) {}
  applyGlobalTheme(nextTheme);

  if (!toggles.length) return;

  toggles.forEach(btn => {
    if (btn.dataset.themeBound === '1') return;
    btn.dataset.themeBound = '1';
    btn.addEventListener('click', () => {
      applyGlobalTheme(document.body.dataset.theme === 'light' ? 'dark' : 'light');
    });
  });

  if (window.__globalThemeStorageBound === '1') return;
  window.__globalThemeStorageBound = '1';
  window.addEventListener('storage', event => {
    if (event.key !== 'global-theme' || !event.newValue) return;
    applyGlobalTheme(event.newValue, { persist: false });
  });
}

window.applyGlobalTheme = applyGlobalTheme;
window.initGlobalThemeToggle = initGlobalThemeToggle;
window.apiUrl = apiUrl;
window.fetchJson = fetchJson;
window.fetchApiJson = fetchApiJson;
