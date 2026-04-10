window.NewsState = window.NewsState || {
  stockCache: {},
  trumpLoaded: false,
  trumpLoading: false,
};

const NewsState = window.NewsState;

function populateStockSelect(qs) {
  const sel = document.getElementById('aiStockSelect');
  const cur = sel.value;
  sel.innerHTML = '<option value="">选择股票...</option>' +
    qs.map(q => `<option value="${q.symbol}">${q.symbol.replace('.T', '')} · ${q.name || q.symbol}</option>`).join('');
  if (cur) sel.value = cur;
}

async function loadStockNews() {
  const sym = document.getElementById('aiStockSelect').value;
  if (!sym) return;
  const btn = document.getElementById('stockNewsBtn');
  const body = document.getElementById('aiStockBody');
  btn.disabled = true;
  body.innerHTML = `<div class="ai-loading"><div class="ai-spinner"></div>正在抓取 ${sym} 的最新相关新闻...</div>`;
  try {
    const items = NewsState.stockCache[sym] || await fetchApiJson(`/api/stock_news?symbol=${encodeURIComponent(sym)}`);
    NewsState.stockCache[sym] = items;
    if (!items.length) {
      body.innerHTML = `<div class="ai-placeholder">暂时没有找到相关新闻</div>`;
    } else {
      body.innerHTML = items.map(it => renderNewsItem(it)).join('');
    }
  } catch (e) {
    body.innerHTML = `<div class="ai-placeholder" style="color:var(--down)">获取失败: ${e.message}</div>`;
  }
  btn.disabled = false;
}

function renderNewsItem(it) {
  return `
  <div class="news-item">
    <div class="news-meta">
      <span class="news-source">${it.provider || ''}</span>
      <span class="news-time">${it.pub || ''}</span>
    </div>
    <div class="news-title">
      ${it.url ? `<a href="${it.url}" target="_blank" rel="noopener">${it.title}</a>` : it.title}
    </div>
    ${it.titleEn && it.titleEn !== it.title ? `<div class="news-title-en">${it.titleEn}</div>` : ''}
    ${it.summary ? `<div class="news-summary">${it.summary}</div>` : ''}
  </div>`;
}

async function loadTrumpNews(force = false) {
  if (NewsState.trumpLoading) return;
  const btn = document.getElementById('trumpBtn');
  const body = document.getElementById('aiTrumpBody');
  const lastFetch = document.getElementById('trumpLastFetch');
  NewsState.trumpLoading = true;
  btn.disabled = true;
  body.innerHTML = `<div class="ai-loading"><div class="ai-spinner"></div>正在从 Truth Social / X / Reuters / AP / BBC / The Hill 抓取...</div>`;
  try {
    const items = await fetchApiJson('/api/trump_news');
    NewsState.trumpLoaded = true;
    if (!items.length) {
      body.innerHTML = `<div class="ai-placeholder">暂未找到特朗普相关动态，请稍后重试</div>`;
    } else {
      body.innerHTML = `<div class="ai-placeholder" style="padding:0 0 12px 0;text-align:left;color:var(--muted)">已按股票相关度优先排序，并尽量压缩成适合快速浏览的摘要。</div>` + items.map(it => renderTrumpItem(it)).join('');
      const now = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
      lastFetch.textContent = `更新: ${now}`;
    }
  } catch (e) {
    body.innerHTML = `<div class="ai-placeholder" style="color:var(--down)">获取失败: ${e.message}</div>`;
  }
  NewsState.trumpLoading = false;
  btn.disabled = false;
}

function lazyLoadTrumpNews() {
  if (NewsState.trumpLoaded || NewsState.trumpLoading) return;
  loadTrumpNews(false);
}

function renderTrumpItem(it) {
  const src = it.source || '';
  const srcColor = {
    TRUTH: '#2563eb', X: '#e5e7eb', REUTERS: '#f97316', APNEWS: '#3b82f6', THEHILL: '#8b5cf6', BBC: '#ef4444'
  }[src] || '#6b7280';
  const brief = it.brief || it.summary_zh || it.title_zh || it.title || '';
  const tags = Array.isArray(it.market_tags) ? it.market_tags : [];
  return `
  <div class="trump-item">
    <div class="news-meta">
      <span class="news-source" style="color:${srcColor}">${src}</span>
      <span class="news-time">${it.pub || ''}</span>
    </div>
    <div class="news-title">
      ${it.url ? `<a href="${it.url}" target="_blank" rel="noopener">${it.title_zh || it.title}</a>` : (it.title_zh || it.title)}
    </div>
    ${brief ? `<div class="trump-brief">${brief}</div>` : ''}
    ${tags.length ? `<div class="trump-tags">${tags.map(tag => `<span class="trump-tag">${tag}</span>`).join('')}</div>` : ''}
    <div class="news-title-en">${it.title}</div>
    ${it.summary_zh && it.summary_zh !== brief ? `<div class="news-summary">${it.summary_zh}</div>` : ''}
  </div>`;
}
