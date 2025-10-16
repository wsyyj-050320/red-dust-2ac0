# AI 合约量化交易系统（离线友好版）

该项目提供一套无需外部依赖即可编译的 Java 17 量化交易样例，涵盖 Binance 合约行情采集、轻量级 AI 信号融合、仓位风控、回测
与前端可视化接口。为解决受限网络环境无法访问 Maven Central 的问题，代码全部基于 JDK 标准库实现，`mvn package` 可以在离线模
式下直接成功。

## 主要特性

- **零外部依赖**：不引入 Spring、Jackson 等第三方库，所有 HTTP、SSE、JSON 处理均由 JDK 提供的类完成。
- **多模型 AI 组合**：内置 Logistic、LSTM、Transformer 风格的轻量模型，配合移动均线信号，通过自适应权重融合给出方向与置信度
  。
- **合约仓位与风控**：`RiskManager` 会限制杠杆、自动调整下单手数，并跟踪当前方向，保证离线/模拟环境也能获得真实的仓位状态。
- **回测 & 指标**：`Backtester` 通过走查历史窗口评估收益、回撤与简化后的 Sharpe 值，可在仪表盘中查看。
- **内嵌 HTTP 服务**：基于 `com.sun.net.httpserver.HttpServer` 暴露 REST 与 `/stream` SSE 接口，同时生成合成行情供无网络环境使用。
- **前端面板**：`frontend/` 目录包含解耦的 HTML/JS 单页应用，展示多币种行情、信号、回测及手动下单功能。

## 如何获取代码与离线压缩包

### 1. 克隆仓库

如果你已经安装 Git，可以直接克隆本仓库：

```bash
git clone <仓库地址>
cd red-dust-2ac0/java-trading-bot
```

仓库的根目录位于 `red-dust-2ac0/`，交易机器人项目位于其中的 `java-trading-bot/` 目录。

### 2. 直接下载 ZIP

若无法使用 Git，可在浏览器或命令行直接下载仓库压缩包：

- 浏览器方式：访问代码托管平台的仓库主页，点击 **Download ZIP**，即可获得最新源码压缩包。
- 命令行方式（示例使用 `wget`）：

  ```bash
  wget <仓库地址>/archive/refs/heads/main.zip -O ai-trading-bot-src.zip
  unzip ai-trading-bot-src.zip
  cd red-dust-2ac0-main/java-trading-bot
  ```

### 3. 生成离线成品包

出于拉取请求审核的需要，仓库本身不再提交任何二进制压缩包，以避免“禁止二进制文件”导致的合并失败。如果需要离线分发包，可在本地运行脚本自行生成：

```bash
cd java-trading-bot
./scripts/create-archive.sh
```

命令会在 `dist/` 目录生成形如 `ai-trading-bot-YYYYMMDDHHMMSS.zip` 的压缩包（包含 Jar、前端与脚本）。随后可以：

- 在本地直接解压运行；
- 上传到 Git 平台的 Release 资产中分享给他人；
- 或通过私有对象存储/网盘等方式分发。

若你只需要 Jar 文件，可在解压后的 `bin/` 目录找到 `ai-trading-bot.jar`，然后执行：

```bash
java -jar bin/ai-trading-bot.jar
```

## 快速上手

### 1. 编译

```bash
cd java-trading-bot
./mvnw -q -s settings.xml package
```

项目提供了 `mvnw` 与 `settings.xml`，在检测到 `package` 目标时会自动调用离线编译脚本，避免网络受限时 Maven 插件无法下载的
问题。生成的 `target/ai-trading-bot-0.3.0-SNAPSHOT.jar` 可直接运行。

也可以手动执行离线脚本：

```bash
./scripts/offline-package.sh
```

### 2. 启动后端

```bash
java -jar target/ai-trading-bot-0.3.0-SNAPSHOT.jar
```

服务默认监听 `8090` 端口。若无法访问 Binance，会自动生成可复现的合成行情，方便在封闭环境中联调。

常用 API：

- `GET /api/candles`：返回所有配置合约的最新 K 线（JSON 数组）。
- `GET /api/signals`：返回最新 AI 信号、置信度与模型权重。
- `GET /api/backtest`：输出累积收益、最大回撤、Sharpe 和交易次数。
- `GET /api/portfolio`：查看仓位方向、手数与估算盈亏。
- `POST /api/trade?symbol=BTCUSDT`：执行一次基于当前信号的下单；离线环境会打印模拟委托。
- `GET /stream`：SSE 实时推送信号和仓位，用于前端订阅。

环境变量可覆盖默认配置：

| 变量名 | 默认值 | 说明 |
| ------ | ------ | ---- |
| `BINANCE_API_KEY` | `demo-key` | Binance API Key |
| `BINANCE_API_SECRET` | `demo-secret` | Binance Secret |
| `BOT_SYMBOLS` | `BTCUSDT,ETHUSDT` | 监控的合约列表（逗号分隔） |
| `BOT_REFRESH_SECONDS` | `20` | 行情刷新间隔（秒） |
| `BOT_HISTORY_LIMIT` | `720` | 每个合约保留的 K 线数量 |

### 3. 启动前端仪表盘

```bash
cd java-trading-bot/frontend
python3 -m http.server 8081
```

浏览器访问 `http://localhost:8081`，即可连接后端 API（默认指向 `http://localhost:8090`），实时查看多币种行情与信号。

### 4. 打包分发

仓库提供 `scripts/create-archive.sh` 生成完整分发包（包含 Jar、前端与脚本）：

```bash
cd java-trading-bot
./scripts/create-archive.sh
```

命令会在 `dist/` 目录生成形如 `ai-trading-bot-YYYYMMDDHHMMSS.zip` 的压缩包（包含 Jar、前端与脚本）。
如需在仓库中保留构建结果，可将压缩包复制到 `releases/` 目录，但请勿将 zip 文件提交到版本库，可在发布 Release 时以附件方式上传。

## 目录结构

```
java-trading-bot/
├── pom.xml
├── README.md
├── frontend/                # 解耦的前端单页应用
├── scripts/                 # 构建与打包脚本
└── src/main/java/com/cryptobot
    ├── ai/                  # 特征提取与多模型信号
    ├── backtest/            # 回测实现
    ├── config/              # 运行配置
    ├── data/                # 行情抓取与本地存储
    ├── execution/           # 下单执行（含离线模拟）
    ├── model/               # 领域模型定义
    ├── risk/                # 仓位管理与风控
    ├── strategy/            # 合约策略逻辑
    └── web/                 # 内嵌 HTTP / SSE 服务
```

## 注意事项

- 示例代码侧重于教学演示，不包含签名、重试、数据库等生产级要素，上线前请务必补全。
- 合成行情仅用于离线调试，请勿据此做真实投资决策。
- 若要连接 Binance 实盘，请在安全环境中设置真实密钥，并遵守交易所 API 限速及风控要求。
