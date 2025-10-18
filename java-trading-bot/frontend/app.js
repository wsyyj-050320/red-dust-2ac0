const API_BASE = window.API_BASE || 'http://localhost:8090/api';
const STREAM_URL = window.STREAM_URL || 'http://localhost:8090/stream';

const overviewEl = document.querySelector('#overview');
const gridEl = document.querySelector('#contract-grid');
const symbolSelect = document.querySelector('#symbol-select');
const chartSymbolEl = document.querySelector('#chart-symbol');
const ma20El = document.querySelector('#ma20');
const ma50El = document.querySelector('#ma50');
const volatilityEl = document.querySelector('#volatility');
const volumeEl = document.querySelector('#volume');
const positionsBody = document.querySelector('#positions-table tbody');
const signalsTable = document.querySelector('#signals-table');
const backtestTable = document.querySelector('#backtest-table');

const cards = new Map();
const state = {
  signals: [],
  positions: [],
  backtests: [],
  candles: new Map(),
  symbols: [],
  selectedSymbol: null
};

let chart;
let candleSeries;
let volumeSeries;

function initChart() {
  const container = document.querySelector('#chart-container');
  if (!container || !window.LightweightCharts) {
    console.warn('未找到 LightweightCharts 库或容器');
    return;
  }
  chart = window.LightweightCharts.createChart(container, {
    layout: {
      background: { color: 'rgba(15,23,42,0.7)' },
      textColor: '#e2e8f0',
      fontFamily: 'JetBrains Mono, monospace'
    },
    grid: {
      vertLines: { color: 'rgba(148,163,184,0.15)' },
      horzLines: { color: 'rgba(148,163,184,0.1)' }
    },
    crosshair: { mode: window.LightweightCharts.CrosshairMode.Normal },
    timeScale: { borderColor: 'rgba(148,163,184,0.3)' },
    rightPriceScale: { borderColor: 'rgba(148,163,184,0.3)' },
    autoSize: true
  });

  candleSeries = chart.addCandlestickSeries({
    upColor: 'rgba(34,197,94,0.9)',
    borderUpColor: 'rgba(34,197,94,1)',
    wickUpColor: 'rgba(34,197,94,1)',
    downColor: 'rgba(248,113,113,0.9)',
    borderDownColor: 'rgba(248,113,113,1)',
    wickDownColor: 'rgba(248,113,113,1)'
  });

  volumeSeries = chart.addHistogramSeries({
    color: 'rgba(59,130,246,0.6)',
    priceFormat: { type: 'volume' },
    priceScaleId: 'volume'
  });

  chart.priceScale('volume').applyOptions({
    scaleMargins: { top: 0.8, bottom: 0.02 },
    borderVisible: false
  });
}

function normaliseCandles(raw) {
  return raw
    .map(([time, open, high, low, close, volume]) => ({
      time: Math.floor(new Date(time).getTime() / 1000),
      iso: time,
      open: Number(open),
      high: Number(high),
      low: Number(low),
      close: Number(close),
      volume: Number(volume)
    }))
    .sort((a, b) => a.time - b.time);
}

async function loadInitial() {
  try {
    const [signalsRes, backtestRes, portfolioRes, candleRes] = await Promise.all([
      fetch(`${API_BASE}/signals`),
      fetch(`${API_BASE}/backtest`),
      fetch(`${API_BASE}/portfolio`),
      fetch(`${API_BASE}/candles`)
    ]);

    if (!signalsRes.ok || !backtestRes.ok || !portfolioRes.ok || !candleRes.ok) {
      throw new Error('接口返回异常');
    }

    const [signals, backtests, portfolio, candlesPayload] = await Promise.all([
      signalsRes.json(),
      backtestRes.json(),
      portfolioRes.json(),
      candleRes.json()
    ]);

    updateCandles(candlesPayload);
    updateState({ signals, positions: portfolio, backtests });
  } catch (error) {
    overviewEl.innerHTML = `<article class="stat-card danger">无法加载数据：${error.message}</article>`;
  }
}

function updateCandles(payload) {
  if (!payload || typeof payload !== 'object') return;
  const entries = Object.entries(payload);
  entries.forEach(([symbol, series]) => {
    state.candles.set(symbol, normaliseCandles(series));
  });
  refreshSymbols();
  refreshSelectedSymbol();
  renderSymbolOptions();
  renderCards();
  applySelectedSymbol();
}

function refreshSymbols() {
  const symbolSet = new Set(state.symbols);
  state.signals.forEach((sig) => symbolSet.add(sig.symbol));
  state.positions.forEach((pos) => symbolSet.add(pos.symbol));
  Array.from(state.candles.keys()).forEach((symbol) => symbolSet.add(symbol));
  state.symbols = Array.from(symbolSet.values()).sort();
}

function refreshSelectedSymbol() {
  if (state.selectedSymbol && state.symbols.includes(state.selectedSymbol)) {
    return;
  }
  state.selectedSymbol = state.symbols[0] || null;
}

function renderSymbolOptions() {
  if (!symbolSelect) return;
  const current = state.selectedSymbol;
  symbolSelect.innerHTML = state.symbols
    .map((symbol) => `<option value="${symbol}" ${symbol === current ? 'selected' : ''}>${symbol}</option>`)
    .join('');
}

function updateState({ signals = [], positions = [], backtests = [] }) {
  state.signals = signals;
  state.positions = positions;
  state.backtests = backtests;
  refreshSymbols();
  refreshSelectedSymbol();
  renderSymbolOptions();
  renderOverview();
  renderPositions();
  renderCards();
  renderSignalsTable();
  renderBacktestTable();
  applySelectedSymbol();
}

function ensureCard(symbol) {
  if (cards.has(symbol)) return;
  const article = document.createElement('article');
  article.className = 'contract-card';
  article.dataset.symbol = symbol;
  article.innerHTML = `
    <div class="status">
      <div>
        <div class="symbol">${symbol}</div>
        <small id="latest-time-${symbol}" class="muted">等待数据...</small>
      </div>
      <span class="badge" id="signal-${symbol}">等待信号</span>
    </div>
    <div class="price-line">
      <strong id="price-${symbol}">--</strong>
      <span id="change-${symbol}" class="change muted">--</span>
    </div>
    <div class="mini-metrics">
      <div>
        <small>仓位</small>
        <span id="position-${symbol}">--</span>
      </div>
      <div>
        <small>回测收益</small>
        <span id="return-${symbol}">--</span>
      </div>
      <div>
        <small>最大回撤</small>
        <span id="drawdown-${symbol}">--</span>
      </div>
    </div>
    <pre id="detail-${symbol}" class="code-block">暂无详细数据</pre>
    <footer class="actions">
      <button class="contrast" data-symbol="${symbol}">触发一次性下单</button>
    </footer>
  `;
  gridEl.appendChild(article);
  cards.set(symbol, article);
}

function renderCards() {
  state.symbols.forEach((symbol) => ensureCard(symbol));
  const groupedSignals = groupBySymbol(state.signals);
  const backtestMap = new Map(state.backtests.map((item) => [item.symbol, item]));
  const positionMap = new Map(state.positions.map((item) => [item.symbol, item]));

  cards.forEach((article, symbol) => {
    if (!state.symbols.includes(symbol)) {
      article.remove();
      cards.delete(symbol);
      return;
    }
    const statusEl = article.querySelector(`#signal-${symbol}`);
    const detailEl = article.querySelector(`#detail-${symbol}`);
    const priceEl = article.querySelector(`#price-${symbol}`);
    const changeEl = article.querySelector(`#change-${symbol}`);
    const latestTimeEl = article.querySelector(`#latest-time-${symbol}`);
    const positionEl = article.querySelector(`#position-${symbol}`);
    const returnEl = article.querySelector(`#return-${symbol}`);
    const drawdownEl = article.querySelector(`#drawdown-${symbol}`);

    const symbolSignals = groupedSignals.get(symbol) || [];
    const latestSignal = summariseSignals(symbolSignals);

    if (latestSignal) {
      statusEl.textContent = `${latestSignal.action} ${(latestSignal.confidence * 100).toFixed(1)}%`;
      statusEl.className = `badge ${
        latestSignal.action === 'LONG' ? 'success' : latestSignal.action === 'SHORT' ? 'danger' : ''
      }`;
    } else {
      statusEl.textContent = '暂无信号';
      statusEl.className = 'badge';
    }

    const candles = state.candles.get(symbol) || [];
    const latestCandle = candles[candles.length - 1];
    const compareCandle = candles[candles.length - 25];
    if (latestCandle) {
      priceEl.textContent = latestCandle.close.toFixed(2);
      latestTimeEl.textContent = new Date(latestCandle.time * 1000).toLocaleString();
      if (compareCandle) {
        const diff = latestCandle.close - compareCandle.close;
        const pct = (diff / compareCandle.close) * 100;
        changeEl.textContent = `${diff >= 0 ? '+' : ''}${pct.toFixed(2)}%`;
        changeEl.className = `change ${diff >= 0 ? 'up' : 'down'}`;
      } else {
        changeEl.textContent = '--';
        changeEl.className = 'change muted';
      }
    }

    const position = positionMap.get(symbol);
    if (position) {
      positionEl.textContent = `${position.side || 'FLAT'} ${Number(position.contracts || 0).toFixed(2)} 手`;
    } else {
      positionEl.textContent = '无持仓';
    }

    const backtest = backtestMap.get(symbol);
    if (backtest) {
      returnEl.textContent = `${(backtest.cumulativeReturn * 100).toFixed(2)}%`;
      drawdownEl.textContent = `${(backtest.maxDrawdown * 100).toFixed(2)}%`;
    } else {
      returnEl.textContent = '--';
      drawdownEl.textContent = '--';
    }

    const lines = [];
    if (latestSignal) {
      lines.push(`综合得分: ${latestSignal.score.toFixed(4)} (${symbolSignals.length} 模型)`);
    }
    if (position) {
      lines.push(`入场价格: ${Number(position.entryPrice || 0).toFixed(2)}`);
      lines.push(`未实现盈亏: ${Number(position.unrealizedPnl || 0).toFixed(4)}`);
    }
    if (backtest) {
      lines.push(`Sharpe: ${backtest.sharpeRatio.toFixed(3)} | 交易: ${backtest.trades}`);
    }
    detailEl.textContent = lines.join('\n') || '暂无详细数据';
  });
}

function renderOverview() {
  const totalSymbols = state.symbols.length;
  const totalSignals = state.signals.length;
  const totalPnl = state.positions.reduce((acc, pos) => acc + Number(pos.unrealizedPnl || 0), 0);
  const grossExposure = state.positions.reduce((acc, pos) => acc + Math.abs(Number(pos.contracts || 0)), 0);
  const bestSharpe = [...state.backtests].sort((a, b) => b.sharpeRatio - a.sharpeRatio)[0];

  overviewEl.innerHTML = `
    <article class="stat-card">
      <span class="label">跟踪合约</span>
      <strong>${totalSymbols}</strong>
      <span class="detail">${state.symbols.join(' · ') || '暂无数据'}</span>
    </article>
    <article class="stat-card">
      <span class="label">累计未实现盈亏</span>
      <strong>${totalPnl >= 0 ? '+' : ''}${totalPnl.toFixed(4)}</strong>
      <span class="detail">总手数 ${grossExposure.toFixed(2)}</span>
    </article>
    <article class="stat-card">
      <span class="label">策略信号</span>
      <strong>${totalSignals}</strong>
      <span class="detail">SSE + REST 累积</span>
    </article>
    <article class="stat-card">
      <span class="label">最优 Sharpe</span>
      <strong>${bestSharpe ? bestSharpe.sharpeRatio.toFixed(2) : '--'}</strong>
      <span class="detail">${bestSharpe ? `${bestSharpe.symbol} · ${(bestSharpe.cumulativeReturn * 100).toFixed(2)}%` : '等待回测'}</span>
    </article>
  `;
}

function renderPositions() {
  if (!positionsBody) return;
  positionsBody.innerHTML = state.positions
    .map((pos) => {
      const confidence = findConfidence(pos.symbol);
      return `
        <tr>
          <td>${pos.symbol}</td>
          <td>${pos.side}</td>
          <td>${Number(pos.contracts || 0).toFixed(3)}</td>
          <td>${Number(pos.entryPrice || 0).toFixed(2)}</td>
          <td class="${pos.unrealizedPnl >= 0 ? 'success' : 'danger'}">${Number(pos.unrealizedPnl || 0).toFixed(4)}</td>
          <td>${confidence ? `${(confidence * 100).toFixed(1)}%` : '--'}</td>
        </tr>
      `;
    })
    .join('');
}

function renderSignalsTable() {
  if (!signalsTable) return;
  const sorted = [...state.signals].sort((a, b) => Math.abs(b.score) - Math.abs(a.score)).slice(0, 30);
  signalsTable.innerHTML = sorted
    .map((signal) => `
      <tr>
        <td>${formatTime(signal.generatedAt)}</td>
        <td>${signal.symbol}</td>
        <td>${signal.model}</td>
        <td class="${signal.score >= 0 ? 'success' : 'danger'}">${signal.score.toFixed(4)}</td>
        <td>${signal.weight.toFixed(2)}</td>
      </tr>
    `)
    .join('');
}

function renderBacktestTable() {
  if (!backtestTable) return;
  const sorted = [...state.backtests].sort((a, b) => b.cumulativeReturn - a.cumulativeReturn).slice(0, 20);
  backtestTable.innerHTML = sorted
    .map((backtest) => `
      <tr>
        <td>${backtest.symbol}</td>
        <td>${(backtest.cumulativeReturn * 100).toFixed(2)}%</td>
        <td>${(backtest.maxDrawdown * 100).toFixed(2)}%</td>
        <td>${backtest.sharpeRatio.toFixed(2)}</td>
        <td>${backtest.trades}</td>
      </tr>
    `)
    .join('');
}

function applySelectedSymbol() {
  if (!chart || !state.selectedSymbol) return;
  chartSymbolEl.textContent = state.selectedSymbol;
  const candles = state.candles.get(state.selectedSymbol) || [];
  const candleData = candles.map((candle) => ({
    time: candle.time,
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close
  }));
  candleSeries.setData(candleData);
  volumeSeries.setData(candles.map((candle) => ({ time: candle.time, value: candle.volume })));
  if (candles.length) {
    chart.timeScale().fitContent();
  }
  updateChartMetrics(candles);
}

function updateChartMetrics(candles) {
  if (!candles.length) {
    ma20El.textContent = '--';
    ma50El.textContent = '--';
    volatilityEl.textContent = '--';
    volumeEl.textContent = '--';
    return;
  }
  const ma20 = movingAverage(candles, 20);
  const ma50 = movingAverage(candles, 50);
  const vol = volatility(candles, 30);
  const vol24 = candles.slice(-24).reduce((acc, candle) => acc + (candle.volume || 0), 0);

  ma20El.textContent = ma20 ? ma20.toFixed(2) : '--';
  ma50El.textContent = ma50 ? ma50.toFixed(2) : '--';
  volatilityEl.textContent = vol ? `${(vol * 100).toFixed(2)}%` : '--';
  volumeEl.textContent = vol24 ? vol24.toFixed(0) : '--';
}

function movingAverage(candles, period) {
  if (candles.length < period) return null;
  const slice = candles.slice(-period);
  const sum = slice.reduce((acc, candle) => acc + candle.close, 0);
  return sum / period;
}

function volatility(candles, window = 30) {
  if (candles.length < 2) return null;
  const slice = candles.slice(-window);
  if (slice.length < 2) return null;
  const returns = [];
  for (let i = 1; i < slice.length; i += 1) {
    const prev = slice[i - 1].close;
    const curr = slice[i].close;
    if (prev > 0 && curr > 0) {
      returns.push(Math.log(curr / prev));
    }
  }
  if (!returns.length) return null;
  const mean = returns.reduce((acc, value) => acc + value, 0) / returns.length;
  const variance = returns.reduce((acc, value) => acc + Math.pow(value - mean, 2), 0) / returns.length;
  return Math.sqrt(variance) * Math.sqrt(365);
}

function groupBySymbol(items) {
  const map = new Map();
  items.forEach((item) => {
    const list = map.get(item.symbol) || [];
    list.push(item);
    map.set(item.symbol, list);
  });
  return map;
}

function summariseSignals(signals) {
  if (!signals.length) return null;
  let sum = 0;
  let weighted = 0;
  signals.forEach((signal) => {
    const weight = 1 + Number(signal.weight || 0);
    sum += Number(signal.score || 0) * weight;
    weighted += weight;
  });
  const mean = weighted ? sum / weighted : 0;
  const confidence = Math.min(1, Math.abs(mean));
  let action = 'FLAT';
  if (mean > 0.12) action = 'LONG';
  if (mean < -0.12) action = 'SHORT';
  return { score: mean, confidence, action };
}

function findConfidence(symbol) {
  const signals = state.signals.filter((sig) => sig.symbol === symbol);
  const summary = summariseSignals(signals);
  return summary?.confidence;
}

function formatTime(iso) {
  if (!iso) return '--';
  return new Date(iso).toLocaleTimeString();
}

function wireActions() {
  gridEl.addEventListener('click', async (event) => {
    const button = event.target.closest('button[data-symbol]');
    if (!button) return;
    const symbol = button.dataset.symbol;
    button.disabled = true;
    const original = button.textContent;
    button.textContent = '执行中...';
    try {
      const res = await fetch(`${API_BASE}/trade?symbol=${encodeURIComponent(symbol)}`, { method: 'POST' });
      if (!res.ok) {
        throw new Error(`接口返回 ${res.status}`);
      }
      const payload = await res.json();
      alert(`${symbol} 执行结果: ${payload.action} × ${payload.positionSize}`);
    } catch (error) {
      alert(`下单失败: ${error.message}`);
    } finally {
      button.disabled = false;
      button.textContent = original;
    }
  });

  symbolSelect?.addEventListener('change', (event) => {
    state.selectedSymbol = event.target.value;
    applySelectedSymbol();
  });
}

function subscribeStream() {
  const source = new EventSource(STREAM_URL);
  source.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      updateState({
        signals: data.signals || [],
        positions: data.positions || [],
        backtests: data.backtests || []
      });
    } catch (error) {
      console.warn('解析 SSE 数据失败', error);
    }
  };
  source.onerror = () => {
    console.warn('SSE 连接出现问题，浏览器会自动重连');
  };
}

async function pollCandles() {
  try {
    const response = await fetch(`${API_BASE}/candles`);
    if (!response.ok) return;
    const data = await response.json();
    updateCandles(data);
  } catch (error) {
    console.warn('刷新 K 线失败', error);
  }
}

initChart();
await loadInitial();
wireActions();
subscribeStream();
setInterval(pollCandles, 20000);
