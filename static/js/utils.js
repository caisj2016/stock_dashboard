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

async function fetchJson(url, options) {
  const res = await fetch(url, options);
  const text = await res.text();
  const ct = (res.headers.get('content-type') || '').toLowerCase();

  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const data = text ? JSON.parse(text) : null;
      if (data && data.error) message = data.error;
    } catch (_) {
      if (text) message = text.slice(0, 120);
    }
    throw new Error(message);
  }

  if (!ct.includes('application/json')) {
    if (text.trim().startsWith('<')) {
      throw new Error('\u8fd4\u56de\u4e86 HTML \u800c\u4e0d\u662f JSON\uff0c\u8bf7\u786e\u8ba4\u670d\u52a1\u5df2\u5728 http://localhost:5555 \u6b63\u786e\u542f\u52a8\u3002');
    }
    throw new Error('\u8fd4\u56de\u5185\u5bb9\u4e0d\u662f JSON \u683c\u5f0f');
  }

  try {
    return text ? JSON.parse(text) : null;
  } catch (_) {
    throw new Error('JSON \u89e3\u6790\u5931\u8d25');
  }
}
