const DASHBOARD_GRID_PHASE_DELAY = 32;
const DASHBOARD_SPARK_DELAY = 140;

AppState.gridHydrated = false;
AppState.gridSignature = '';
AppState.gridRenderTimer = null;
AppState.sparkRenderTimer = null;
AppState.gridRenderToken = 0;

function getHoldingQuotes(qs) {
  return (qs || []).filter(q => (q.status || (q.shares > 0 ? 'holding' : 'watch')) === 'holding');
}

function cardId(symbol) {
  return `c-${String(symbol || '').replace('.', '_')}`;
}

function getCardTone(q) {
  const up = q.change > 0;
  const down = q.change < 0;
  return {
    up,
    down,
    cardClass: up ? 'up-card' : (down ? 'down-card' : ''),
    changeClass: up ? 'up' : (down ? 'down' : 'neutral'),
  };
}

function getHoldingMeta(q) {
  const tone = getCardTone(q);
  const status = q.status || (q.shares > 0 ? 'holding' : 'watch');
  const statusLabel = status === 'holding' ? '\u5df2\u6301\u6709' : '\u89c2\u5bdf\u4e2d';
  const pnlSign = q.pnl > 0 ? '+' : '';
  const pnlPct = q.pnlPct != null ? `${pnlSign}${q.pnlPct.toFixed(2)}%` : '--';
  return {
    status,
    statusLabel,
    tone,
    price: q.price != null ? '\u00a5' + q.price.toLocaleString('ja-JP') : '--',
    change: q.change != null ? (tone.up ? '+' : '') + Math.round(q.change).toLocaleString() : '--',
    pct: q.pct != null ? (tone.up ? '+' : '') + q.pct.toFixed(2) + '%' : '--',
    shares: `${(q.shares || 0).toLocaleString()} \u80a1`,
    cost: `\u00a5${Number(q.cost || 0).toLocaleString('ja-JP')}`,
    marketValue: `\u00a5${fmtK(q.marketValue)}`,
    pnl: `${pnlSign}\u00a5${fmtK(q.pnl)} (${pnlPct})`,
    pnlClass: q.pnl > 0 ? 'up' : (q.pnl < 0 ? 'down' : ''),
  };
}

function renderSparkMarkup(q) {
  const tone = getCardTone(q);
  const spark = (q.closes && q.closes.length > 1) ? sparkPath(q.closes) : '';
  const sparkColor = tone.up ? '#ef4444' : (tone.down ? '#22c55e' : '#6b7280');
  if (!spark) return '<div class="spark spark-empty"></div>';
  return `<svg class="spark" viewBox="0 0 170 30" preserveAspectRatio="none">
    <path d="${spark}" fill="none" stroke="${sparkColor}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.8"/>
  </svg>`;
}

function renderCard(q, idx, options = {}) {
  const { deferSpark = true } = options;
  const meta = getHoldingMeta(q);
  const delay = idx * 0.04;

  return `
  <div class="card ${meta.tone.cardClass}" style="animation-delay:${delay}s" id="${cardId(q.symbol)}" data-symbol="${q.symbol}">
    <button class="card-remove" title="\u5220\u9664" onclick="removeStock('${q.symbol}')">\u00d7</button>
    <div class="card-ticker" data-field="ticker">${q.symbol}</div>
    <div class="card-name" data-field="name">${q.name || q.symbol}</div>
    <div class="card-status ${meta.status}" data-field="status">${meta.statusLabel}</div>
    <div class="card-price" data-field="price">${meta.price}</div>
    <div class="card-change">
      <span class="chg ${meta.tone.changeClass}" data-field="change">${meta.change}</span>
      <span class="chg ${meta.tone.changeClass}" data-field="pct">${meta.pct}</span>
    </div>
    <div class="spark-slot" data-field="spark">${deferSpark ? '' : renderSparkMarkup(q)}</div>
    <div class="holdings">
      <div class="holding-row"><span class="holding-key">\u6301\u80a1\u6570</span><span class="holding-val" data-field="shares">${meta.shares}</span></div>
      <div class="holding-row"><span class="holding-key">\u6210\u672c\u4ef7</span><span class="holding-val" data-field="cost">${meta.cost}</span></div>
      <div class="holding-row"><span class="holding-key">\u5e02\u573a\u4ef7\u503c</span><span class="holding-val" data-field="market-value">${meta.marketValue}</span></div>
      <div class="holding-row"><span class="holding-key">\u76c8\u4e8f</span><span class="holding-val ${meta.pnlClass}" data-field="pnl">${meta.pnl}</span></div>
    </div>
    <button class="chart-link-btn" onclick="openChartModal('${q.symbol}', '${(q.name || q.symbol).replace(/'/g, "\\'")}')">\u5feb\u901f\u770b\u56fe</button>
    <a class="chart-link-btn" href="/chart?symbol=${encodeURIComponent(q.symbol)}&name=${encodeURIComponent(q.name || q.symbol)}">\u6253\u5f00\u4e13\u4e1a K \u7ebf</a>
    <button class="edit-btn" onclick="openModal('${q.symbol}', '${(q.name || '').replace(/'/g, "\\'")}')">\u270e \u7f16\u8f91\u72b6\u6001 / \u6301\u4ed3</button>
  </div>`;
}

function getHoldingsSignature(holdings) {
  return (holdings || []).map(item => item.symbol).join('|');
}

function updateCardNode(card, q) {
  const meta = getHoldingMeta(q);
  card.dataset.symbol = q.symbol;
  card.classList.toggle('up-card', meta.tone.cardClass === 'up-card');
  card.classList.toggle('down-card', meta.tone.cardClass === 'down-card');
  card.querySelector('[data-field="ticker"]').textContent = q.symbol;
  card.querySelector('[data-field="name"]').textContent = q.name || q.symbol;

  const statusEl = card.querySelector('[data-field="status"]');
  statusEl.textContent = meta.statusLabel;
  statusEl.className = `card-status ${meta.status}`;

  card.querySelector('[data-field="price"]').textContent = meta.price;

  const changeEl = card.querySelector('[data-field="change"]');
  const pctEl = card.querySelector('[data-field="pct"]');
  changeEl.textContent = meta.change;
  pctEl.textContent = meta.pct;
  changeEl.className = `chg ${meta.tone.changeClass}`;
  pctEl.className = `chg ${meta.tone.changeClass}`;

  card.querySelector('[data-field="shares"]').textContent = meta.shares;
  card.querySelector('[data-field="cost"]').textContent = meta.cost;
  card.querySelector('[data-field="market-value"]').textContent = meta.marketValue;

  const pnlEl = card.querySelector('[data-field="pnl"]');
  pnlEl.textContent = meta.pnl;
  pnlEl.className = `holding-val ${meta.pnlClass}`.trim();
}

function renderMainGrid(qs, options = {}) {
  const holdings = getHoldingQuotes(qs);
  if (!holdings.length) {
    return `<div class="ai-placeholder" style="grid-column:1 / -1; padding:32px 20px; text-align:center;">暂无持仓，先把股票标记为已持有吧。</div>`;
  }
  return holdings.map((q, i) => renderCard(q, i, options)).join('');
}

function scheduleSparklineHydration(holdings) {
  clearTimeout(AppState.sparkRenderTimer);
  AppState.sparkRenderTimer = setTimeout(() => {
    holdings.forEach(q => {
      const slot = document.querySelector(`#${cardId(q.symbol)} [data-field="spark"]`);
      if (slot) slot.innerHTML = renderSparkMarkup(q);
    });
  }, DASHBOARD_SPARK_DELAY);
}

function renderOrUpdateMainGrid(qs, options = {}) {
  const { forceRebuild = false } = options;
  const grid = document.getElementById('grid');
  if (!grid) return;

  const holdings = getHoldingQuotes(qs);
  const signature = getHoldingsSignature(holdings);
  const existingCards = Array.from(grid.querySelectorAll('.card[data-symbol]'));
  const canPatch = !forceRebuild && AppState.gridHydrated && signature === AppState.gridSignature && existingCards.length === holdings.length;

  if (canPatch) {
    holdings.forEach(q => {
      const card = document.getElementById(cardId(q.symbol));
      if (card) updateCardNode(card, q);
    });
  } else {
    grid.innerHTML = renderMainGrid(qs, { deferSpark: true });
    AppState.gridHydrated = true;
  }

  AppState.gridSignature = signature;
  scheduleSparklineHydration(holdings);
}

function queueMainGridRender(qs, options = {}) {
  const { forceRebuild = false } = options;
  clearTimeout(AppState.gridRenderTimer);
  const token = ++AppState.gridRenderToken;
  AppState.gridRenderTimer = setTimeout(() => {
    if (token !== AppState.gridRenderToken) return;
    renderOrUpdateMainGrid(qs, { forceRebuild });
  }, DASHBOARD_GRID_PHASE_DELAY);
}

function updateSummary(qs) {
  const holdings = getHoldingQuotes(qs);
  let totalMV = 0;
  let totalCost = 0;
  let totalPnl = 0;
  const dayPcts = [];

  holdings.forEach(q => {
    if (q.marketValue) totalMV += q.marketValue;
    if (q.costValue) totalCost += q.costValue;
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
  const hasLive = qs.some(q => q.marketState === 'REGULAR');
  if (hasLive) {
    dot.classList.add('live');
    txt.textContent = '\u4ea4\u6613\u4e2d ' + new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  } else {
    dot.classList.add('closed');
    txt.textContent = '\u6536\u76d8\u4e2d';
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

function applyFastSnapshot(snapshot) {
  AppState.quotes = Array.isArray(snapshot?.quotes) ? snapshot.quotes : [];
  updateSummary(AppState.quotes);
  updateStatus(AppState.quotes);
  renderWatchlist(AppState.quotes);
  populateStockSelect(AppState.quotes);
  applyIndexSnapshot(snapshot?.indexes || {});
}

async function refresh(options = {}) {
  const { forceRebuild = false } = options;
  clearInterval(AppState.cdTimer);
  AppState.countdown = 60;
  try {
    const snapshot = await fetchApiJson('/api/dashboard_snapshot');
    applyFastSnapshot(snapshot);
    queueMainGridRender(AppState.quotes, { forceRebuild: forceRebuild || !AppState.gridHydrated });
  } catch (e) {
    console.error(e);
    document.getElementById('statusText').textContent = '\u884c\u60c5\u5f02\u5e38';
  }
  startCountdown();
}

function startCountdown() {
  AppState.countdown = 60;
  document.getElementById('cdText').textContent = AppState.countdown + 's';
  AppState.cdTimer = setInterval(() => {
    AppState.countdown--;
    document.getElementById('cdText').textContent = AppState.countdown + 's';
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
  const portfolio = await fetchApiJson('/api/portfolio');
  const items = Array.isArray(portfolio?.items) ? [...portfolio.items] : [];
  const idx = items.findIndex(item => item.symbol === AppState.editCode);
  if (idx >= 0) {
    items[idx].status = status;
    items[idx].shares = status === 'holding' ? shares : 0;
    items[idx].cost = status === 'holding' ? cost : 0;
  }
  await fetchApiJson('/api/portfolio', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(items),
  });
  closeModal();
  refresh({ forceRebuild: true });
}

async function addStock() {
  const inp = document.getElementById('addInput');
  const err = document.getElementById('addErr');
  err.style.display = 'none';
  const code = inp.value.trim();
  if (!code) return;
  try {
    await fetchApiJson('/api/add_stock', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code }),
    });
    inp.value = '';
    refresh({ forceRebuild: true });
  } catch (_) {
    err.style.display = 'inline';
  }
}

async function removeStock(code) {
  if (!confirm(`Delete ${code}?`)) return;
  await fetchApiJson('/api/remove_stock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  });
  refresh({ forceRebuild: true });
}

async function fetchIndexes() {
  try {
    const data = await fetchApiJson('/api/index_quotes');
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

function fillChartModalMeta() {
  document.getElementById('chartModalMacd').textContent = '--';
  document.getElementById('chartModalRsi').textContent = '--';
  document.getElementById('chartModalVolume').textContent = '--';
}

bindChartModalIntervals();
