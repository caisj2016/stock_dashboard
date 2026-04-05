const ScreenerPage = {
  mode: 'oversold',
  universe: 'core45',
  adding: new Set(),
};

const ScreenerUniverseMeta = {
  core45: {
    label: '核心45',
  },
  nikkei225: {
    label: 'Nikkei 225样本45',
  },
  topixcore: {
    label: 'TOPIX Core 30',
  },
};

ScreenerUniverseMeta.nikkei225 = {
  label: 'Nikkei 225',
};

function priceScale(values, height, topPad = 4, bottomPad = 4) {
  const vals = (values || []).filter(v => v != null);
  if (!vals.length) return () => height / 2;
  const min = Math.min(...vals);
  const max = Math.max(...vals);
  const range = max - min || 1;
  return value => topPad + (max - value) / range * (height - topPad - bottomPad);
}

function linePath(values, width, height) {
  const vals = (values || []).filter(v => v != null);
  if (vals.length < 2) return '';
  const min = Math.min(...vals);
  const max = Math.max(...vals);
  const range = max - min || 1;
  return vals.map((v, i) => {
    const x = (i / (vals.length - 1)) * width;
    const y = height - ((v - min) / range) * height;
    return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
  }).join(' ');
}

function renderCandles(opens, highs, lows, closes, width, height) {
  if (!opens?.length || !highs?.length || !lows?.length || !closes?.length) return '';
  const scale = priceScale([...highs, ...lows], height);
  const step = width / opens.length;
  const candleWidth = Math.max(4, step * 0.55);
  return opens.map((open, i) => {
    const close = closes[i];
    const high = highs[i];
    const low = lows[i];
    const x = i * step + step / 2;
    const openY = scale(open);
    const closeY = scale(close);
    const highY = scale(high);
    const lowY = scale(low);
    const bodyTop = Math.min(openY, closeY);
    const bodyHeight = Math.max(1.5, Math.abs(openY - closeY));
    const fill = close >= open ? '#22c55e' : '#ef4444';
    return `
      <line x1="${x.toFixed(1)}" y1="${highY.toFixed(1)}" x2="${x.toFixed(1)}" y2="${lowY.toFixed(1)}" stroke="${fill}" stroke-width="1.2" opacity="0.9" />
      <rect x="${(x - candleWidth / 2).toFixed(1)}" y="${bodyTop.toFixed(1)}" width="${candleWidth.toFixed(1)}" height="${bodyHeight.toFixed(1)}" rx="1" fill="${fill}" opacity="0.82" />
    `;
  }).join('');
}

function volumeBars(values, width, height) {
  const vals = (values || []).filter(v => v != null);
  if (!vals.length) return '';
  const max = Math.max(...vals) || 1;
  const barWidth = Math.max(2, width / vals.length - 1);
  return vals.map((v, i) => {
    const h = (v / max) * height;
    const x = i * (width / vals.length);
    const y = height - h;
    return `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${barWidth.toFixed(1)}" height="${h.toFixed(1)}" rx="1" />`;
  }).join('');
}

function macdBars(values, width, height) {
  const vals = values || [];
  if (!vals.length) return '';
  const maxAbs = Math.max(...vals.map(v => Math.abs(v))) || 1;
  const mid = height / 2;
  const barWidth = Math.max(2, width / vals.length - 1);
  return vals.map((v, i) => {
    const h = (Math.abs(v) / maxAbs) * (height / 2 - 2);
    const x = i * (width / vals.length);
    const y = v >= 0 ? mid - h : mid;
    const fill = v >= 0 ? '#22c55e' : '#ef4444';
    return `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${barWidth.toFixed(1)}" height="${h.toFixed(1)}" rx="1" fill="${fill}" opacity="0.75" />`;
  }).join('');
}

function updateUniverseHeader(label) {
  const pill = document.getElementById('screenerStatusPill');
  if (pill) pill.textContent = `股票池：${label || '--'}`;
}

function bindScreenerControls() {
  document.querySelectorAll('[data-mode]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-mode]').forEach(item => item.classList.remove('active'));
      btn.classList.add('active');
      ScreenerPage.mode = btn.dataset.mode || 'oversold';
      loadScreenerResults();
    });
  });

  document.querySelectorAll('[data-universe]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-universe]').forEach(item => item.classList.remove('active'));
      btn.classList.add('active');
      ScreenerPage.universe = btn.dataset.universe || 'core45';
      const meta = ScreenerUniverseMeta[ScreenerPage.universe] || ScreenerUniverseMeta.core45;
      updateUniverseHeader(meta.label);
      loadScreenerResults();
    });
  });

  const refreshBtn = document.getElementById('screenerRefreshBtn');
  if (refreshBtn) refreshBtn.addEventListener('click', () => loadScreenerResults());
}

function renderScreenerResults(data) {
  const items = Array.isArray(data.items) ? data.items : [];
  const warning = data.warning
    ? `<div class="ai-placeholder" style="text-align:left;padding:0 0 12px 0;color:#fbbf24">${data.warning}</div>`
    : '';

  updateUniverseHeader(data.universe || (ScreenerUniverseMeta[ScreenerPage.universe]?.label ?? '--'));

  if (!items.length) {
    return warning + `<div class="ai-placeholder">当前没有筛选到符合条件的股票，可以切换股票池或策略后再试。</div>`;
  }

  return `
    ${warning}
    <div class="screener-result-meta">
      <span>股票池：${data.universe || '--'} · ${data.universe_size || 0} 只</span>
      <span>命中：${data.count || 0} 只</span>
    </div>
    <div class="screener-results-grid">
      ${items.map(renderScreenerCard).join('')}
    </div>
  `;
}

function renderScreenerCard(item) {
  const signals = Array.isArray(item.signals) ? item.signals : [];
  const changePct = item.change_pct != null ? `${item.change_pct > 0 ? '+' : ''}${item.change_pct.toFixed(2)}%` : '--';
  const changeCls = item.change_pct > 0 ? 'up' : (item.change_pct < 0 ? 'down' : 'neutral');
  const volumeSvg = volumeBars(item.volumes_20 || [], 220, 48);
  const macdHist = macdBars(item.hist_series || [], 220, 58);
  const macdPath = linePath(item.macd_series || [], 220, 58);
  const signalPath = linePath(item.signal_series || [], 220, 58);
  const candles = renderCandles(item.opens_20 || [], item.highs_20 || [], item.lows_20 || [], item.closes_20 || [], 220, 76);
  const ma20Series = Array.isArray(item.ma20_series) && item.ma20_series.length ? item.ma20_series : (item.closes_20 || []);
  const ma20Path = linePath(ma20Series, 220, 76);
  const isAdding = ScreenerPage.adding.has(item.symbol);
  const isSaved = !!item.in_watchlist;
  const disabled = isAdding || isSaved ? 'disabled' : '';
  const btnLabel = isSaved ? '已加入观察股' : (isAdding ? '加入中...' : '加入观察股');
  const chartUrl = `/chart?symbol=${encodeURIComponent(item.symbol)}&name=${encodeURIComponent(item.name || item.symbol)}`;

  return `
    <div class="screener-result-card" role="button" tabindex="0" onclick="openScreenerChart('${item.symbol}', '${(item.name || item.symbol).replace(/'/g, "\\'")}')" onkeydown="handleScreenerCardKey(event, '${item.symbol}', '${(item.name || item.symbol).replace(/'/g, "\\'")}')">
      <div class="screener-result-head">
        <div>
          <div class="screener-result-symbol">${item.symbol}</div>
          <div class="screener-result-name">${item.name || item.symbol}</div>
        </div>
        <div class="screener-result-price">
          <div>¥${item.price != null ? item.price.toLocaleString('ja-JP') : '--'}</div>
          <div class="wl-pct ${changeCls}">${changePct}</div>
        </div>
      </div>

      <div class="digest-driver-list">
        ${signals.map(signal => `<span class="digest-driver">${signal}</span>`).join('')}
      </div>

      <div class="screener-chart-grid">
        <div class="screener-chart-card screener-chart-main">
          <div class="screener-chart-title">K线 + MA20</div>
          <svg viewBox="0 0 220 76" class="screener-chart-svg">
            ${candles}
            <path d="${ma20Path}" fill="none" stroke="#f59e0b" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" opacity="0.95" />
          </svg>
        </div>
        <div class="screener-chart-card">
          <div class="screener-chart-title">量能</div>
          <svg viewBox="0 0 220 48" class="screener-chart-svg screener-bars">${volumeSvg}</svg>
        </div>
        <div class="screener-chart-card">
          <div class="screener-chart-title">MACD</div>
          <svg viewBox="0 0 220 58" class="screener-chart-svg">
            <line x1="0" y1="29" x2="220" y2="29" stroke="rgba(255,255,255,0.08)" />
            ${macdHist}
            <path d="${macdPath}" fill="none" stroke="#60a5fa" stroke-width="1.8" stroke-linecap="round" />
            <path d="${signalPath}" fill="none" stroke="#f59e0b" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </div>
      </div>

      <div class="screener-metrics">
        <span>RSI: ${item.rsi14 ?? '--'}</span>
        <span>MACD: ${item.macd ?? '--'}</span>
        <span>Signal: ${item.macd_signal ?? '--'}</span>
        <span>量比: ${item.volume_ratio ?? '--'}x</span>
        <span>MA20: ${item.ma20 ?? '--'}</span>
      </div>

      <div class="screener-actions">
        <span class="screener-card-tip">点击卡片可直接打开大图</span>
        <button class="ai-btn ${isSaved ? 'is-saved' : ''}" ${disabled} onclick="event.stopPropagation(); addScreenerStock('${item.symbol}', '${(item.name || '').replace(/'/g, "\\'")}')">${btnLabel}</button>
        <a class="page-link" href="${chartUrl}" onclick="event.stopPropagation()">查看大图</a>
        ${isSaved ? `<a class="page-link" href="/" onclick="event.stopPropagation()">回主页面</a>` : ''}
      </div>
    </div>
  `;
}

function openScreenerChart(symbol, name) {
  window.location.href = `/chart?symbol=${encodeURIComponent(symbol)}&name=${encodeURIComponent(name || symbol)}`;
}

function handleScreenerCardKey(event, symbol, name) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    openScreenerChart(symbol, name);
  }
}

async function addScreenerStock(symbol, name) {
  if (ScreenerPage.adding.has(symbol)) return;
  ScreenerPage.adding.add(symbol);
  loadScreenerResults();
  try {
    const res = await fetch('/api/add_stock', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: symbol, name }),
    });
    const data = await res.json();
    if (!data.ok && data.error !== 'already exists') {
      throw new Error(data.error || '加入失败');
    }
  } catch (e) {
    alert(`加入失败：${e.message}`);
  } finally {
    ScreenerPage.adding.delete(symbol);
    loadScreenerResults();
  }
}

async function loadScreenerResults() {
  const body = document.getElementById('screenerResultsBody');
  const lastFetch = document.getElementById('screenerLastFetch');
  const refreshBtn = document.getElementById('screenerRefreshBtn');
  if (!body) return;
  if (refreshBtn) refreshBtn.disabled = true;
  body.innerHTML = `<div class="ai-loading"><div class="ai-spinner"></div>正在从股票池中寻找符合条件的结果...</div>`;
  try {
    const data = await fetchJson(`/api/screener?mode=${encodeURIComponent(ScreenerPage.mode)}&universe=${encodeURIComponent(ScreenerPage.universe)}&limit=18`);
    body.innerHTML = renderScreenerResults(data);
    if (lastFetch && data.updated) lastFetch.textContent = `更新: ${data.updated}`;
  } catch (e) {
    body.innerHTML = `<div class="ai-placeholder" style="color:var(--down)">获取失败: ${e.message}</div>`;
  }
  if (refreshBtn) refreshBtn.disabled = false;
}

bindScreenerControls();
updateUniverseHeader(ScreenerUniverseMeta.core45.label);
loadScreenerResults();
