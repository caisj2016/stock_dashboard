function renderCard(q, idx) {
  const up = q.change > 0;
  const down = q.change < 0;
  const cls = up ? 'up-card' : (down ? 'down-card' : '');
  const chgCls = up ? 'up' : (down ? 'down' : 'neutral');
  const status = q.status || (q.shares > 0 ? 'holding' : 'watch');
  const statusLabel = status === 'holding' ? '\u5df2\u6301\u6709' : '\u89c2\u5bdf\u4e2d';
  const price = q.price != null ? '\u00a5' + q.price.toLocaleString('ja-JP') : '--';
  const chg = q.change != null ? (up ? '+' : '') + Math.round(q.change).toLocaleString() : '--';
  const pct = q.pct != null ? (up ? '+' : '') + q.pct.toFixed(2) + '%' : '--';
  const spark = (q.closes && q.closes.length > 1) ? sparkPath(q.closes) : '';
  const sparkColor = up ? '#22c55e' : (down ? '#ef4444' : '#6b7280');
  const delay = idx * 0.04;

  let holdingHtml = '';
  if (status === 'holding' && q.shares && q.shares > 0) {
    const pnlCls = q.pnl > 0 ? 'up' : (q.pnl < 0 ? 'down' : '');
    const pnlSign = q.pnl > 0 ? '+' : '';
    holdingHtml = `
    <div class="holdings">
      <div class="holding-row"><span class="holding-key">\u6301\u80a1\u6570</span><span class="holding-val">${q.shares.toLocaleString()} \u80a1</span></div>
      <div class="holding-row"><span class="holding-key">\u6210\u672c\u4ef7</span><span class="holding-val">\u00a5${q.cost.toLocaleString()}</span></div>
      <div class="holding-row"><span class="holding-key">\u5e02\u573a\u4ef7\u503c</span><span class="holding-val">\u00a5${fmtK(q.market_value)}</span></div>
      <div class="holding-row"><span class="holding-key">\u76c8\u4e8f</span><span class="holding-val ${pnlCls}">${pnlSign}\u00a5${fmtK(q.pnl)} (${pnlSign}${q.pnl_pct?.toFixed(2)}%)</span></div>
    </div>`;
  } else {
    holdingHtml = `<div class="no-holding">\u5f53\u524d\u6807\u8bb0\u4e3a\u89c2\u5bdf\u4e2d</div>`;
  }

  return `
  <div class="card ${cls}" style="animation-delay:${delay}s" id="c-${q.symbol.replace('.', '_')}">
    <button class="card-remove" title="\u5220\u9664" onclick="removeStock('${q.symbol}')">\u00d7</button>
    <div class="card-ticker">${q.symbol}</div>
    <div class="card-name">${q.name || q.symbol}</div>
    <div class="card-status ${status}">${statusLabel}</div>
    <div class="card-price">${price}</div>
    <div class="card-change">
      <span class="chg ${chgCls}">${chg}</span>
      <span class="chg ${chgCls}">${pct}</span>
    </div>
    ${spark ? `<svg class="spark" viewBox="0 0 170 30" preserveAspectRatio="none">
      <path d="${spark}" fill="none" stroke="${sparkColor}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.8"/>
    </svg>` : '<div style="height:32px"></div>'}
    ${holdingHtml}
    <button class="chart-link-btn" onclick="openChartModal('${q.symbol}', '${(q.name || q.symbol).replace(/'/g, "\\'")}')">\u5feb\u901f\u770b\u56fe</button>
    <a class="chart-link-btn" href="/chart?symbol=${encodeURIComponent(q.symbol)}&name=${encodeURIComponent(q.name || q.symbol)}">\u6253\u5f00\u4e13\u4e1a K \u7ebf</a>
    <button class="edit-btn" onclick="openModal('${q.symbol}', '${(q.name || '').replace(/'/g, "\\'")}')">\u270e \u7f16\u8f91\u72b6\u6001 / \u6301\u4ed3</button>
  </div>`;
}

function updateSummary(qs) {
  const holdings = qs.filter(q => (q.status || (q.shares > 0 ? 'holding' : 'watch')) === 'holding');
  let totalMV = 0;
  let totalCost = 0;
  let totalPnl = 0;
  const dayPcts = [];

  holdings.forEach(q => {
    if (q.market_value) totalMV += q.market_value;
    if (q.cost_value) totalCost += q.cost_value;
    if (q.pnl != null) totalPnl += q.pnl;
    if (q.pct != null) dayPcts.push(q.pct);
  });

  const avgDay = dayPcts.length ? dayPcts.reduce((a, b) => a + b, 0) / dayPcts.length : null;

  document.getElementById('sumMV').textContent = totalMV ? '\u00A5' + fmtK(totalMV) : '--';
  document.getElementById('sumCost').textContent = totalCost ? '\u00A5' + fmtK(totalCost) : '--';

  const pnlEl = document.getElementById('sumPnl');
  if (totalPnl !== 0 || totalCost > 0) {
    pnlEl.textContent = (totalPnl >= 0 ? '+' : '') + '\u00A5' + fmtK(totalPnl);
    pnlEl.className = 'sum-val ' + (totalPnl >= 0 ? 'up' : 'down');
  } else {
    pnlEl.textContent = '--';
    pnlEl.className = 'sum-val';
  }

  const dayEl = document.getElementById('sumDay');
  if (avgDay != null) {
    dayEl.textContent = (avgDay >= 0 ? '+' : '') + avgDay.toFixed(2) + '%';
    dayEl.className = 'sum-val ' + (avgDay >= 0 ? 'up' : 'down');
  } else {
    dayEl.textContent = '--';
    dayEl.className = 'sum-val';
  }
}

function updateStatus(qs) {
  const dot = document.getElementById('statusDot');
  const txt = document.getElementById('statusText');
  dot.className = 'dot';
  const hasLive = qs.some(q => q.market_state === 'REGULAR');
  if (hasLive) {
    dot.classList.add('live');
    txt.textContent = '交易时段中 · 更新于 ' + new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  } else {
    dot.classList.add('closed');
    txt.textContent = '当前为收盘时段 · 可查看最近一次行情';
  }
}

function applyIndexSnapshot(data) {
  const keyMap = { NI225: 'wlNi225', TOPIX: 'wlTopix' };
  Object.entries(keyMap).forEach(([key, elId]) => {
    const el = document.getElementById(elId);
    if (!el || !data || !data[key]) return;
    const q = data[key];
    const up = q.change > 0;
    const dn = q.change < 0;
    const cls = up ? 'up' : (dn ? 'down' : 'neutral');
    const price = q.price != null ? Number(q.price).toLocaleString('ja-JP', { maximumFractionDigits: 2 }) : '--';
    const pct = q.pct != null ? (up ? '+' : '') + Number(q.pct).toFixed(2) + '%' : '';
    el.innerHTML = `<span style="color:var(--${cls})">${price} <small style="font-size:9px">${pct}</small></span>`;
  });
}

async function refresh() {
  clearInterval(AppState.cdTimer);
  AppState.countdown = 60;
  try {
    const snapshot = await fetchJson('/api/dashboard_snapshot');
    AppState.quotes = Array.isArray(snapshot?.quotes) ? snapshot.quotes : [];
    document.getElementById('grid').innerHTML = AppState.quotes.map((q, i) => renderCard(q, i)).join('');
    updateSummary(AppState.quotes);
    updateStatus(AppState.quotes);
    renderWatchlist(AppState.quotes);
    populateStockSelect(AppState.quotes);
    applyIndexSnapshot(snapshot?.indexes || {});
  } catch (e) {
    console.error(e);
    document.getElementById('statusText').textContent = '获取行情失败，请检查网络后重试';
  }
  startCountdown();
}

function startCountdown() {
  AppState.countdown = 60;
  AppState.cdTimer = setInterval(() => {
    AppState.countdown--;
    document.getElementById('cdText').textContent = AppState.countdown + ' 秒后刷新';
    if (AppState.countdown <= 0) refresh();
  }, 1000);
}

function openModal(code, name) {
  AppState.editCode = code;
  const q = AppState.quotes.find(item => item.symbol === code);
  document.getElementById('modalTitle').textContent = `编辑状态 / 持仓 · ${name}`;
  document.getElementById('mStatus').value = q?.status || (q?.shares > 0 ? 'holding' : 'watch');
  document.getElementById('mShares').value = q?.shares || '';
  document.getElementById('mCost').value = q?.cost || '';
  document.getElementById('modal').style.display = 'flex';
  setTimeout(() => document.getElementById('mShares').focus(), 50);
}

function closeModal() {
  document.getElementById('modal').style.display = 'none';
  AppState.editCode = null;
}

async function saveHolding() {
  if (!AppState.editCode) return;
  const status = document.getElementById('mStatus').value || 'watch';
  const shares = parseFloat(document.getElementById('mShares').value) || 0;
  const cost = parseFloat(document.getElementById('mCost').value) || 0;
  const port = await (await fetch('/api/portfolio')).json();
  const idx = port.findIndex(s => s.code === AppState.editCode);
  if (idx >= 0) {
    port[idx].status = status;
    port[idx].shares = status === 'holding' ? shares : 0;
    port[idx].cost = status === 'holding' ? cost : 0;
  }
  await fetch('/api/portfolio', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(port),
  });
  closeModal();
  refresh();
}

async function addStock() {
  const inp = document.getElementById('addInput');
  const err = document.getElementById('addErr');
  err.style.display = 'none';
  const code = inp.value.trim();
  if (!code) return;
  const res = await fetch('/api/add_stock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  });
  const data = await res.json();
  if (data.ok) {
    inp.value = '';
    refresh();
  } else {
    err.style.display = 'inline';
  }
}

async function removeStock(code) {
  if (!confirm(`Delete ${code}?`)) return;
  await fetch('/api/remove_stock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  });
  refresh();
}

async function fetchIndexes() {
  try {
    const data = await fetchJson('/api/index_quotes');
    applyIndexSnapshot(data);
  } catch (e) {
    // silently ignore
  }
}

const DashboardChartModal = {
  symbol: '',
  name: '',
  interval: 'D',
};

function bindChartModalIntervals() {
  document.querySelectorAll('[data-chart-interval]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-chart-interval]').forEach(item => item.classList.remove('active'));
      btn.classList.add('active');
      DashboardChartModal.interval = btn.dataset.chartInterval || 'D';
      renderChartModalWidget();
    });
  });
}

function openChartModal(symbol, name) {
  const quote = (AppState.quotes || []).find(item => item.symbol === symbol);
  DashboardChartModal.symbol = symbol;
  DashboardChartModal.name = name || symbol;
  DashboardChartModal.interval = 'D';
  const modal = document.getElementById('chartModal');
  document.getElementById('chartModalSymbol').textContent = symbol;
  document.getElementById('chartModalName').textContent = name || symbol;
  document.getElementById('chartModalOpenPage').href = `/chart?symbol=${encodeURIComponent(symbol)}&name=${encodeURIComponent(name || symbol)}`;
  document.querySelectorAll('[data-chart-interval]').forEach((item, idx) => {
    item.classList.toggle('active', idx === 0);
  });
  fillChartModalMeta(quote);
  modal.style.display = 'flex';
  renderChartModalWidget();
}

function closeChartModal() {
  const modal = document.getElementById('chartModal');
  const container = document.getElementById('tvChartModalContainer');
  const fallback = document.getElementById('chartModalFallback');
  if (container) container.innerHTML = '';
  if (fallback) fallback.style.display = 'none';
  if (modal) modal.style.display = 'none';
}

function renderChartModalWidget() {
  if (typeof createTradingViewWidget !== 'function') return;
  createTradingViewWidget(
    'tvChartModalContainer',
    'chartModalFallback',
    DashboardChartModal.symbol,
    DashboardChartModal.interval
  );
}

function fillChartModalMeta(quote) {
  document.getElementById('chartModalMacd').textContent = '--';
  document.getElementById('chartModalRsi').textContent = '--';
  document.getElementById('chartModalVolume').textContent = '--';
}

bindChartModalIntervals();
