window.DigestState = window.DigestState || {
  cache: {},
  loading: {},
};

const DigestState = window.DigestState;

async function loadTopicDigest(topic, options = {}) {
  const map = {
    nikkei: { btn: 'nikkeiDigestBtn', body: 'nikkeiDigestBody', fetch: 'nikkeiDigestFetch', label: '日经市场' },
    semiconductor: { btn: 'semiDigestBtn', body: 'semiDigestBody', fetch: 'semiDigestFetch', label: '半导体+科技' }
  };
  const cfg = map[topic];
  if (!cfg) return;

  const btn = document.getElementById(cfg.btn);
  const body = document.getElementById(cfg.body);
  const fetchEl = document.getElementById(cfg.fetch);
  if (DigestState.loading[topic]) return;
  DigestState.loading[topic] = true;
  btn.disabled = true;
  body.innerHTML = `<div class="ai-loading"><div class="ai-spinner"></div>正在整理${cfg.label}摘要...</div>`;
  try {
    const data = (!options.force && DigestState.cache[topic]) || await fetchJson(`/api/topic_digest?topic=${encodeURIComponent(topic)}`);
    DigestState.cache[topic] = data;
    body.innerHTML = renderTopicDigest(data);
    if (data.updated) fetchEl.textContent = `更新: ${data.updated}`;
  } catch (e) {
    body.innerHTML = `<div class="ai-placeholder" style="color:var(--down)">获取失败: ${e.message}</div>`;
  }
  DigestState.loading[topic] = false;
  btn.disabled = false;
}

function lazyLoadTopicDigest(topic) {
  if (DigestState.cache[topic] || DigestState.loading[topic]) return;
  loadTopicDigest(topic);
}

function renderTopicDigest(data) {
  const drivers = Array.isArray(data.drivers) ? data.drivers : [];
  const items = Array.isArray(data.items) ? data.items : [];
  return `
    <div class="digest-hero">
      <div class="digest-tone ${data.tone_class || 'neutral'}">${data.tone || '中性'}</div>
      <div class="digest-summary">${data.summary || '当前暂无足够摘要内容，请稍后刷新。'}</div>
    </div>
    <div class="digest-section-title">最重要的 3 个驱动</div>
    <div class="digest-driver-list">
      ${(drivers.length ? drivers : ['暂无主要驱动']).map(driver => `<span class="digest-driver">${driver}</span>`).join('')}
    </div>
    <div class="digest-section-title">最值得看的 3 条</div>
    ${items.length ? items.map(renderDigestItem).join('') : `<div class="ai-placeholder" style="padding:10px 0;text-align:left">当前还没有整理出可展示的重点新闻。</div>`}
  `;
}

function renderDigestItem(it) {
  const title = it.title_zh || it.title || '';
  const brief = it.brief || '';
  return `
    <div class="digest-item">
      <div class="news-meta">
        <span class="news-source">${it.provider || 'News'}</span>
        <span class="news-time">${it.pub || ''}</span>
      </div>
      <div class="digest-item-title">
        ${it.url ? `<a href="${it.url}" target="_blank" rel="noopener">${title}</a>` : title}
      </div>
      ${brief ? `<div class="digest-item-brief">${brief}</div>` : ''}
    </div>
  `;
}
