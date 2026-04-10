const ShortPageState = {
  symbol: document.body.dataset.shortSymbol || '6758.T',
  name: document.body.dataset.shortName || '',
};

function shortEscapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function shortIsEmpty(rawValue) {
  const value = String(rawValue ?? '').trim();
  return !value || value === '--' || value === 'None' || value === 'null';
}

function shortRenderMetricHelp(helpText) {
  if (!helpText) return '';
  const safeText = shortEscapeHtml(helpText);
  return `<span class="metric-help" tabindex="0" aria-label="${safeText}" data-tooltip="${safeText}">?</span>`;
}

function shortRenderMetricLabel(label, helpText, className) {
  return `<div class="${className}"><span>${shortEscapeHtml(label || '--')}</span>${shortRenderMetricHelp(helpText)}</div>`;
}

function shortRenderOwnership(payload) {
  const container = document.getElementById('shortOwnershipBody');
  if (!container) return;
  if (!payload?.ok || !Array.isArray(payload.cards) || !payload.cards.length) {
    container.innerHTML = `<div class="empty-note">${shortEscapeHtml(payload?.error || '空头与持仓数据暂不可用')}</div>`;
    return;
  }
  container.innerHTML = payload.cards.map(card => `
    <article class="chart-ownership-item">
      <div class="chart-ownership-head">
        <div class="chart-ownership-title">${card.title || '--'}</div>
        ${card.subtitle ? `<div class="chart-ownership-subtitle">${card.subtitle}</div>` : ''}
      </div>
      <div class="chart-ownership-list">
        ${(card.items || []).map(item => {
          const rawValue = String(item?.value ?? '--').trim();
          const isEmptyValue = shortIsEmpty(rawValue);
          return `
            <div class="chart-ownership-row">
              ${shortRenderMetricLabel(item.label, item.help, 'chart-ownership-label')}
              <div class="chart-ownership-value ${item.tone || 'neutral'}${isEmptyValue ? ' empty' : ''}">${shortEscapeHtml(item.value || '--')}</div>
              ${item.detail ? `<div class="chart-ownership-detail">${shortEscapeHtml(item.detail)}</div>` : ''}
            </div>
          `;
        }).join('')}
      </div>
    </article>
  `).join('');
}

function shortRenderKeyValue(containerId, entries) {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.innerHTML = entries.map(([label, value]) => {
    const text = value == null || value === '' ? '--' : Array.isArray(value) ? value.join(', ') : String(value);
    const empty = text === '--' ? ' empty' : '';
    return `
      <div class="chart-ownership-row">
        <div class="chart-ownership-label">${shortEscapeHtml(label)}</div>
        <div class="chart-ownership-value neutral${empty}">${shortEscapeHtml(text)}</div>
      </div>
    `;
  }).join('');
}

function shortRenderRowTable(containerId, rows) {
  const container = document.getElementById(containerId);
  if (!container) return;
  if (!Array.isArray(rows) || !rows.length) {
    container.innerHTML = '<div class="empty-note">暂无可展示的原始行</div>';
    return;
  }
  container.innerHTML = rows.map(row => `
    <div class="short-raw-row">
      ${(row || []).map(cell => `<div class="short-raw-cell">${shortEscapeHtml(cell || '--')}</div>`).join('')}
    </div>
  `).join('');
}

function shortUpdateMeta(symbol, name) {
  const symbolEl = document.getElementById('shortPageSymbol');
  const nameEl = document.getElementById('shortPageName');
  const inputEl = document.getElementById('shortSymbolInput');
  const chartLink = document.getElementById('shortChartLink');
  if (symbolEl) symbolEl.textContent = symbol || '--';
  if (nameEl) nameEl.textContent = name || '空头与持仓调试';
  if (inputEl) inputEl.value = symbol || '';
  if (chartLink) chartLink.href = `/chart?symbol=${encodeURIComponent(symbol)}${name ? `&name=${encodeURIComponent(name)}` : ''}`;
}

async function shortLoadAll(symbol, name = '') {
  const clean = (symbol || '').trim().toUpperCase();
  if (!clean) return;
  ShortPageState.symbol = clean;
  ShortPageState.name = name;
  shortUpdateMeta(clean, name);
  history.replaceState({}, '', `/short-interest?symbol=${encodeURIComponent(clean)}${name ? `&name=${encodeURIComponent(name)}` : ''}`);

  const ownershipFetch = document.getElementById('shortOwnershipFetch');
  const debugFetch = document.getElementById('shortDebugFetch');
  const debugStatus = document.getElementById('shortDebugStatus');
  if (ownershipFetch) ownershipFetch.textContent = '加载中...';
  if (debugFetch) debugFetch.textContent = '加载中...';
  if (debugStatus) debugStatus.textContent = `正在检查 ${clean} ...`;

  try {
    const [ownership, debug] = await Promise.all([
      fetchApiJson(`/api/ownership_short?symbol=${encodeURIComponent(clean)}`),
      fetchApiJson(`/api/ownership_short_debug?symbol=${encodeURIComponent(clean)}`),
    ]);

    shortRenderOwnership(ownership);
    shortRenderKeyValue('shortDebugSummary', [
      ['诊断', debug?.diagnosis || '--'],
      ['概览抓取', debug?.source?.overviewFetchOk ?? '--'],
      ['明细抓取', debug?.source?.detailFetchOk ?? '--'],
      ['余额字段近似 0', debug?.derived?.allBalanceFieldsZeroLike ?? '--'],
      ['缺失字段', (debug?.missingFields || []).join(', ') || '无'],
    ]);
    shortRenderRowTable('shortLatestRows', [
      debug?.raw?.overviewLatestRow || [],
      debug?.raw?.detailLatestRow || [],
    ].filter(row => Array.isArray(row) && row.length));
    shortRenderRowTable('shortOverviewRecent', debug?.raw?.overviewRecentRows || []);
    shortRenderRowTable('shortDetailRecent', debug?.raw?.detailRecentRows || []);

    const payloadText = JSON.stringify(debug || {}, null, 2);
    const debugJson = document.getElementById('shortDebugJson');
    if (debugJson) debugJson.textContent = payloadText;

    if (debugStatus) debugStatus.textContent = debug?.diagnosis || '已完成';
    if (ownershipFetch) ownershipFetch.textContent = ownership?.updated ? `更新 ${ownership.updated}` : '已更新';
    if (debugFetch) debugFetch.textContent = '已更新';
  } catch (error) {
    const message = error?.message || '加载失败';
    const body = document.getElementById('shortOwnershipBody');
    if (body) body.innerHTML = `<div class="empty-note">${shortEscapeHtml(message)}</div>`;
    if (debugStatus) debugStatus.textContent = message;
    if (ownershipFetch) ownershipFetch.textContent = '加载失败';
    if (debugFetch) debugFetch.textContent = '加载失败';
  }
}

function shortBindEvents() {
  const input = document.getElementById('shortSymbolInput');
  const loadBtn = document.getElementById('shortLoadBtn');
  const refreshBtn = document.getElementById('shortOwnershipRefreshBtn');
  const debugRefreshBtn = document.getElementById('shortDebugRefreshBtn');
  if (loadBtn) {
    loadBtn.addEventListener('click', () => shortLoadAll(input?.value || ShortPageState.symbol, ShortPageState.name));
  }
  if (refreshBtn) {
    refreshBtn.addEventListener('click', () => shortLoadAll(ShortPageState.symbol, ShortPageState.name));
  }
  if (debugRefreshBtn) {
    debugRefreshBtn.addEventListener('click', () => shortLoadAll(ShortPageState.symbol, ShortPageState.name));
  }
  if (input) {
    input.addEventListener('keydown', event => {
      if (event.key === 'Enter') shortLoadAll(input.value, ShortPageState.name);
    });
  }
}

shortBindEvents();
shortLoadAll(ShortPageState.symbol, ShortPageState.name);
