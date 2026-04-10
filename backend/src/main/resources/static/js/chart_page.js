const ChartPageState = {
  symbol: '',
  name: '',
  interval: 'D',
  chart: null,
  chartData: [],
  activeOverlay: 'cursor',
  showVolume: true,
  showMacd: true,
  measureStart: null,
  resizeObserver: null,
  theme: 'dark',
};

const DRAWING_TOOLS = {
  cursor: null,
  segment: 'segment',
  horizontal: 'horizontalStraightLine',
  fibonacci: 'fibonacciLine',
};

const CUSTOM_MEASURE_TOOLS = new Set(['price_range', 'time_range']);

function pageFmtPrice(value) {
  if (value == null || Number.isNaN(Number(value))) return '--';
  return `¥${Number(value).toLocaleString('ja-JP', { maximumFractionDigits: 2 })}`;
}

function pageFmtSignedPct(value) {
  if (value == null || Number.isNaN(Number(value))) return '--';
  const n = Number(value);
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}%`;
}

function pageFmtSignedNumber(value, digits = 2) {
  if (value == null || Number.isNaN(Number(value))) return '--';
  const n = Number(value);
  return `${n > 0 ? '+' : ''}${n.toFixed(digits)}`;
}

function formatMeasureDate(timestamp) {
  try {
    return new Date(timestamp).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
  } catch (_) {
    return '--';
  }
}

async function fetchChartHistory(symbol, interval, timeoutMs = 15000) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetchApiJson(
      `/api/chart_history?symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}`,
      { signal: controller.signal }
    );
  } catch (error) {
    if (error && error.name === 'AbortError') {
      throw new Error('历史行情请求超时，请稍后重试。');
    }
    throw error;
  } finally {
    window.clearTimeout(timer);
  }
}

function getChartPageDom() {
  return {
    root: document.getElementById('klineChartContainer'),
    measureLayer: document.getElementById('chartMeasureLayer'),
    fallback: document.getElementById('chartFallback'),
    toolHint: document.getElementById('chartToolHint'),
    symbol: document.getElementById('chartPageSymbol'),
    name: document.getElementById('chartPageName'),
    companyProfile: document.getElementById('chartCompanyProfile'),
    businessContent: document.getElementById('chartBusinessContent'),
    productContent: document.getElementById('chartProductContent'),
    sectorTag: document.getElementById('chartSectorTag'),
    trackTag: document.getElementById('chartTrackTag'),
    price: document.getElementById('chartHeroPrice'),
    change: document.getElementById('chartHeroChange'),
    updated: document.getElementById('chartHeroUpdated'),
    drawButtons: Array.from(document.querySelectorAll('[data-draw-tool]')),
    intervalButtons: Array.from(document.querySelectorAll('[data-chart-page-interval]')),
    subpaneButtons: Array.from(document.querySelectorAll('[data-subpane-toggle]')),
    clearButton: document.getElementById('chartClearDrawingsBtn'),
    themeToggleBtn: document.getElementById('chartThemeToggleBtn'),
    insightsBody: document.getElementById('chartInsightsBody'),
    insightsFetch: document.getElementById('chartInsightsFetch'),
    insightsRefreshBtn: document.getElementById('chartInsightsRefreshBtn'),
    ownershipBody: document.getElementById('chartOwnershipBody'),
    ownershipFetch: document.getElementById('chartOwnershipFetch'),
    ownershipRefreshBtn: document.getElementById('chartOwnershipRefreshBtn'),
    newsBody: document.getElementById('chartNewsBody'),
    newsFetch: document.getElementById('chartNewsFetch'),
    newsRefreshBtn: document.getElementById('chartNewsRefreshBtn'),
  };
}

function pageFmtPlain(value, digits = 2) {
  if (value == null || Number.isNaN(Number(value))) return '--';
  return Number(value).toLocaleString('ja-JP', { maximumFractionDigits: digits });
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function isDisplayEmptyValue(rawValue) {
  const value = String(rawValue ?? '').trim();
  return !value || value === '--' || value === 'None' || value.startsWith('暂无');
}

function renderMetricHelp(helpText) {
  if (!helpText) return '';
  const safeText = escapeHtml(helpText);
  return `<span class="metric-help" tabindex="0" aria-label="${safeText}" data-tooltip="${safeText}">?</span>`;
}

function renderMetricLabel(label, helpText, className) {
  return `<div class="${className}"><span>${escapeHtml(label || '--')}</span>${renderMetricHelp(helpText)}</div>`;
}

function renderCompanyProfile(profile) {
  const dom = getChartPageDom();
  if (!dom.companyProfile || !dom.businessContent || !dom.productContent) return;
  dom.businessContent.textContent = String(profile?.business || '').trim() || '暂无覆盖';
  dom.productContent.textContent = String(profile?.products || '').trim() || '暂无覆盖';
  if (dom.sectorTag) dom.sectorTag.textContent = String(profile?.sector || '').trim() || '--';
  if (dom.trackTag) dom.trackTag.textContent = String(profile?.track || '').trim() || '--';
}

function getChartPageStyles() {
  const isLight = ChartPageState.theme === 'light';
  return {
    grid: {
      show: true,
      horizontal: { show: true, color: isLight ? 'rgba(148,163,184,0.18)' : 'rgba(148,163,184,0.10)', style: 'dashed', dashedValue: [3, 3] },
      vertical: { show: true, color: isLight ? 'rgba(148,163,184,0.14)' : 'rgba(148,163,184,0.08)', style: 'dashed', dashedValue: [3, 3] },
    },
    candle: {
      type: 'candle_solid',
      bar: {
        upColor: '#ef4444',
        downColor: '#22c55e',
        noChangeColor: '#94a3b8',
        upBorderColor: '#ef4444',
        downBorderColor: '#22c55e',
        noChangeBorderColor: '#94a3b8',
        upWickColor: '#ef4444',
        downWickColor: '#22c55e',
        noChangeWickColor: '#94a3b8',
      },
      tooltip: { showRule: 'none', showType: 'rect' },
      priceMark: {
        last: {
          line: { show: true, style: 'dashed', dashedValue: [4, 4], size: 1, color: isLight ? 'rgba(71,85,105,0.7)' : undefined },
        },
      },
    },
    indicator: {
      tooltip: { showRule: 'none', showType: 'rect' },
      bars: [
        {
          style: 'fill',
          upColor: 'rgba(239,68,68,0.5)',
          downColor: 'rgba(34,197,94,0.5)',
          noChangeColor: 'rgba(148,163,184,0.4)',
        },
      ],
      lines: [
        { color: '#60a5fa', size: 2 },
        { color: '#f59e0b', size: 2 },
        { color: '#a78bfa', size: 1.5 },
        { color: '#f43f5e', size: 1.5 },
      ],
      lastValueMark: {
        show: false,
      },
    },
    xAxis: {
      axisLine: { color: isLight ? 'rgba(148,163,184,0.28)' : 'rgba(148,163,184,0.18)' },
      tickText: { color: isLight ? '#6b7280' : '#94a3b8' },
    },
    yAxis: {
      axisLine: { color: isLight ? 'rgba(148,163,184,0.28)' : 'rgba(148,163,184,0.18)' },
      tickText: { color: isLight ? '#334155' : '#cbd5e1' },
      tickLine: { color: isLight ? 'rgba(148,163,184,0.12)' : 'rgba(148,163,184,0.08)' },
    },
    separator: {
      color: isLight ? 'rgba(148,163,184,0.2)' : 'rgba(148,163,184,0.14)',
      size: 1,
      fill: true,
      activeBackgroundColor: isLight ? 'rgba(180,138,92,0.12)' : 'rgba(99,102,241,0.12)',
    },
    crosshair: {
      show: true,
      horizontal: {
        line: { color: isLight ? 'rgba(100,116,139,0.4)' : 'rgba(148,163,184,0.32)', style: 'dashed', dashedValue: [4, 4] },
        text: { color: isLight ? '#3f2f24' : '#0b1220', backgroundColor: isLight ? '#f6ead8' : '#e2e8f0' },
      },
      vertical: {
        line: { color: isLight ? 'rgba(100,116,139,0.4)' : 'rgba(148,163,184,0.32)', style: 'dashed', dashedValue: [4, 4] },
        text: { color: isLight ? '#3f2f24' : '#0b1220', backgroundColor: isLight ? '#f6ead8' : '#e2e8f0' },
      },
    },
  };
}

function applyChartTheme(theme) {
  ChartPageState.theme = theme === 'light' ? 'light' : 'dark';
  getChartPageDom().themeToggleBtn?.setAttribute('aria-pressed', ChartPageState.theme === 'light' ? 'true' : 'false');
  rebuildChartForLayoutChange();
}

function initChartTheme() {
  if (typeof initGlobalThemeToggle === 'function') {
    initGlobalThemeToggle();
  }
  applyChartTheme(document.body.dataset.theme || document.documentElement.dataset.theme || 'dark');
}

function buildChartLayout() {
  const layout = [
    { type: 'candle', content: ['MA'], options: { id: 'candle_pane' } },
  ];

  if (ChartPageState.showVolume) {
    layout.push({ type: 'indicator', content: ['VOL'], options: { id: 'vol_pane', height: 64, minHeight: 44 } });
  }

  if (ChartPageState.showMacd) {
    layout.push({ type: 'indicator', content: ['MACD'], options: { id: 'macd_pane', height: 86, minHeight: 58 } });
  }

  layout.push({ type: 'xAxis' });
  return layout;
}

function mapChartHistoryToKline(data) {
  return (data.timestamps || []).map((timestamp, index) => ({
    timestamp,
    open: data.opens[index],
    high: data.highs[index],
    low: data.lows[index],
    close: data.closes[index],
    volume: data.volumes[index],
  })).filter(item => item.timestamp && [item.open, item.high, item.low, item.close].every(v => v != null));
}

function findChartMethod(target, methodName) {
  let current = target;
  while (current) {
    if (typeof current[methodName] === 'function') {
      return current[methodName].bind(target);
    }
    current = Object.getPrototypeOf(current);
  }
  return null;
}

function buildChartPage() {
  const dom = getChartPageDom();
  const containerId = 'klineChartContainer';

  if (ChartPageState.chart) {
    try {
      window.klinecharts.dispose(containerId);
    } catch (_) {}
    ChartPageState.chart = null;
  }

  if (dom.root) {
    dom.root.innerHTML = '';
  }

  ChartPageState.chart = window.klinecharts.init(containerId, {
    locale: 'zh-CN',
    timezone: 'Asia/Tokyo',
    layout: buildChartLayout(),
  });

  const setStyles = findChartMethod(ChartPageState.chart, 'setStyles');
  if (setStyles) {
    try {
      setStyles(getChartPageStyles());
    } catch (_) {}
  }

  return ChartPageState.chart;
}

function applyChartData(chart, dataList) {
  const applyNewData = findChartMethod(chart, 'applyNewData');
  if (applyNewData) {
    applyNewData(dataList, false);
    return;
  }
  const setData = findChartMethod(chart, 'setData');
  if (setData) {
    setData(dataList);
    return;
  }
  throw new Error('当前 KLineChart 版本不支持数据写入接口。');
}

function scrollChartToRealtime(chart) {
  const setOffsetRightDistance = findChartMethod(chart, 'setOffsetRightDistance');
  const setMaxOffsetLeftDistance = findChartMethod(chart, 'setMaxOffsetLeftDistance');
  const setMaxOffsetRightDistance = findChartMethod(chart, 'setMaxOffsetRightDistance');
  const setLeftMinVisibleBarCount = findChartMethod(chart, 'setLeftMinVisibleBarCount');
  const setRightMinVisibleBarCount = findChartMethod(chart, 'setRightMinVisibleBarCount');
  const setBarSpace = findChartMethod(chart, 'setBarSpace');
  const getDataList = findChartMethod(chart, 'getDataList');
  const getSize = findChartMethod(chart, 'getSize');
  const scrollToDataIndex = findChartMethod(chart, 'scrollToDataIndex');
  const scrollToRealTime = findChartMethod(chart, 'scrollToRealTime');

  if (setOffsetRightDistance) setOffsetRightDistance(8);
  if (setMaxOffsetLeftDistance) setMaxOffsetLeftDistance(0);
  if (setMaxOffsetRightDistance) setMaxOffsetRightDistance(24);
  if (setLeftMinVisibleBarCount) setLeftMinVisibleBarCount(1);
  if (setRightMinVisibleBarCount) setRightMinVisibleBarCount(1);

  if (getDataList && getSize && setBarSpace) {
    const dataList = getDataList() || [];
    const size = getSize() || {};
    const width = Number(size.width || 0);
    if (dataList.length > 0 && width > 0) {
      const dynamicBarSpace = Math.max(8, Math.min(28, Math.floor((width - 28) / Math.max(dataList.length, 1))));
      setBarSpace(dynamicBarSpace);
    }
  }

  if (getDataList && scrollToDataIndex) {
    const dataList = getDataList() || [];
    if (dataList.length > 0) {
      scrollToDataIndex(dataList.length - 1);
    }
  }

  if (scrollToRealTime) {
    scrollToRealTime();
  }
}

function updateChartHero(data) {
  const dom = getChartPageDom();
  dom.symbol.textContent = data.symbol || ChartPageState.symbol || '--';
  dom.name.textContent = data.name || ChartPageState.name || data.symbol || '--';
  dom.price.textContent = pageFmtPrice(data.price);
  dom.change.textContent = pageFmtSignedPct(data.changePct);
  dom.change.className = `chart-hero-change ${(data.changePct || 0) >= 0 ? 'up' : 'down'}`;
  dom.updated.textContent = data.updated ? `更新 ${data.updated}` : '';
}

function showChartFallback(message) {
  const dom = getChartPageDom();
  if (dom.root) dom.root.innerHTML = '';
  if (dom.fallback) {
    dom.fallback.style.display = 'block';
    dom.fallback.textContent = message;
  }
}

function clearMeasureLayer() {
  const dom = getChartPageDom();
  if (dom.measureLayer) {
    dom.measureLayer.innerHTML = '';
  }
  ChartPageState.measureStart = null;
}

function getMeasurePoint(event) {
  const dom = getChartPageDom();
  const rect = dom.root?.getBoundingClientRect();
  const data = ChartPageState.chartData || [];
  if (!rect || !data.length) return null;
  const x = Math.min(Math.max(event.clientX - rect.left, 0), rect.width);
  const y = Math.min(Math.max(event.clientY - rect.top, 0), rect.height);
  const index = Math.min(data.length - 1, Math.max(0, Math.round((x / Math.max(rect.width, 1)) * (data.length - 1))));
  const lows = data.map(item => item.low);
  const highs = data.map(item => item.high);
  const minPrice = Math.min(...lows);
  const maxPrice = Math.max(...highs);
  const priceRange = Math.max(maxPrice - minPrice, 1);
  const price = maxPrice - (y / Math.max(rect.height, 1)) * priceRange;
  return {
    x,
    y,
    index,
    price,
    timestamp: data[index]?.timestamp,
  };
}

function renderMeasureResult(start, end, tool) {
  const dom = getChartPageDom();
  if (!dom.measureLayer) return;
  const left = Math.min(start.x, end.x);
  const top = Math.min(start.y, end.y);
  const width = Math.max(2, Math.abs(end.x - start.x));
  const height = Math.max(2, Math.abs(end.y - start.y));
  const boxTop = tool === 'time_range' ? Math.max(18, top) : top;
  const boxHeight = tool === 'time_range' ? 24 : height;

  let label = '';
  if (tool === 'price_range') {
    const diff = end.price - start.price;
    const pct = start.price ? (diff / start.price) * 100 : 0;
    label = `${pageFmtSignedNumber(diff, 2)} · ${diff >= 0 ? '+' : ''}${pct.toFixed(2)}%`;
  } else if (tool === 'time_range') {
    const bars = Math.abs(end.index - start.index) + 1;
    label = `${formatMeasureDate(start.timestamp)} 至 ${formatMeasureDate(end.timestamp)} · ${bars} 根`;
  }

  dom.measureLayer.innerHTML = `
    <div class="chart-measure-box ${tool === 'time_range' ? 'time' : ''}" style="left:${left}px;top:${boxTop}px;width:${width}px;height:${boxHeight}px;"></div>
    <div class="chart-measure-label" style="left:${left + 6}px;top:${Math.max(8, boxTop - 30)}px;">${label}</div>
  `;
}

function handleMeasureClick(event) {
  const tool = ChartPageState.activeOverlay;
  if (!CUSTOM_MEASURE_TOOLS.has(tool)) return;
  const point = getMeasurePoint(event);
  if (!point) return;
  if (!ChartPageState.measureStart) {
    ChartPageState.measureStart = point;
    return;
  }
  renderMeasureResult(ChartPageState.measureStart, point, tool);
  ChartPageState.measureStart = null;
}

function setToolHint(tool) {
  const dom = getChartPageDom();
  if (!dom.toolHint) return;
  const hintMap = {
    cursor: '',
    segment: '趋势线：在图上点击两个位置即可完成绘制。',
    horizontal: '水平线：在图上点击一个价格位置即可放置。',
    fibonacci: '斐波那契回撤：先点起点，再点终点，生成 0% 到 100% 回撤层级。',
    price_range: '价格范围：先点起点，再点终点，测量价格差和涨跌幅。',
    time_range: '日期范围：先点起点，再点终点，测量时间跨度和 K 线根数。',
  };
  dom.toolHint.textContent = hintMap[tool] || '';
}

function setDrawTool(tool) {
  ChartPageState.activeOverlay = tool;
  if (!CUSTOM_MEASURE_TOOLS.has(tool)) {
    ChartPageState.measureStart = null;
  }
  const dom = getChartPageDom();
  dom.drawButtons.forEach(btn => {
    btn.classList.toggle('active', btn.dataset.drawTool === tool);
  });
  setToolHint(tool);
}

function clearDrawings() {
  if (ChartPageState.chart && typeof ChartPageState.chart.removeOverlay === 'function') {
    ChartPageState.chart.removeOverlay({ groupId: 'analysis-tools' });
  }
  clearMeasureLayer();
  setDrawTool('cursor');
}

function startDrawing(tool) {
  if (CUSTOM_MEASURE_TOOLS.has(tool)) {
    clearMeasureLayer();
    setDrawTool(tool);
    return;
  }
  if (!ChartPageState.chart) return;
  const overlayName = DRAWING_TOOLS[tool];
  if (!overlayName) {
    setDrawTool('cursor');
    return;
  }
  setDrawTool(tool);
  if (typeof ChartPageState.chart.createOverlay === 'function') {
    ChartPageState.chart.createOverlay({
      name: overlayName,
      groupId: 'analysis-tools',
      mode: 'weak_magnet',
    });
  }
}

function bindChartPageToolbar() {
  const dom = getChartPageDom();

  dom.intervalButtons.forEach(btn => {
    btn.addEventListener('click', async () => {
      const nextInterval = btn.dataset.chartPageInterval || 'D';
      if (nextInterval === ChartPageState.interval) return;
      ChartPageState.interval = nextInterval;
      dom.intervalButtons.forEach(item => item.classList.toggle('active', item === btn));
      await renderChartPage();
    });
  });

  dom.drawButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const tool = btn.dataset.drawTool || 'cursor';
      if (tool === 'cursor') {
        setDrawTool('cursor');
      } else {
        startDrawing(tool);
      }
    });
  });

  dom.subpaneButtons.forEach(btn => {
    btn.addEventListener('click', async () => {
      const pane = btn.dataset.subpaneToggle;
      if (pane === 'volume') {
        ChartPageState.showVolume = !ChartPageState.showVolume;
        btn.classList.toggle('active', ChartPageState.showVolume);
      }
      if (pane === 'macd') {
        ChartPageState.showMacd = !ChartPageState.showMacd;
        btn.classList.toggle('active', ChartPageState.showMacd);
      }
      await renderChartPage();
    });
  });

  dom.clearButton?.addEventListener('click', clearDrawings);
  dom.root?.addEventListener('click', handleMeasureClick);
  dom.insightsRefreshBtn?.addEventListener('click', () => loadChartInsights(ChartPageState.symbol));
  dom.ownershipRefreshBtn?.addEventListener('click', () => loadOwnershipShort(ChartPageState.symbol));
  dom.newsRefreshBtn?.addEventListener('click', () => loadChartNews(ChartPageState.symbol));
}

function renderOwnershipShort(payload) {
  const dom = getChartPageDom();
  if (!dom.ownershipBody) return;
  if (!payload?.ok || !Array.isArray(payload.cards) || !payload.cards.length) {
    dom.ownershipBody.innerHTML = `<div class="empty-note">${escapeHtml(payload?.error || '机构与空头暂不可用')}</div>`;
    return;
  }
  dom.ownershipBody.innerHTML = payload.cards.map(card => `
    <article class="chart-ownership-item">
      <div class="chart-ownership-head">
        <div class="chart-ownership-title">${card.title || '--'}</div>
        ${card.subtitle ? `<div class="chart-ownership-subtitle">${card.subtitle}</div>` : ''}
      </div>
      <div class="chart-ownership-list">
        ${(card.items || []).map(item => {
          const rawValue = String(item?.value ?? '--').trim();
          const isEmptyValue = isDisplayEmptyValue(rawValue);
          return `
            <div class="chart-ownership-row">
              ${renderMetricLabel(item.label, item.help, 'chart-ownership-label')}
              <div class="chart-ownership-value ${item.tone || 'neutral'}${isEmptyValue ? ' empty' : ''}">${escapeHtml(item.value || '--')}</div>
              ${item.detail ? `<div class="chart-ownership-detail">${escapeHtml(item.detail)}</div>` : ''}
            </div>
          `;
        }).join('')}
      </div>
    </article>
  `).join('');
}

async function loadOwnershipShort(symbol) {
  const dom = getChartPageDom();
  if (!symbol || !dom.ownershipBody) return;
  dom.ownershipFetch.textContent = '机构与空头加载中...';
  dom.ownershipBody.innerHTML = '<div class="empty-note">正在加载机构与空头...</div>';
  try {
    const payload = await fetchApiJson(`/api/ownership_short?symbol=${encodeURIComponent(symbol)}`);
    renderOwnershipShort(payload);
    dom.ownershipFetch.textContent = payload?.updated ? `更新 ${payload.updated}` : `更新 ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`;
  } catch (error) {
    dom.ownershipBody.innerHTML = `<div class="empty-note">${error.message || '机构与空头加载失败'}</div>`;
    dom.ownershipFetch.textContent = '机构与空头不可用';
  }
}

function renderChartInsights(payload) {
  const dom = getChartPageDom();
  if (!dom.insightsBody) return;
  renderCompanyProfile(payload?.profile);
  if (!payload?.ok || !Array.isArray(payload.groups) || !payload.groups.length) {
    dom.insightsBody.innerHTML = `<div class="empty-note">${escapeHtml(payload?.error || '参考信息暂时不可用')}</div>`;
    return;
  }
  dom.insightsBody.innerHTML = payload.groups.map(group => `
    <article class="chart-insight-card">
      <div class="chart-insight-card-head">
        <div>
          <div class="chart-insight-title">${group.title || '--'}</div>
          <div class="chart-insight-subtitle">${group.subtitle || ''}</div>
        </div>
      </div>
      <div class="chart-insight-list">
        ${(group.items || []).map(item => {
          const rawValue = String(item?.value ?? '--').trim();
          const isEmptyValue = isDisplayEmptyValue(rawValue);
          const valueClass = `chart-insight-value ${item.tone || 'neutral'}${isEmptyValue ? ' empty' : ''}`;
          return `
          <div class="chart-insight-item">
            ${renderMetricLabel(item.label, item.help, 'chart-insight-label')}
            <div class="chart-insight-value-row">
              <div class="${valueClass}">${escapeHtml(item.value || '--')}</div>
              ${item.numeric != null ? `<div class="chart-insight-numeric">${pageFmtPlain(item.numeric)}</div>` : ''}
            </div>
            ${item.detail ? `<div class="chart-insight-detail">${escapeHtml(item.detail)}</div>` : ''}
          </div>
        `;
        }).join('')}
      </div>
    </article>
  `).join('');
}

async function loadChartInsights(symbol) {
  const dom = getChartPageDom();
  if (!symbol || !dom.insightsBody) return;
  dom.insightsFetch.textContent = '研究面板加载中...';
  dom.insightsBody.innerHTML = '<div class="empty-note">正在加载研究面板...</div>';
  try {
    const payload = await fetchApiJson(`/api/stock_insights?symbol=${encodeURIComponent(symbol)}`);
    renderChartInsights(payload);
    dom.insightsFetch.textContent = payload?.updated ? `更新 ${payload.updated}` : `更新 ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`;
  } catch (error) {
    dom.insightsBody.innerHTML = `<div class="empty-note">${error.message || '研究面板加载失败'}</div>`;
    dom.insightsFetch.textContent = '研究面板不可用';
  }
}

function renderChartNewsItems(items) {
  const dom = getChartPageDom();
  if (!items || !items.length) {
    dom.newsBody.innerHTML = '<div class="empty-note">暂时没有最新资讯。</div>';
    return;
  }
  dom.newsBody.innerHTML = `
    <div class="chart-news-grid">
      ${items.slice(0, 6).map(item => `
        <article class="chart-news-item">
          <div class="chart-news-meta">
            <span>${item.provider || 'News'}</span>
            <span>${item.pub || ''}</span>
          </div>
          <div class="chart-news-title">
            <a href="${item.url || '#'}" target="_blank" rel="noopener">${item.title || item.titleEn || '未命名资讯'}</a>
          </div>
          ${item.titleEn && item.titleEn !== item.title ? `<div class="news-title-en">${item.titleEn}</div>` : ''}
          <div class="chart-news-summary">${item.summary || ''}</div>
        </article>
      `).join('')}
    </div>
  `;
}

async function loadChartNews(symbol) {
  const dom = getChartPageDom();
  if (!symbol || !dom.newsBody) return;
  dom.newsFetch.textContent = '资讯加载中...';
  dom.newsBody.innerHTML = '<div class="empty-note">正在获取个股最新资讯...</div>';
  try {
    const items = await fetchApiJson(`/api/stock_news?symbol=${encodeURIComponent(symbol)}`);
    renderChartNewsItems(items || []);
    dom.newsFetch.textContent = `更新 ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`;
  } catch (error) {
    dom.newsBody.innerHTML = `<div class="empty-note">${error.message || '资讯加载失败'}</div>`;
    dom.newsFetch.textContent = '资讯不可用';
  }
}

async function renderChartPage() {
  const dom = getChartPageDom();
  if (!ChartPageState.symbol) {
    showChartFallback('未选择股票，无法显示图表。');
    return;
  }
  if (!window.klinecharts?.init) {
    showChartFallback('KLineChart 组件未成功加载，请检查网络后重试。');
    return;
  }

  if (dom.fallback) {
    dom.fallback.style.display = 'none';
  }
  if (dom.root) {
    dom.root.innerHTML = '<div class="chart-loading">图表加载中...</div>';
  }
  clearMeasureLayer();
  setDrawTool('cursor');

  try {
    const data = await fetchChartHistory(ChartPageState.symbol, ChartPageState.interval);
    if (!data?.ok) {
      throw new Error(data?.error || '图表数据获取失败。');
    }
    ChartPageState.chartData = mapChartHistoryToKline(data);
    const chart = buildChartPage();
    applyChartData(chart, ChartPageState.chartData);
    scrollChartToRealtime(chart);
    updateChartHero(data);
  } catch (error) {
    console.error(error);
    showChartFallback(error.message || '图表数据获取失败。');
  }
}

async function openChartSymbol(symbol, name) {
  const nextSymbol = (symbol || '').trim().toUpperCase();
  if (!nextSymbol) return;
  ChartPageState.symbol = nextSymbol;
  ChartPageState.name = name || nextSymbol;
  document.body.dataset.chartSymbol = nextSymbol;
  document.body.dataset.chartName = ChartPageState.name;
  const nextUrl = `/chart?symbol=${encodeURIComponent(nextSymbol)}&name=${encodeURIComponent(ChartPageState.name)}`;
  window.history.replaceState({}, '', nextUrl);
  await renderChartPage();
  await loadChartInsights(nextSymbol);
  await loadOwnershipShort(nextSymbol);
  await loadChartNews(nextSymbol);
}

function resizeChartPage() {
  const resize = findChartMethod(ChartPageState.chart, 'resize');
  if (resize) {
    resize();
  }
}

function triggerChartResizeBurst() {
  resizeChartPage();
  window.setTimeout(resizeChartPage, 60);
  window.setTimeout(resizeChartPage, 180);
  window.setTimeout(resizeChartPage, 320);
}

function rebuildChartForLayoutChange() {
  if (!ChartPageState.symbol || !ChartPageState.chartData.length) return;
  try {
    const chart = buildChartPage();
    applyChartData(chart, ChartPageState.chartData);
    scrollChartToRealtime(chart);
  } catch (error) {
    console.error(error);
  }
}

function bindChartResizeObservers() {
  const dom = getChartPageDom();
  const resizeTarget = dom.root?.parentElement || dom.root;
  if (!resizeTarget || typeof ResizeObserver === 'undefined') return;
  if (ChartPageState.resizeObserver) {
    try {
      ChartPageState.resizeObserver.disconnect();
    } catch (_) {}
  }
  ChartPageState.resizeObserver = new ResizeObserver(() => {
    triggerChartResizeBurst();
  });
  ChartPageState.resizeObserver.observe(resizeTarget);
}

function initChartPage() {
  ChartPageState.symbol = document.body.dataset.chartSymbol || '';
  ChartPageState.name = document.body.dataset.chartName || '';
  initChartTheme();
  window.addEventListener('global-theme:change', event => {
    applyChartTheme(event.detail?.theme || 'dark');
  });
  bindChartPageToolbar();
  renderChartPage();
  loadChartInsights(ChartPageState.symbol);
  loadOwnershipShort(ChartPageState.symbol);
  loadChartNews(ChartPageState.symbol);
  bindChartResizeObservers();
  window.addEventListener('resize', triggerChartResizeBurst);
  window.addEventListener('watchlist:toggle', () => {
    triggerChartResizeBurst();
    window.setTimeout(rebuildChartForLayoutChange, 260);
  });
}

window.openChartSymbol = openChartSymbol;
document.addEventListener('DOMContentLoaded', initChartPage);
