window.AppState = window.AppState || {};

const WatchlistState = {
  tab: 'holding',
  quotes: [],
  selectedSymbol: null,
  contextSymbol: null,
  refreshTimer: null,
  collapsed: false,
};

function wlIsDashboardPage() {
  return window.location.pathname === '/';
}

function wlShell() {
  return document.querySelector('.app-shell.has-shared-watchlist');
}

function setWlTab(tab, el) {
  WatchlistState.tab = tab;
  document.querySelectorAll('[data-wl-tab]').forEach(btn => {
    btn.classList.toggle('active', btn === el || btn.dataset.wlTab === tab);
  });
  renderWatchlist(WatchlistState.quotes);
}

window.setWlTab = setWlTab;

function wlFilteredQuotes(quotes) {
  if (WatchlistState.tab === 'holding') {
    return quotes.filter(q => (q.status || (q.shares > 0 ? 'holding' : 'watch')) === 'holding');
  }
  if (WatchlistState.tab === 'watch') {
    return quotes.filter(q => (q.status || (q.shares > 0 ? 'holding' : 'watch')) === 'watch');
  }
  return quotes;
}

function wlSparklineSvg(q) {
  const up = q.change > 0;
  const down = q.change < 0;
  const sparkColor = up ? '#22c55e' : (down ? '#ef4444' : '#6b7280');
  if (!(q.closes && q.closes.length > 1)) {
    return '<svg class="wl-spark" viewBox="0 0 40 28" preserveAspectRatio="none"></svg>';
  }
  const vals = q.closes.filter(v => v != null);
  if (vals.length < 2) {
    return '<svg class="wl-spark" viewBox="0 0 40 28" preserveAspectRatio="none"></svg>';
  }
  const mn = Math.min(...vals);
  const mx = Math.max(...vals);
  const rng = mx - mn || 1;
  const pts = vals.map((v, i) => {
    const x = (i / (vals.length - 1)) * 38 + 1;
    const y = 27 - ((v - mn) / rng) * 25;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' L ');
  return `<svg class="wl-spark" viewBox="0 0 40 28" preserveAspectRatio="none">
    <path d="M ${pts}" fill="none" stroke="${sparkColor}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.85"/>
  </svg>`;
}

function renderWatchlistDetail(symbol) {
  const detail = document.getElementById('wlDetail');
  if (!detail) return;
  const q = (WatchlistState.quotes || []).find(item => item.symbol === symbol);
  if (!q) {
    detail.innerHTML = '<div class="wl-detail-empty">点击一只股票，这里会显示详情。</div>';
    return;
  }
  WatchlistState.selectedSymbol = symbol;
  const status = q.status || (q.shares > 0 ? 'holding' : 'watch');
  const up = q.change > 0;
  const down = q.change < 0;
  const cls = up ? 'up' : (down ? 'down' : 'neutral');
  const marketValue = q.market_value != null ? fmtK(q.market_value) : '--';
  const pnl = q.pnl != null ? `${q.pnl > 0 ? '+' : ''}${fmtK(q.pnl)}` : '--';

  detail.innerHTML = `
    <div class="wl-detail-card">
      <div class="wl-detail-head">
        <div>
          <div class="wl-detail-symbol">${q.symbol}</div>
          <div class="wl-detail-name">${q.name || q.symbol}</div>
        </div>
        <a class="wl-detail-open" href="/chart?symbol=${encodeURIComponent(q.symbol)}&name=${encodeURIComponent(q.name || q.symbol)}">打开图表</a>
      </div>
      <div class="wl-detail-price ${cls}">
        ${q.price != null ? `¥${Number(q.price).toLocaleString('ja-JP')}` : '--'}
        <span>${q.pct != null ? `${up ? '+' : ''}${q.pct.toFixed(2)}%` : '--'}</span>
      </div>
      <div class="wl-detail-grid">
        <div><span>状态</span><strong>${status === 'holding' ? '已持有' : '观察中'}</strong></div>
        <div><span>持股数</span><strong>${q.shares ? Number(q.shares).toLocaleString('ja-JP') : '--'}</strong></div>
        <div><span>成本价</span><strong>${q.cost ? `¥${Number(q.cost).toLocaleString('ja-JP')}` : '--'}</strong></div>
        <div><span>市值</span><strong>${marketValue !== '--' ? `¥${marketValue}` : '--'}</strong></div>
        <div><span>盈亏</span><strong class="${q.pnl > 0 ? 'up' : (q.pnl < 0 ? 'down' : 'neutral')}">${pnl !== '--' ? `¥${pnl}` : '--'}</strong></div>
      </div>
    </div>
  `;
}

function openWatchlistContextMenu(symbol, x, y) {
  const menu = document.getElementById('wlContextMenu');
  if (!menu) return;
  WatchlistState.contextSymbol = symbol;
  menu.style.display = 'block';
  menu.style.left = `${x}px`;
  menu.style.top = `${y}px`;
}

function closeWatchlistContextMenu() {
  const menu = document.getElementById('wlContextMenu');
  if (!menu) return;
  menu.style.display = 'none';
}

function openWatchlistEditModal(symbol) {
  const modal = document.getElementById('wlEditModal');
  const q = (WatchlistState.quotes || []).find(item => item.symbol === symbol);
  if (!modal || !q) return;
  WatchlistState.contextSymbol = symbol;
  document.getElementById('wlEditTitle').textContent = `编辑状态 · ${q.name || q.symbol}`;
  document.getElementById('wlEditStatus').value = q.status || (q.shares > 0 ? 'holding' : 'watch');
  document.getElementById('wlEditShares').value = q.shares || '';
  document.getElementById('wlEditCost').value = q.cost || '';
  modal.style.display = 'flex';
}

function closeWatchlistEditModal() {
  const modal = document.getElementById('wlEditModal');
  if (modal) modal.style.display = 'none';
}

async function saveWatchlistStatus() {
  const symbol = WatchlistState.contextSymbol;
  if (!symbol) return;
  const status = document.getElementById('wlEditStatus').value || 'watch';
  const shares = parseFloat(document.getElementById('wlEditShares').value) || 0;
  const cost = parseFloat(document.getElementById('wlEditCost').value) || 0;
  const portfolio = await fetchJson('/api/portfolio');
  const idx = portfolio.findIndex(item => item.code === symbol);
  if (idx < 0) return;
  portfolio[idx].status = status;
  portfolio[idx].shares = status === 'holding' ? shares : 0;
  portfolio[idx].cost = status === 'holding' ? cost : 0;
  await fetchJson('/api/portfolio', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(portfolio),
  });
  closeWatchlistEditModal();
  await refreshWatchlistData();
  if (typeof window.refresh === 'function' && wlIsDashboardPage()) {
    await window.refresh();
  }
}

async function addToWatchFromMenu() {
  const symbol = WatchlistState.contextSymbol;
  if (!symbol) return;
  const portfolio = await fetchJson('/api/portfolio');
  const idx = portfolio.findIndex(item => item.code === symbol);
  if (idx < 0) return;
  portfolio[idx].status = 'watch';
  portfolio[idx].shares = 0;
  portfolio[idx].cost = 0;
  await fetchJson('/api/portfolio', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(portfolio),
  });
  closeWatchlistContextMenu();
  await refreshWatchlistData();
  if (typeof window.refresh === 'function' && wlIsDashboardPage()) {
    await window.refresh();
  }
}

function bindWatchlistRows(list) {
  list.forEach(q => {
    const row = document.getElementById(`wlr-${q.symbol.replace('.', '_')}`);
    if (!row) return;
    row.addEventListener('click', () => {
      document.querySelectorAll('#wlRows .wl-row').forEach(item => item.classList.remove('wl-active'));
      row.classList.add('wl-active');
      renderWatchlistDetail(q.symbol);
    });
    row.addEventListener('dblclick', () => {
      window.location.href = `/chart?symbol=${encodeURIComponent(q.symbol)}&name=${encodeURIComponent(q.name || q.symbol)}`;
    });
    row.addEventListener('contextmenu', (event) => {
      event.preventDefault();
      openWatchlistContextMenu(q.symbol, event.clientX, event.clientY);
    });
  });
}

function renderWatchlist(qs) {
  WatchlistState.quotes = Array.isArray(qs) ? qs : [];
  const rows = document.getElementById('wlRows');
  if (!rows) return;
  const list = wlFilteredQuotes(WatchlistState.quotes);
  rows.innerHTML = list.map(q => {
    const up = q.change > 0;
    const down = q.change < 0;
    const cls = up ? 'up' : (down ? 'down' : 'neutral');
    const price = q.price != null ? Number(q.price).toLocaleString('ja-JP') : '--';
    const pct = q.pct != null ? `${up ? '+' : ''}${Number(q.pct).toFixed(2)}%` : '--';
    const status = q.status || (q.shares > 0 ? 'holding' : 'watch');
    return `
      <div class="wl-row ${WatchlistState.selectedSymbol === q.symbol ? 'wl-active' : ''}" id="wlr-${q.symbol.replace('.', '_')}" data-symbol="${q.symbol}">
        ${wlSparklineSvg(q)}
        <div class="wl-name-wrap">
          <div class="wl-sym">${q.symbol.replace('.T', '')}<span style="color:var(--dim);font-size:9px">.T</span></div>
          <div class="wl-fullname">${q.name || q.symbol}</div>
          <div class="wl-status ${status}">${status === 'holding' ? '已持有' : '观察中'}</div>
        </div>
        <div class="wl-price ${cls}">¥${price}</div>
        <div class="wl-pct ${cls}">${pct}</div>
      </div>
    `;
  }).join('');
  bindWatchlistRows(list);
  if (WatchlistState.selectedSymbol) {
    renderWatchlistDetail(WatchlistState.selectedSymbol);
  }
}

function applyWatchlistIndexes(data) {
  const keyMap = { NI225: 'wlNi225', TOPIX: 'wlTopix' };
  Object.entries(keyMap).forEach(([key, elId]) => {
    const el = document.getElementById(elId);
    if (!el || !data || !data[key]) return;
    const q = data[key];
    const up = q.change > 0;
    const dn = q.change < 0;
    const cls = up ? 'up' : (dn ? 'down' : 'neutral');
    const price = q.price != null ? Number(q.price).toLocaleString('ja-JP', { maximumFractionDigits: 2 }) : '--';
    const pct = q.pct != null ? `${up ? '+' : ''}${Number(q.pct).toFixed(2)}%` : '';
    el.innerHTML = `<span class="${cls}">${price}${pct ? ` <small>${pct}</small>` : ''}</span>`;
  });
}

async function refreshWatchlistData() {
  const [quotes, indexes] = await Promise.all([
    fetchJson('/api/quotes'),
    fetchJson('/api/index_quotes'),
  ]);
  renderWatchlist(quotes);
  applyWatchlistIndexes(indexes);
}

async function addStockFromSidebar() {
  const input = document.getElementById('addInput');
  const err = document.getElementById('addErr');
  if (!input || !err) return;
  err.style.display = 'none';
  const code = input.value.trim();
  if (!code) return;
  const data = await fetchJson('/api/add_stock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  }).catch(() => ({ ok: false }));
  if (!data.ok) {
    err.style.display = 'inline';
    return;
  }
  input.value = '';
  await refreshWatchlistData();
  if (typeof window.refresh === 'function' && wlIsDashboardPage()) {
    await window.refresh();
  }
}

function applyWatchlistCollapsed(collapsed) {
  WatchlistState.collapsed = collapsed;
  const shell = wlShell();
  const btn = document.getElementById('wlCollapseBtn');
  if (!shell) return;
  shell.classList.toggle('watchlist-collapsed', collapsed);
  if (btn) {
    btn.textContent = collapsed ? '›' : '‹';
    btn.title = collapsed ? '展开自选栏' : '折叠自选栏';
  }
  try {
    localStorage.setItem('watchlistCollapsed', collapsed ? '1' : '0');
  } catch (_) {}
  window.dispatchEvent(new Event('resize'));
}

function toggleWatchlistCollapsed() {
  applyWatchlistCollapsed(!WatchlistState.collapsed);
}

function initWatchlistCollapse() {
  let collapsed = false;
  try {
    collapsed = localStorage.getItem('watchlistCollapsed') === '1';
  } catch (_) {}
  applyWatchlistCollapsed(collapsed);
  document.getElementById('wlCollapseBtn')?.addEventListener('click', toggleWatchlistCollapsed);
}

function initWatchlist() {
  const root = document.getElementById('watchlist');
  if (!root) return;
  initWatchlistCollapse();
  root.querySelectorAll('[data-wl-tab]').forEach(btn => {
    btn.addEventListener('click', () => setWlTab(btn.dataset.wlTab, btn));
  });
  document.getElementById('wlAddBtn')?.addEventListener('click', addStockFromSidebar);
  document.getElementById('addInput')?.addEventListener('keydown', event => {
    if (event.key === 'Enter') addStockFromSidebar();
  });
  document.getElementById('wlEditCancel')?.addEventListener('click', closeWatchlistEditModal);
  document.getElementById('wlEditSave')?.addEventListener('click', saveWatchlistStatus);
  document.getElementById('wlEditStatus')?.addEventListener('change', event => {
    const holding = event.target.value === 'holding';
    document.getElementById('wlEditShares').disabled = !holding;
    document.getElementById('wlEditCost').disabled = !holding;
  });
  document.querySelectorAll('.wl-menu-item').forEach(item => {
    item.addEventListener('click', () => {
      const action = item.dataset.wlMenu;
      if (action === 'edit') {
        closeWatchlistContextMenu();
        openWatchlistEditModal(WatchlistState.contextSymbol);
      }
      if (action === 'watch') {
        addToWatchFromMenu();
      }
    });
  });
  document.addEventListener('click', event => {
    if (!event.target.closest('.wl-context-menu')) {
      closeWatchlistContextMenu();
    }
  });
  if (!wlIsDashboardPage()) {
    refreshWatchlistData();
    WatchlistState.refreshTimer = window.setInterval(refreshWatchlistData, 3 * 60 * 1000);
  }
}

window.renderWatchlist = renderWatchlist;
window.refreshWatchlistData = refreshWatchlistData;
document.addEventListener('DOMContentLoaded', initWatchlist);
