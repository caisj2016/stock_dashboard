const ChartPage = {
  interval: 'D',
};

const ChartRuntime = {
  loader: null,
  charts: new Map(),
  measureMode: 'none',
  measureDraft: null,
  activeMeasurement: null,
};

function renderChartNewsItem(it) {
  return `
    <article class="chart-news-item">
      <div class="news-meta">
        <span class="news-source">${it.provider || ''}</span>
        <span class="news-time">${it.pub || ''}</span>
      </div>
      <div class="chart-news-title">
        ${it.url ? `<a href="${it.url}" target="_blank" rel="noopener">${it.title}</a>` : (it.title || '')}
      </div>
      ${it.titleEn && it.titleEn !== it.title ? `<div class="news-title-en">${it.titleEn}</div>` : ''}
      ${it.summary ? `<div class="chart-news-summary">${it.summary}</div>` : ''}
    </article>
  `;
}

async function loadChartNews() {
  const body = document.getElementById('chartNewsBody');
  const btn = document.getElementById('chartNewsBtn');
  const fetchEl = document.getElementById('chartNewsFetch');
  const symbol = window.ChartPageConfig?.symbol;
  if (!body || !btn || !symbol) return;

  btn.disabled = true;
  body.innerHTML = `<div class="ai-loading"><div class="ai-spinner"></div>正在抓取 ${symbol} 的最新资讯...</div>`;
  try {
    const items = await fetchApiJson(`/api/stock_news?symbol=${encodeURIComponent(symbol)}`);
    if (!items.length) {
      body.innerHTML = `<div class="ai-placeholder">暂时没有找到相关新闻</div>`;
    } else {
      body.innerHTML = `
        <div class="chart-news-grid">
          ${items.slice(0, 6).map(renderChartNewsItem).join('')}
        </div>
      `;
    }
    const now = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    fetchEl.textContent = `更新: ${now}`;
  } catch (error) {
    body.innerHTML = `<div class="ai-placeholder" style="color:var(--down)">获取失败: ${error.message}</div>`;
  }
  btn.disabled = false;
}

function addCandlestickSeriesCompat(chart, options) {
  if (typeof chart.addCandlestickSeries === 'function') {
    return chart.addCandlestickSeries(options);
  }
  return chart.addSeries(window.LightweightCharts.CandlestickSeries, options);
}

function addLineSeriesCompat(chart, options) {
  if (typeof chart.addLineSeries === 'function') {
    return chart.addLineSeries(options);
  }
  return chart.addSeries(window.LightweightCharts.LineSeries, options);
}

function addHistogramSeriesCompat(chart, options) {
  if (typeof chart.addHistogramSeries === 'function') {
    return chart.addHistogramSeries(options);
  }
  return chart.addSeries(window.LightweightCharts.HistogramSeries, options);
}

function ensureLightweightCharts() {
  if (window.LightweightCharts) return Promise.resolve(window.LightweightCharts);
  if (ChartRuntime.loader) return ChartRuntime.loader;

  ChartRuntime.loader = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://unpkg.com/lightweight-charts/dist/lightweight-charts.standalone.production.js';
    script.async = true;
    script.onload = () => {
      if (window.LightweightCharts) resolve(window.LightweightCharts);
      else reject(new Error('Lightweight Charts failed to initialize'));
    };
    script.onerror = () => reject(new Error('Lightweight Charts failed to load'));
    document.head.appendChild(script);
  });

  return ChartRuntime.loader;
}

function chartIntervalLabel(interval) {
  return {
    D: '日线',
    W: '周线',
    M: '月线',
    240: '4小时',
  }[interval] || interval;
}

function lineSeriesData(dates, values) {
  const result = [];
  for (let i = 0; i < dates.length; i += 1) {
    const value = values[i];
    if (value == null) continue;
    result.push({ time: dates[i], value });
  }
  return result;
}

function candleSeriesData(data) {
  return data.dates.map((time, i) => ({
    time,
    open: data.opens[i],
    high: data.highs[i],
    low: data.lows[i],
    close: data.closes[i],
  }));
}

function volumeSeriesData(data) {
  return data.dates.map((time, i) => ({
    time,
    value: data.volumes[i],
    color: data.closes[i] >= data.opens[i] ? 'rgba(239,68,68,0.75)' : 'rgba(34,197,94,0.75)',
  }));
}

function histogramData(dates, values) {
  return dates.map((time, i) => {
    const value = values[i];
    return {
      time,
      value: value == null ? 0 : value,
      color: (value || 0) >= 0 ? 'rgba(239,68,68,0.75)' : 'rgba(34,197,94,0.75)',
    };
  });
}

function sharedChartOptions(height) {
  return {
    autoSize: true,
    height,
    layout: {
      background: { color: '#1a1f2e' },
      textColor: '#cbd5e1',
      fontFamily: "'Noto Sans JP', sans-serif",
    },
    grid: {
      vertLines: { color: 'rgba(255,255,255,0.04)' },
      horzLines: { color: 'rgba(255,255,255,0.05)' },
    },
    rightPriceScale: {
      borderColor: 'rgba(255,255,255,0.10)',
    },
    timeScale: {
      borderColor: 'rgba(255,255,255,0.10)',
      timeVisible: true,
      secondsVisible: false,
    },
    crosshair: {
      vertLine: { color: 'rgba(148,163,184,0.35)', width: 1, style: 2 },
      horzLine: { color: 'rgba(148,163,184,0.35)', width: 1, style: 2 },
    },
  };
}

function cleanupChartBundle(containerId) {
  const current = ChartRuntime.charts.get(containerId);
  if (current) {
    current.resizeObserver?.disconnect();
    current.overlay?.removeEventListener('click', current.handleOverlayClick);
    current.charts?.forEach(chart => chart.remove());
    ChartRuntime.charts.delete(containerId);
  }
}

function buildLocalChartShell(container) {
  container.innerHTML = `
    <div class="lw-chart-stack">
      <div class="lw-pane-wrap">
        <div class="lw-pane-head">
          <div class="lw-pane-title">价格走势</div>
          <div class="lw-pane-legend" id="${container.id}PriceLegend"></div>
        </div>
        <div class="lw-pane-frame">
          <div class="lw-pane lw-pane-price"></div>
          <div class="lw-measure-overlay" id="${container.id}MeasureOverlay"></div>
        </div>
      </div>
      <div class="lw-pane-wrap">
        <div class="lw-pane-head">
          <div class="lw-pane-title">成交量</div>
          <div class="lw-pane-legend" id="${container.id}VolumeLegend"></div>
        </div>
        <div class="lw-pane lw-pane-volume"></div>
      </div>
      <div class="lw-pane-wrap">
        <div class="lw-pane-head">
          <div class="lw-pane-title">MACD</div>
          <div class="lw-pane-legend" id="${container.id}MacdLegend"></div>
        </div>
        <div class="lw-pane lw-pane-macd"></div>
      </div>
    </div>
  `;
  return {
    price: container.querySelector('.lw-pane-price'),
    volume: container.querySelector('.lw-pane-volume'),
    macd: container.querySelector('.lw-pane-macd'),
    overlay: container.querySelector('.lw-measure-overlay'),
    priceLegend: container.querySelector(`#${container.id}PriceLegend`),
    volumeLegend: container.querySelector(`#${container.id}VolumeLegend`),
    macdLegend: container.querySelector(`#${container.id}MacdLegend`),
  };
}

function applySharedVisibleRange(sourceChart, targetCharts) {
  sourceChart.timeScale().subscribeVisibleLogicalRangeChange(range => {
    targetCharts.forEach(chart => chart.timeScale().setVisibleLogicalRange(range));
  });
}

function updateChartMeta(prefix, data) {
  const macdEl = document.getElementById(`${prefix}Macd`);
  const rsiEl = document.getElementById(`${prefix}Rsi`);
  const volumeEl = document.getElementById(`${prefix}Volume`);
  const lastMacd = [...(data.macd || [])].reverse().find(v => v != null);
  const lastVolume = [...(data.volumes || [])].reverse().find(v => v != null);

  if (macdEl) macdEl.textContent = lastMacd != null ? lastMacd.toFixed(4) : '--';
  if (rsiEl) rsiEl.textContent = data.rsi14 != null ? data.rsi14.toFixed(2) : '--';
  if (volumeEl) volumeEl.textContent = lastVolume != null ? lastVolume.toLocaleString('ja-JP') : '--';
}

function setLegendContent(el, chips) {
  if (!el) return;
  el.innerHTML = chips.map(chip => `
    <span class="lw-legend-chip">
      <span class="lw-legend-dot" style="background:${chip.color}"></span>
      <span class="lw-legend-name">${chip.label}</span>
      <span class="lw-legend-value">${chip.value}</span>
    </span>
  `).join('');
}

function bindMeasurementToolbar() {
  const toolbar = document.getElementById('measureToolbar');
  if (!toolbar) return;

  document.querySelectorAll('[data-measure-mode]').forEach(btn => {
    btn.addEventListener('click', () => {
      ChartRuntime.measureMode = btn.dataset.measureMode || 'none';
      ChartRuntime.measureDraft = null;
      document.querySelectorAll('[data-measure-mode]').forEach(item => item.classList.remove('active'));
      btn.classList.add('active');
      updateMeasureHint();
      rerenderMeasurementOverlay('tvChartContainer');
    });
  });

  const clearBtn = document.getElementById('measureClearBtn');
  if (clearBtn) {
    clearBtn.addEventListener('click', () => {
      ChartRuntime.activeMeasurement = null;
      ChartRuntime.measureDraft = null;
      rerenderMeasurementOverlay('tvChartContainer');
      updateMeasureHint();
    });
  }
}

function updateMeasureHint() {
  const hint = document.getElementById('measureHint');
  if (!hint) return;
  const map = {
    none: '普通查看模式，不会在图表上添加测量标记。',
    price_range: '价格范围：在主图上点击两个位置，测量价格差和涨跌幅。',
    time_range: '时间范围：在主图上点击两个位置，测量K线数量和时间跨度。',
    fibonacci: '费波那契回撤：先点起点，再点终点，自动画出回撤线。',
  };
  hint.textContent = map[ChartRuntime.measureMode] || map.none;
}

function formatMeasurePoint(point) {
  return {
    index: point.index,
    price: point.price,
    date: point.date,
  };
}

function getNearestIndexFromCoordinate(bundle, x) {
  const logical = bundle.priceChart.timeScale().coordinateToLogical(x);
  if (logical == null) return null;
  const index = Math.max(0, Math.min(bundle.data.dates.length - 1, Math.round(logical)));
  return index;
}

function readMeasurePoint(bundle, event) {
  const rect = bundle.overlay.getBoundingClientRect();
  const x = event.clientX - rect.left;
  const y = event.clientY - rect.top;
  const index = getNearestIndexFromCoordinate(bundle, x);
  if (index == null) return null;
  const price = bundle.candleSeries.coordinateToPrice(y);
  if (price == null) return null;
  return formatMeasurePoint({
    index,
    price,
    date: bundle.data.dates[index],
  });
}

function measurementToScreen(bundle, point) {
  const x = bundle.priceChart.timeScale().logicalToCoordinate(point.index);
  const y = bundle.candleSeries.priceToCoordinate(point.price);
  return { x, y };
}

function buildPriceRangeOverlay(bundle, measurement) {
  const start = measurementToScreen(bundle, measurement.start);
  const end = measurementToScreen(bundle, measurement.end);
  if (start.x == null || end.x == null || start.y == null || end.y == null) return '';

  const left = Math.min(start.x, end.x);
  const top = Math.min(start.y, end.y);
  const width = Math.max(2, Math.abs(end.x - start.x));
  const height = Math.max(2, Math.abs(end.y - start.y));
  const diff = measurement.end.price - measurement.start.price;
  const pct = measurement.start.price ? (diff / measurement.start.price) * 100 : 0;
  const label = `${diff >= 0 ? '+' : ''}${diff.toFixed(2)} (${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%)`;

  return `
    <div class="measure-rect" style="left:${left}px;top:${top}px;width:${width}px;height:${height}px"></div>
    <div class="measure-label" style="left:${left + width + 8}px;top:${top}px">${label}</div>
  `;
}

function buildTimeRangeOverlay(bundle, measurement) {
  const start = measurementToScreen(bundle, measurement.start);
  const end = measurementToScreen(bundle, measurement.end);
  if (start.x == null || end.x == null) return '';
  const left = Math.min(start.x, end.x);
  const width = Math.max(2, Math.abs(end.x - start.x));
  const bars = Math.abs(measurement.end.index - measurement.start.index) + 1;
  const label = `${bars} 根K线 · ${measurement.start.date} -> ${measurement.end.date}`;

  return `
    <div class="measure-time-line" style="left:${left}px;width:${width}px"></div>
    <div class="measure-time-cap" style="left:${left}px"></div>
    <div class="measure-time-cap" style="left:${left + width}px"></div>
    <div class="measure-label measure-label-bottom" style="left:${left}px">${label}</div>
  `;
}

function buildFibonacciOverlay(bundle, measurement) {
  const start = measurementToScreen(bundle, measurement.start);
  const end = measurementToScreen(bundle, measurement.end);
  if (start.x == null || end.x == null || start.y == null || end.y == null) return '';

  const left = Math.min(start.x, end.x);
  const width = Math.max(80, Math.abs(end.x - start.x));
  const high = Math.max(measurement.start.price, measurement.end.price);
  const low = Math.min(measurement.start.price, measurement.end.price);
  const range = high - low || 1;
  const fibLevels = [0, 0.236, 0.382, 0.5, 0.618, 0.786, 1];

  return fibLevels.map(level => {
    const price = high - range * level;
    const y = bundle.candleSeries.priceToCoordinate(price);
    if (y == null) return '';
    const percent = `${(level * 100).toFixed(level === 0 || level === 1 ? 0 : 1)}%`;
    return `
      <div class="measure-fib-line" style="left:${left}px;width:${width}px;top:${y}px"></div>
      <div class="measure-fib-label" style="left:${left + width + 8}px;top:${Math.max(0, y - 8)}px">${percent} (${price.toFixed(2)})</div>
    `;
  }).join('');
}

function rerenderMeasurementOverlay(containerId) {
  const bundle = ChartRuntime.charts.get(containerId);
  if (!bundle?.overlay) return;
  if (!bundle.isMeasureEnabled) {
    bundle.overlay.innerHTML = '';
    return;
  }

  if (!ChartRuntime.activeMeasurement) {
    bundle.overlay.innerHTML = ChartRuntime.measureDraft ? '<div class="measure-draft-tip">已选择起点，再点击一次完成测量</div>' : '';
    return;
  }

  const measurement = ChartRuntime.activeMeasurement;
  if (measurement.mode === 'price_range') {
    bundle.overlay.innerHTML = buildPriceRangeOverlay(bundle, measurement);
  } else if (measurement.mode === 'time_range') {
    bundle.overlay.innerHTML = buildTimeRangeOverlay(bundle, measurement);
  } else if (measurement.mode === 'fibonacci') {
    bundle.overlay.innerHTML = buildFibonacciOverlay(bundle, measurement);
  } else {
    bundle.overlay.innerHTML = '';
  }
}

function handleMeasureClick(containerId, event) {
  const bundle = ChartRuntime.charts.get(containerId);
  if (!bundle || ChartRuntime.measureMode === 'none') return;
  const point = readMeasurePoint(bundle, event);
  if (!point) return;

  if (!ChartRuntime.measureDraft) {
    ChartRuntime.measureDraft = point;
    ChartRuntime.activeMeasurement = null;
  } else {
    ChartRuntime.activeMeasurement = {
      mode: ChartRuntime.measureMode,
      start: ChartRuntime.measureDraft,
      end: point,
    };
    ChartRuntime.measureDraft = null;
  }
  rerenderMeasurementOverlay(containerId);
}

async function renderLightweightChart(containerId, fallbackId, symbol, interval, metaPrefix = '') {
  const container = document.getElementById(containerId);
  const fallback = document.getElementById(fallbackId);
  if (!container || !symbol) return;

  cleanupChartBundle(containerId);
  container.innerHTML = '';
  if (fallback) {
    fallback.style.display = 'none';
    fallback.textContent = '图表数据加载中...';
  }

  try {
    await ensureLightweightCharts();
    const data = await fetchApiJson(`/api/chart_history?symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}`);
    if (!data.ok) throw new Error(data.error || '图表数据获取失败');

    const panes = buildLocalChartShell(container);
    const { createChart } = window.LightweightCharts;

    const priceChart = createChart(panes.price, sharedChartOptions(360));
    const volumeChart = createChart(panes.volume, {
      ...sharedChartOptions(120),
      handleScroll: false,
      handleScale: false,
      localization: { locale: 'zh-CN' },
    });
    const macdChart = createChart(panes.macd, {
      ...sharedChartOptions(140),
      handleScroll: false,
      handleScale: false,
      localization: { locale: 'zh-CN' },
    });

    const candleSeries = addCandlestickSeriesCompat(priceChart, {
      upColor: '#ef4444',
      downColor: '#22c55e',
      borderVisible: false,
      wickUpColor: '#ef4444',
      wickDownColor: '#22c55e',
      priceLineVisible: true,
      lastValueVisible: true,
    });
    candleSeries.setData(candleSeriesData(data));

    const ma5Series = addLineSeriesCompat(priceChart, {
      color: '#60a5fa',
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: false,
    });
    ma5Series.setData(lineSeriesData(data.dates, data.ma5));

    const ma20Series = addLineSeriesCompat(priceChart, {
      color: '#f59e0b',
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: false,
    });
    ma20Series.setData(lineSeriesData(data.dates, data.ma20));

    const volumeSeries = addHistogramSeriesCompat(volumeChart, {
      priceFormat: { type: 'volume' },
      priceLineVisible: false,
      lastValueVisible: false,
    });
    volumeSeries.setData(volumeSeriesData(data));

    const macdHistSeries = addHistogramSeriesCompat(macdChart, {
      priceLineVisible: false,
      lastValueVisible: false,
    });
    macdHistSeries.setData(histogramData(data.dates, data.hist));

    const macdLineSeries = addLineSeriesCompat(macdChart, {
      color: '#60a5fa',
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: false,
    });
    macdLineSeries.setData(lineSeriesData(data.dates, data.macd));

    const signalSeries = addLineSeriesCompat(macdChart, {
      color: '#f59e0b',
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: false,
    });
    signalSeries.setData(lineSeriesData(data.dates, data.signal));

    const lastClose = data.closes[data.closes.length - 1];
    const lastMa5 = [...data.ma5].reverse().find(v => v != null);
    const lastMa20 = [...data.ma20].reverse().find(v => v != null);
    const lastVolume = data.volumes[data.volumes.length - 1];
    const lastMacd = [...data.macd].reverse().find(v => v != null);
    const lastSignal = [...data.signal].reverse().find(v => v != null);
    const lastHist = [...data.hist].reverse().find(v => v != null);

    setLegendContent(panes.priceLegend, [
      { label: '收盘', value: lastClose != null ? `¥${lastClose.toLocaleString('ja-JP')}` : '--', color: '#cbd5e1' },
      { label: 'MA5', value: lastMa5 != null ? lastMa5.toFixed(2) : '--', color: '#60a5fa' },
      { label: 'MA20', value: lastMa20 != null ? lastMa20.toFixed(2) : '--', color: '#f59e0b' },
    ]);
    setLegendContent(panes.volumeLegend, [
      { label: '成交量', value: lastVolume != null ? lastVolume.toLocaleString('ja-JP') : '--', color: '#ef4444' },
      { label: '量比', value: data.volumeRatio != null ? `${data.volumeRatio.toFixed(2)}x` : '--', color: '#a78bfa' },
    ]);
    setLegendContent(panes.macdLegend, [
      { label: 'MACD', value: lastMacd != null ? lastMacd.toFixed(4) : '--', color: '#60a5fa' },
      { label: 'Signal', value: lastSignal != null ? lastSignal.toFixed(4) : '--', color: '#f59e0b' },
      { label: 'Hist', value: lastHist != null ? lastHist.toFixed(4) : '--', color: lastHist >= 0 ? '#ef4444' : '#22c55e' },
    ]);

    priceChart.timeScale().fitContent();
    volumeChart.timeScale().fitContent();
    macdChart.timeScale().fitContent();
    applySharedVisibleRange(priceChart, [volumeChart, macdChart]);

    const resizeObserver = new ResizeObserver(() => {
      const priceRect = panes.price.getBoundingClientRect();
      const volumeRect = panes.volume.getBoundingClientRect();
      const macdRect = panes.macd.getBoundingClientRect();
      if (priceRect.width) priceChart.applyOptions({ width: priceRect.width });
      if (volumeRect.width) volumeChart.applyOptions({ width: volumeRect.width });
      if (macdRect.width) macdChart.applyOptions({ width: macdRect.width });
      rerenderMeasurementOverlay(containerId);
    });
    resizeObserver.observe(container);

    const handleOverlayClick = event => handleMeasureClick(containerId, event);
    const isMeasureEnabled = containerId === 'tvChartContainer';
    if (isMeasureEnabled) {
      panes.overlay.addEventListener('click', handleOverlayClick);
      priceChart.timeScale().subscribeVisibleLogicalRangeChange(() => rerenderMeasurementOverlay(containerId));
    } else {
      panes.overlay.style.display = 'none';
    }

    ChartRuntime.charts.set(containerId, {
      charts: [priceChart, volumeChart, macdChart],
      priceChart,
      candleSeries,
      overlay: panes.overlay,
      handleOverlayClick,
      isMeasureEnabled,
      resizeObserver,
      data,
    });

    if (metaPrefix) updateChartMeta(metaPrefix, data);
    if (isMeasureEnabled) rerenderMeasurementOverlay(containerId);
  } catch (error) {
    container.innerHTML = '';
    if (fallback) {
      fallback.textContent = error.message || '图表加载失败';
      fallback.style.display = 'block';
    }
  }
}

function createTradingViewWidget(containerId, fallbackId, symbol, interval) {
  const metaPrefix = containerId === 'tvChartContainer' ? 'chartPage' : (containerId === 'tvChartModalContainer' ? 'chartModal' : '');
  return renderLightweightChart(containerId, fallbackId, symbol, interval, metaPrefix);
}

function bindChartIntervals() {
  document.querySelectorAll('[data-interval]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-interval]').forEach(item => item.classList.remove('active'));
      btn.classList.add('active');
      ChartPage.interval = btn.dataset.interval || 'D';
      ChartRuntime.measureDraft = null;
      ChartRuntime.activeMeasurement = null;
      renderTradingViewChart();
    });
  });
}

function renderTradingViewChart() {
  return createTradingViewWidget(
    'tvChartContainer',
    'chartFallback',
    window.ChartPageConfig?.symbol,
    ChartPage.interval
  );
}

if (window.ChartPageConfig) {
  bindChartIntervals();
  bindMeasurementToolbar();
  updateMeasureHint();
  renderTradingViewChart();
  loadChartNews();
  document.getElementById('chartNewsBtn')?.addEventListener('click', loadChartNews);
}
