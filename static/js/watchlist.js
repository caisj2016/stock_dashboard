window.AppState = window.AppState || {};

const WatchlistState = {
  tab: 'holding',
  quotes: [],
  selectedSymbol: null,
  contextSymbol: null,
  refreshTimer: null,
  collapsed: false,
  colorFilter: '',
  width: 320,
  detailHeight: 230,
};

const WL_FILTER_LABELS = {
  '': '\u989c\u8272',
  red: '\u7ea2',
  blue: '\u84dd',
  green: '\u7eff',
  yellow: '\u9ec4',
  purple: '\u7d2b',
  cyan: '\u9752',
  pink: '\u7c89',
};

function wlIsDashboardPage() {
  return window.location.pathname === '/';
}

function wlIsChartPage() {
  return window.location.pathname === '/chart';
}

function wlShell() {
  return document.querySelector('.app-shell.has-shared-watchlist');
}

function setWlTab(tab, el) {
  WatchlistState.tab = tab;
  document.querySelectorAll('[data-wl-tab]').forEach(btn => {
    btn.classList.toggle('active', btn === el || btn.dataset.wlTab === tab);
  });
  syncWlFilterVisibility();
  renderWatchlist(WatchlistState.quotes);
}

window.setWlTab = setWlTab;

function wlFilteredQuotes(quotes) {
  let filtered = quotes;
  if (WatchlistState.tab === 'holding') {
    filtered = filtered.filter(q => (q.status || (q.shares > 0 ? 'holding' : 'watch')) === 'holding');
  } else if (WatchlistState.tab === 'watch') {
    filtered = filtered.filter(q => (q.status || (q.shares > 0 ? 'holding' : 'watch')) === 'watch');
  }
  if (WatchlistState.colorFilter) {
    filtered = filtered.filter(q => wlMarkerColor(q) === WatchlistState.colorFilter);
  }
  return filtered;
}

function setWlColorFilter(color, el) {
  WatchlistState.colorFilter = color || '';
  updateWlFilterUi();
  closeWlFilterMenu();
  renderWatchlist(WatchlistState.quotes);
}

window.setWlColorFilter = setWlColorFilter;

function syncWlFilterVisibility() {
  const wrap = document.getElementById('wlFilterWrap');
  if (!wrap) return;
  const show = WatchlistState.tab !== 'holding';
  wrap.classList.toggle('is-hidden', !show);
  if (!show && WatchlistState.colorFilter) {
    WatchlistState.colorFilter = '';
    updateWlFilterUi();
  }
  if (!show) closeWlFilterMenu();
}

function updateWlFilterUi() {
  const btn = document.getElementById('wlFilterBtn');
  const label = document.getElementById('wlFilterBtnLabel');
  const dot = document.getElementById('wlFilterBtnDot');
  if (btn) btn.classList.toggle('active', Boolean(WatchlistState.colorFilter));
  if (label) label.textContent = WL_FILTER_LABELS[WatchlistState.colorFilter] || '\u989c\u8272';
  if (dot) {
    dot.className = 'wl-filter-trigger-dot';
    if (WatchlistState.colorFilter) dot.classList.add(WatchlistState.colorFilter);
  }
  document.querySelectorAll('[data-filter-color]').forEach(item => {
    item.classList.toggle('active', item.dataset.filterColor === WatchlistState.colorFilter);
  });
}

function openWlFilterMenu() {
  const menu = document.getElementById('wlFilterMenu');
  const btn = document.getElementById('wlFilterBtn');
  if (!menu || !btn) return;
  menu.style.display = 'block';
  btn.setAttribute('aria-expanded', 'true');
}

function closeWlFilterMenu() {
  const menu = document.getElementById('wlFilterMenu');
  const btn = document.getElementById('wlFilterBtn');
  if (menu) menu.style.display = 'none';
  if (btn) btn.setAttribute('aria-expanded', 'false');
}

function toggleWlFilterMenu() {
  const menu = document.getElementById('wlFilterMenu');
  if (!menu) return;
  if (menu.style.display === 'block') {
    closeWlFilterMenu();
  } else {
    openWlFilterMenu();
  }
}

function wlMarkerColor(q) {
  const color = String(q?.marker_color || '').trim().toLowerCase();
  return ['red', 'blue', 'green', 'yellow', 'purple', 'cyan', 'pink'].includes(color) ? color : '';
}

const WL_MARKER_GROUPS = ['red', 'blue', 'green', 'yellow', 'purple', 'cyan', 'pink', ''];

function wlMarkerDot(color) {
  if (!color) return '';
  return `<span class="wl-marker-badge ${color}" title="\u989c\u8272\u6807\u7b7e"></span>`;
}

function wlSortWithinGroup(quotes) {
  return [...(quotes || [])].sort((left, right) => {
    const leftStatus = left.status || (left.shares > 0 ? 'holding' : 'watch');
    const rightStatus = right.status || (right.shares > 0 ? 'holding' : 'watch');
    if (leftStatus !== rightStatus) {
      return leftStatus === 'holding' ? -1 : 1;
    }
    return String(left.symbol || left.code || '').localeCompare(String(right.symbol || right.code || ''));
  });
}

function renderWatchlistRow(q) {
  const up = q.change > 0;
  const down = q.change < 0;
  const cls = up ? 'up' : (down ? 'down' : 'neutral');
  const price = q.price != null ? Number(q.price).toLocaleString('ja-JP') : '--';
  const pct = q.pct != null ? `${up ? '+' : ''}${Number(q.pct).toFixed(2)}%` : '--';
  const markerColor = wlMarkerColor(q);
  return `
    <div class="wl-row ${WatchlistState.selectedSymbol === q.symbol ? 'wl-active' : ''}" id="wlr-${q.symbol.replace('.', '_')}" data-symbol="${q.symbol}">
      <span class="wl-row-marker ${markerColor || 'none'}" aria-hidden="true"></span>
      <div class="wl-name-wrap">
        <div class="wl-fullname wl-fullname-main">${q.name || q.symbol}</div>
      </div>
      <div class="wl-price ${cls}">\u00a5${price}</div>
      <div class="wl-pct ${cls}">${pct}</div>
    </div>
  `;
}

function renderWatchlistSections(list) {
  const buckets = new Map(WL_MARKER_GROUPS.map(color => [color, []]));
  list.forEach(item => {
    buckets.get(wlMarkerColor(item) || '')?.push(item);
  });

  return WL_MARKER_GROUPS
    .map(color => {
      const items = wlSortWithinGroup(buckets.get(color));
      if (!items.length) return '';
      return items.map(renderWatchlistRow).join('');
    })
    .join('');
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
    detail.innerHTML = '<div class="wl-detail-empty">\u9009\u62e9\u80a1\u7968\u67e5\u770b</div>';
    return;
  }
  WatchlistState.selectedSymbol = symbol;
  const status = q.status || (q.shares > 0 ? 'holding' : 'watch');
  const up = q.change > 0;
  const down = q.change < 0;
  const cls = up ? 'up' : (down ? 'down' : 'neutral');
  const marketValue = q.market_value != null ? fmtK(q.market_value) : '--';
  const pnl = q.pnl != null ? `${q.pnl > 0 ? '+' : ''}${fmtK(q.pnl)}` : '--';
  const markerColor = wlMarkerColor(q);
  const changeText = q.change != null ? `${up ? '+' : ''}${Number(q.change).toLocaleString('ja-JP')}` : '--';
  const pctText = q.pct != null ? `${up ? '+' : ''}${q.pct.toFixed(2)}%` : '--';
  detail.innerHTML = `
    <div class="wl-detail-card">
      <div class="wl-detail-top">
        <div class="wl-detail-title-block">
          <div class="wl-detail-symbol">${q.symbol}</div>
          <div class="wl-detail-name">${q.name || q.symbol}</div>
          <div class="wl-detail-kicker">
            <div class="wl-status ${status}">${status === 'holding' ? '\u5df2\u6301\u6709' : '\u89c2\u5bdf\u4e2d'}</div>
            ${markerColor ? `<div class="wl-marker-line"><span class="wl-marker-badge ${markerColor}"></span><span>\u989c\u8272\u6807\u7b7e</span></div>` : ''}
          </div>
        </div>
        <div class="wl-detail-actions">
          <a class="wl-detail-open" href="/chart?symbol=${encodeURIComponent(q.symbol)}&name=${encodeURIComponent(q.name || q.symbol)}">\u56fe\u8868</a>
        </div>
      </div>
      <div class="wl-detail-hero ${cls}">
        <div class="wl-detail-price">
          <div class="wl-detail-price-main">${q.price != null ? `\u00a5${Number(q.price).toLocaleString('ja-JP')}` : '--'}</div>
          <div class="wl-detail-currency">JPY</div>
        </div>
        <div class="wl-detail-change">
          <strong>${changeText !== '--' ? `\u00a5${changeText}` : '--'}</strong>
          <span>${pctText}</span>
        </div>
      </div>
      <div class="wl-detail-grid">
        <div><span>\u72b6\u6001</span><strong>${status === 'holding' ? '\u5df2\u6301\u6709' : '\u89c2\u5bdf\u4e2d'}</strong></div>
        <div><span>\u989c\u8272\u6807\u7b7e</span><strong>${markerColor ? wlMarkerDot(markerColor) : '\u672a\u6807\u8bb0'}</strong></div>
        <div><span>\u6301\u80a1\u6570</span><strong>${q.shares ? Number(q.shares).toLocaleString('ja-JP') : '--'}</strong></div>
        <div><span>\u6210\u672c\u4ef7</span><strong>${q.cost ? `\u00a5${Number(q.cost).toLocaleString('ja-JP')}` : '--'}</strong></div>
        <div><span>\u5e02\u503c</span><strong>${marketValue !== '--' ? `\u00a5${marketValue}` : '--'}</strong></div>
        <div><span>\u76c8\u4e8f</span><strong class="${q.pnl > 0 ? 'up' : (q.pnl < 0 ? 'down' : 'neutral')}">${pnl !== '--' ? `\u00a5${pnl}` : '--'}</strong></div>
      </div>
    </div>
  `;
}

function openWatchlistContextMenu(symbol, x, y) {
  const menu = document.getElementById('wlContextMenu');
  if (!menu) return;
  WatchlistState.contextSymbol = symbol;
  const q = (WatchlistState.quotes || []).find(item => item.symbol === symbol);
  const markerColor = wlMarkerColor(q);
  const label = document.getElementById('wlMarkerMenuLabel');
  if (label) label.textContent = markerColor ? '\u66f4\u6539\u6807\u8bb0' : '\u6807\u8bb0';
  menu.querySelectorAll('[data-marker-color]').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.markerColor === markerColor);
  });
  menu.style.display = 'block';
  menu.style.left = `${x}px`;
  menu.style.top = `${y}px`;
}

function closeWatchlistContextMenu() {
  const menu = document.getElementById('wlContextMenu');
  if (!menu) return;
  menu.style.display = 'none';
}

async function persistWatchlistPortfolio(mutator) {
  const portfolio = await fetchJson('/api/portfolio');
  const changed = mutator(portfolio);
  if (changed === false) return false;
  await fetchJson('/api/portfolio', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(portfolio),
  });
  return true;
}

function openWatchlistEditModal(symbol) {
  const modal = document.getElementById('wlEditModal');
  const q = (WatchlistState.quotes || []).find(item => item.symbol === symbol);
  if (!modal || !q) return;
  WatchlistState.contextSymbol = symbol;
  document.getElementById('wlEditTitle').textContent = `编辑状态 / ${q.name || q.symbol}`;
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
  const saved = await persistWatchlistPortfolio(portfolio => {
    const idx = portfolio.findIndex(item => item.code === symbol);
    if (idx < 0) return false;
    portfolio[idx].status = status;
    portfolio[idx].shares = status === 'holding' ? shares : 0;
    portfolio[idx].cost = status === 'holding' ? cost : 0;
    return true;
  });
  if (!saved) return;
  closeWatchlistEditModal();
  await refreshWatchlistData();
  if (typeof window.refresh === 'function' && wlIsDashboardPage()) {
    await window.refresh();
  }
}

async function saveWatchlistMarker(color) {
  const symbol = WatchlistState.contextSymbol;
  if (!symbol) return;
  const nextColor = ['red', 'blue', 'green', 'yellow', 'purple', 'cyan', 'pink'].includes(color) ? color : '';
  const saved = await persistWatchlistPortfolio(portfolio => {
    const idx = portfolio.findIndex(item => item.code === symbol);
    if (idx < 0) return false;
    portfolio[idx].marker_color = nextColor;
    return true;
  });
  if (!saved) return;
  closeWatchlistContextMenu();
  await refreshWatchlistData();
  if (typeof window.refresh === 'function' && wlIsDashboardPage()) {
    await window.refresh();
  }
}

async function addToWatchFromMenu() {
  const symbol = WatchlistState.contextSymbol;
  if (!symbol) return;
  const saved = await persistWatchlistPortfolio(portfolio => {
    const idx = portfolio.findIndex(item => item.code === symbol);
    if (idx < 0) return false;
    portfolio[idx].status = 'watch';
    portfolio[idx].shares = 0;
    portfolio[idx].cost = 0;
    return true;
  });
  if (!saved) return;
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
      if (wlIsChartPage() && typeof window.openChartSymbol === 'function') {
        window.openChartSymbol(q.symbol, q.name || q.symbol);
      }
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
  rows.innerHTML = renderWatchlistSections(list);
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

function handleWatchlistUpdated() {
  refreshWatchlistData().catch(() => {});
}

async function addStockFromSidebar() {
  const input = document.getElementById('wlAddCodeInput');
  const err = document.getElementById('wlAddCodeErr');
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
  closeWatchlistAddModal();
  await refreshWatchlistData();
  if (typeof window.refresh === 'function' && wlIsDashboardPage()) {
    await window.refresh();
  }
}

function openWatchlistAddModal() {
  const modal = document.getElementById('wlAddModal');
  const input = document.getElementById('wlAddCodeInput');
  const err = document.getElementById('wlAddCodeErr');
  if (!modal) return;
  modal.style.display = 'flex';
  if (err) err.style.display = 'none';
  window.setTimeout(() => input?.focus(), 0);
}

function closeWatchlistAddModal() {
  const modal = document.getElementById('wlAddModal');
  const input = document.getElementById('wlAddCodeInput');
  const err = document.getElementById('wlAddCodeErr');
  if (modal) modal.style.display = 'none';
  if (input) input.value = '';
  if (err) err.style.display = 'none';
}

function wlClampWidth(width) {
  return Math.max(260, Math.min(520, Math.round(width)));
}

function applyWatchlistWidth(width) {
  const root = document.getElementById('watchlist');
  const nextWidth = wlClampWidth(width);
  WatchlistState.width = nextWidth;
  if (!root || WatchlistState.collapsed) return;
  root.style.width = `${nextWidth}px`;
  root.style.minWidth = `${nextWidth}px`;
}

function persistWatchlistWidth(width) {
  try {
    localStorage.setItem('watchlistWidth', String(wlClampWidth(width)));
  } catch (_) {}
}

function wlClampDetailHeight(height) {
  return Math.max(120, Math.min(420, Math.round(height)));
}

function applyWatchlistDetailHeight(height) {
  const detail = document.getElementById('wlDetail');
  const nextHeight = wlClampDetailHeight(height);
  WatchlistState.detailHeight = nextHeight;
  if (!detail || WatchlistState.collapsed) return;
  detail.style.height = `${nextHeight}px`;
}

function persistWatchlistDetailHeight(height) {
  try {
    localStorage.setItem('watchlistDetailHeight', String(wlClampDetailHeight(height)));
  } catch (_) {}
}

function initWatchlistResize() {
  const root = document.getElementById('watchlist');
  const shell = wlShell();
  const handle = document.getElementById('wlResizer');
  if (!root || !shell || !handle) return;
  try {
    const saved = parseInt(localStorage.getItem('watchlistWidth') || '', 10);
    if (Number.isFinite(saved)) WatchlistState.width = wlClampWidth(saved);
  } catch (_) {}
  applyWatchlistWidth(WatchlistState.width);

  let dragging = false;
  let frameId = 0;
  let pendingWidth = WatchlistState.width;

  const flush = () => {
    frameId = 0;
    applyWatchlistWidth(pendingWidth);
    window.dispatchEvent(new Event('resize'));
  };

  const onMove = (event) => {
    if (!dragging) return;
    pendingWidth = window.innerWidth - event.clientX;
    if (!frameId) frameId = window.requestAnimationFrame(flush);
  };

  const stop = () => {
    if (!dragging) return;
    dragging = false;
    shell.classList.remove('is-resizing');
    document.body.style.userSelect = '';
    document.body.style.cursor = '';
    if (frameId) {
      window.cancelAnimationFrame(frameId);
      flush();
    }
    persistWatchlistWidth(WatchlistState.width);
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', stop);
  };

  handle.addEventListener('pointerdown', (event) => {
    if (WatchlistState.collapsed) return;
    dragging = true;
    shell.classList.add('is-resizing');
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'col-resize';
    event.preventDefault();
    handle.setPointerCapture?.(event.pointerId);
    window.addEventListener('pointermove', onMove, { passive: true });
    window.addEventListener('pointerup', stop, { once: true });
  });
}

function initWatchlistDetailResize() {
  const root = document.getElementById('watchlist');
  const shell = wlShell();
  const handle = document.getElementById('wlDetailResizer');
  if (!root || !shell || !handle) return;
  try {
    const saved = parseInt(localStorage.getItem('watchlistDetailHeight') || '', 10);
    if (Number.isFinite(saved)) WatchlistState.detailHeight = wlClampDetailHeight(saved);
  } catch (_) {}
  applyWatchlistDetailHeight(WatchlistState.detailHeight);

  let dragging = false;
  let frameId = 0;
  let pendingHeight = WatchlistState.detailHeight;

  const flush = () => {
    frameId = 0;
    applyWatchlistDetailHeight(pendingHeight);
    window.dispatchEvent(new Event('resize'));
  };

  const onMove = (event) => {
    if (!dragging) return;
    const rootRect = root.getBoundingClientRect();
    pendingHeight = rootRect.bottom - event.clientY;
    if (!frameId) frameId = window.requestAnimationFrame(flush);
  };

  const stop = () => {
    if (!dragging) return;
    dragging = false;
    shell.classList.remove('is-detail-resizing');
    document.body.style.userSelect = '';
    document.body.style.cursor = '';
    if (frameId) {
      window.cancelAnimationFrame(frameId);
      flush();
    }
    persistWatchlistDetailHeight(WatchlistState.detailHeight);
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', stop);
  };

  handle.addEventListener('pointerdown', (event) => {
    if (WatchlistState.collapsed) return;
    dragging = true;
    shell.classList.add('is-detail-resizing');
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'row-resize';
    event.preventDefault();
    handle.setPointerCapture?.(event.pointerId);
    window.addEventListener('pointermove', onMove, { passive: true });
    window.addEventListener('pointerup', stop, { once: true });
  });
}

function applyWatchlistCollapsed(collapsed) {
  WatchlistState.collapsed = collapsed;
  const shell = wlShell();
  const btn = document.getElementById('wlCollapseBtn');
  if (!shell) return;
  shell.classList.toggle('watchlist-collapsed', collapsed);
  if (btn) {
    btn.textContent = collapsed ? '>' : '<';
    btn.title = collapsed ? '展开自选栏' : '折叠自选栏';
  }
  if (collapsed) {
    const root = document.getElementById('watchlist');
    const detail = document.getElementById('wlDetail');
    if (root) {
      root.style.width = '';
      root.style.minWidth = '';
    }
    if (detail) {
      detail.style.height = '';
    }
  } else {
    applyWatchlistWidth(WatchlistState.width);
    applyWatchlistDetailHeight(WatchlistState.detailHeight);
  }
  try {
    localStorage.setItem('watchlistCollapsed', collapsed ? '1' : '0');
  } catch (_) {}
  window.dispatchEvent(new CustomEvent('watchlist:toggle', { detail: { collapsed } }));
  window.dispatchEvent(new Event('resize'));
  window.setTimeout(() => {
    window.dispatchEvent(new Event('resize'));
    window.dispatchEvent(new CustomEvent('watchlist:toggle', { detail: { collapsed } }));
  }, 240);
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
  initWatchlistResize();
  initWatchlistDetailResize();
  initWatchlistCollapse();
  syncWlFilterVisibility();
  updateWlFilterUi();
  window.addEventListener('watchlist:updated', handleWatchlistUpdated);
  root.querySelectorAll('[data-wl-tab]').forEach(btn => {
    btn.addEventListener('click', () => setWlTab(btn.dataset.wlTab, btn));
  });
  document.getElementById('wlFilterBtn')?.addEventListener('click', event => {
    event.stopPropagation();
    toggleWlFilterMenu();
  });
  root.querySelectorAll('[data-filter-color]').forEach(btn => {
    btn.addEventListener('click', event => {
      event.stopPropagation();
      setWlColorFilter(btn.dataset.filterColor, btn);
    });
  });
  document.getElementById('wlAddTriggerBtn')?.addEventListener('click', openWatchlistAddModal);
  document.getElementById('wlAddBtn')?.addEventListener('click', addStockFromSidebar);
  document.getElementById('wlAddCancel')?.addEventListener('click', closeWatchlistAddModal);
  document.getElementById('wlAddCodeInput')?.addEventListener('keydown', event => {
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
      if (action === 'clear-marker') saveWatchlistMarker('');
      if (action === 'watch') {
        addToWatchFromMenu();
      }
    });
  });
  root.querySelectorAll('[data-marker-color]').forEach(btn => {
    btn.addEventListener('click', () => {
      const current = wlMarkerColor((WatchlistState.quotes || []).find(item => item.symbol === WatchlistState.contextSymbol));
      const picked = btn.dataset.markerColor || '';
      saveWatchlistMarker(current === picked ? '' : picked);
    });
  });
  document.addEventListener('click', event => {
    if (!event.target.closest('.wl-context-menu')) {
      closeWatchlistContextMenu();
    }
    if (!event.target.closest('.wl-filter-wrap')) {
      closeWlFilterMenu();
    }
  });
  document.getElementById('wlAddModal')?.addEventListener('click', event => {
    if (event.target.id === 'wlAddModal') closeWatchlistAddModal();
  });
  if (!wlIsDashboardPage()) {
    refreshWatchlistData();
    WatchlistState.refreshTimer = window.setInterval(refreshWatchlistData, 3 * 60 * 1000);
  }
}

window.renderWatchlist = renderWatchlist;
window.refreshWatchlistData = refreshWatchlistData;
document.addEventListener('DOMContentLoaded', initWatchlist);

