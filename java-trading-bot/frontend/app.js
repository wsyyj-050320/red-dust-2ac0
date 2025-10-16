const API_BASE = window.API_BASE || 'http://localhost:8090/api';
const STREAM_URL = window.STREAM_URL || 'http://localhost:8090/stream';
const overviewEl = document.querySelector('#overview');
const gridEl = document.querySelector('#contract-grid');
const cards = new Map();

async function loadInitial() {
  try {
    const [signalsRes, backtestRes, portfolioRes] = await Promise.all([
      fetch(`${API_BASE}/signals`),
      fetch(`${API_BASE}/backtest`),
      fetch(`${API_BASE}/portfolio`)
    ]);
    if (!signalsRes.ok || !backtestRes.ok || !portfolioRes.ok) {
      throw new Error('接口返回异常');
    }
    const [signals, backtests, portfolio] = await Promise.all([
      signalsRes.json(),
      backtestRes.json(),
      portfolioRes.json()
    ]);
    const symbols = collectSymbols(signals, portfolio);
    renderOverview(symbols);
    symbols.forEach((symbol) => ensureCard(symbol));
    updateFromPayload({ signals, positions: portfolio, backtests });
  } catch (error) {
    overviewEl.innerHTML = `<article class="danger">无法加载数据：${error.message}</article>`;
  }
}

function collectSymbols(signals, portfolio) {
  const set = new Set();
  signals.forEach((sig) => set.add(sig.symbol));
  portfolio.forEach((p) => set.add(p.symbol));
  return Array.from(set.values());
}

function renderOverview(symbols) {
  overviewEl.innerHTML = `
    <article>
      <header>当前监控合约</header>
      <p>${symbols.join(', ') || '暂无数据'}</p>
      <p class="muted">API 基址：${API_BASE}</p>
    </article>
  `;
}

function ensureCard(symbol) {
  if (cards.has(symbol)) return;
  const article = document.createElement('article');
  article.dataset.symbol = symbol;
  article.innerHTML = `
    <header class="status">
      <span>${symbol}</span>
      <small class="badge" id="signal-${symbol}">等待数据...</small>
    </header>
    <p id="summary-${symbol}" class="muted">AI 推理中...</p>
    <pre id="detail-${symbol}">回测结果加载中...</pre>
    <footer class="actions">
      <button class="contrast" data-symbol="${symbol}">触发一次性下单</button>
    </footer>
  `;
  gridEl.appendChild(article);
  cards.set(symbol, article);
}

function updateFromPayload({ signals = [], positions = [], backtests = [] }) {
  const groupedSignals = groupBySymbol(signals);
  const backtestMap = new Map(backtests.map((item) => [item.symbol, item]));
  const positionMap = new Map(positions.map((item) => [item.symbol, item]));

  cards.forEach((article, symbol) => {
    const statusEl = article.querySelector(`#signal-${symbol}`);
    const summaryEl = article.querySelector(`#summary-${symbol}`);
    const detailEl = article.querySelector(`#detail-${symbol}`);
    const symbolSignals = groupedSignals.get(symbol) || [];
    const latest = summariseSignals(symbolSignals);
    if (latest) {
      statusEl.textContent = `${latest.action} ${(latest.confidence * 100).toFixed(1)}%`;
      statusEl.className = `badge ${latest.action === 'LONG' ? 'success' : latest.action === 'SHORT' ? 'danger' : ''}`;
      summaryEl.textContent = `平均得分 ${latest.score.toFixed(4)} · 模型数 ${symbolSignals.length}`;
    } else {
      statusEl.textContent = '暂无信号';
      statusEl.className = 'badge';
      summaryEl.textContent = '等待新的行情数据...';
    }

    const position = positionMap.get(symbol);
    const backtest = backtestMap.get(symbol);
    const lines = [];
    if (position) {
      lines.push(`仓位: ${position.side} ${Number(position.contracts).toFixed(2)} 手 @ ${Number(position.entryPrice).toFixed(2)}`);
      lines.push(`未实现盈亏: ${Number(position.unrealizedPnl).toFixed(4)}`);
    }
    if (backtest) {
      lines.push(`回测收益: ${(backtest.cumulativeReturn * 100).toFixed(2)}%`);
      lines.push(`最大回撤: ${(backtest.maxDrawdown * 100).toFixed(2)}%`);
      lines.push(`Sharpe: ${backtest.sharpeRatio.toFixed(3)} | 交易次数: ${backtest.trades}`);
    }
    detailEl.textContent = lines.join('\n') || '暂无详细数据';
  });
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

function wireActions() {
  gridEl.addEventListener('click', async (event) => {
    const button = event.target.closest('button[data-symbol]');
    if (!button) return;
    const symbol = button.dataset.symbol;
    button.disabled = true;
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
      button.textContent = '触发一次性下单';
    }
  });
}

function subscribeStream() {
  const source = new EventSource(STREAM_URL);
  source.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      updateFromPayload({
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

await loadInitial();
wireActions();
subscribeStream();
