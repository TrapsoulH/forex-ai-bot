const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell,
  WidthType, AlignmentType, ShadingType, BorderStyle, PageBreak, NumberingLevel,
  convertInchesToTwip, UnderlineType, Header, Footer, PageNumber, TabStopPosition,
  TabStopType
} = require('docx');
const fs = require('fs');

// ── Colours ───────────────────────────────────────────────────────────────────
const NAVY     = '0D1F35';
const BLUE     = '3B82F6';
const GREEN    = '10B981';
const GREY_BG  = 'F1F5F9';
const MID_GREY = '64748B';
const BLACK    = '1E293B';
const WHITE    = 'FFFFFF';

// ── Helpers ───────────────────────────────────────────────────────────────────
function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 320, after: 120 },
    children: [new TextRun({ text, bold: true, size: 28, color: NAVY, font: 'Calibri' })],
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 240, after: 80 },
    children: [new TextRun({ text, bold: true, size: 22, color: BLUE, font: 'Calibri' })],
  });
}
function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 160, after: 60 },
    children: [new TextRun({ text, bold: true, size: 20, color: BLACK, font: 'Calibri' })],
  });
}
function body(text, opts = {}) {
  return new Paragraph({
    spacing: { before: 60, after: 60 },
    children: [new TextRun({ text, size: 20, color: BLACK, font: 'Calibri', ...opts })],
  });
}
function mono(text) {
  return new Paragraph({
    spacing: { before: 40, after: 40 },
    indent: { left: convertInchesToTwip(0.3) },
    children: [new TextRun({ text, size: 18, font: 'Courier New', color: '334155' })],
  });
}
function bullet(text, bold_prefix = '') {
  return new Paragraph({
    spacing: { before: 40, after: 40 },
    indent: { left: convertInchesToTwip(0.35), hanging: convertInchesToTwip(0.2) },
    children: [
      ...(bold_prefix ? [new TextRun({ text: bold_prefix + ' ', bold: true, size: 20, font: 'Calibri', color: BLACK })] : []),
      new TextRun({ text, size: 20, font: 'Calibri', color: BLACK }),
    ],
  });
}
function spacer() {
  return new Paragraph({ spacing: { before: 60, after: 60 }, children: [new TextRun('')] });
}
function divider() {
  return new Paragraph({
    spacing: { before: 120, after: 120 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: 'E2E8F0' } },
    children: [new TextRun('')],
  });
}
function pageBreakPara() {
  return new Paragraph({ children: [new PageBreak()] });
}

// ── Table helper ──────────────────────────────────────────────────────────────
function makeTable(headers, rows, colWidths) {
  const totalW = colWidths.reduce((a, b) => a + b, 0);
  const headerRow = new TableRow({
    tableHeader: true,
    children: headers.map((h, i) => new TableCell({
      width: { size: colWidths[i], type: WidthType.DXA },
      shading: { type: ShadingType.CLEAR, color: 'auto', fill: NAVY },
      children: [new Paragraph({
        spacing: { before: 60, after: 60 },
        children: [new TextRun({ text: h, bold: true, size: 18, color: WHITE, font: 'Calibri' })],
      })],
    })),
  });
  const dataRows = rows.map((row, ri) =>
    new TableRow({
      children: row.map((cell, ci) => new TableCell({
        width: { size: colWidths[ci], type: WidthType.DXA },
        shading: { type: ShadingType.CLEAR, color: 'auto', fill: ri % 2 === 0 ? WHITE : GREY_BG },
        children: [new Paragraph({
          spacing: { before: 60, after: 60 },
          children: [new TextRun({ text: String(cell), size: 18, font: 'Calibri', color: BLACK })],
        })],
      })),
    })
  );
  return new Table({
    width: { size: totalW, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: [headerRow, ...dataRows],
  });
}

// ── Cover page ────────────────────────────────────────────────────────────────
function coverPage() {
  return [
    new Paragraph({ spacing: { before: 1440 }, children: [new TextRun('')] }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 120 },
      children: [new TextRun({ text: 'BLUE OCEAN HUB', bold: true, size: 52, color: NAVY, font: 'Calibri' })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 240 },
      children: [new TextRun({ text: 'AI Forex Trading Platform', size: 36, color: BLUE, font: 'Calibri' })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 80 },
      children: [new TextRun({ text: 'Technical Reference Document', bold: true, size: 28, color: MID_GREY, font: 'Calibri' })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 60 },
      children: [new TextRun({ text: 'Version 1.0 · July 2026', size: 22, color: MID_GREY, font: 'Calibri' })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 60 },
      children: [new TextRun({ text: 'Confidential', size: 20, color: 'EF4444', font: 'Calibri', italics: true })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 120, after: 0 },
      children: [new TextRun({ text: 'Developed by Harvest Technologies', size: 18, color: MID_GREY, font: 'Calibri', italics: true })],
    }),
    pageBreakPara(),
  ];
}

// ── Document sections ─────────────────────────────────────────────────────────
const doc = new Document({
  numbering: { config: [] },
  styles: {
    paragraphStyles: [],
  },
  sections: [{
    properties: {
      page: {
        margin: { top: 1080, right: 1080, bottom: 1080, left: 1080 },
      },
    },
    children: [
      // ── COVER ──
      ...coverPage(),

      // ── 1. OVERVIEW ──
      h1('1. System Overview'),
      body('Blue Ocean Hub is an AI-powered forex trading SaaS. It monitors four major currency pairs in real time, generates hybrid technical + machine-learning signals, and autonomously opens and closes trades on a connected MetaTrader 5 account. The web dashboard provides live visibility, bot control, risk management, and performance analytics.'),
      spacer(),
      body('Current phase: local development on Windows with a MetaTrader 5 demo account. Phase 5 migrates to MetaAPI (cloud-native) and GCP Cloud Run.'),
      divider(),

      // ── 2. ARCHITECTURE ──
      h1('2. Architecture'),
      body('The system is composed of four independently running services:'),
      spacer(),
      makeTable(
        ['Service', 'Language', 'Port', 'Responsibility'],
        [
          ['mt5-bridge',     'Python 3.11', '8001', 'MetaTrader 5 connectivity — OHLCV price feed, order open/close'],
          ['signal-engine',  'Python 3.11', '8002', 'Hybrid signal generation — technical indicators + XGBoost ML'],
          ['backend',        'Java 21 / Spring Boot 3.3', '8080', 'SaaS web app — REST API, Thymeleaf dashboard, auth, email, SSE'],
          ['MySQL 8',        '–',           '3306', 'Persistence — Flyway-managed schema'],
        ],
        [2000, 2200, 800, 4000]
      ),
      spacer(),
      h2('2.1 Request Flow'),
      body('1. SignalPollerService (Spring) calls GET /signal/{symbol} on signal-engine every N seconds.'),
      body('2. signal-engine calls GET /candles/{symbol} on mt5-bridge, computes indicators, runs XGBoost, returns direction + confidence.'),
      body('3. If confidence ≥ threshold and signal is BUY/SELL, TradeService calls POST /trade/open on mt5-bridge.'),
      body('4. mt5-bridge sends the order to MetaTrader 5 and returns the MT5 ticket number.'),
      body('5. The trade record is saved to MySQL. SseService broadcasts an event to all connected dashboard tabs.'),
      body('6. Dashboard EventSource receives the event, fetches /api/live/stats and /api/live/positions, updates the UI without a page reload.'),
      divider(),

      // ── 3. TECH STACK ──
      h1('3. Tech Stack'),
      makeTable(
        ['Layer', 'Technology', 'Notes'],
        [
          ['MT5 connectivity (dev)', 'MetaTrader5 Python package', 'Windows only — must be replaced in cloud deploy'],
          ['MT5 connectivity (prod)', 'MetaAPI REST API', 'Cloud-native, Linux-compatible, free tier available'],
          ['Technical indicators', 'ta library (RSI, MACD, EMA, Bollinger, ATR, OBV)', 'pip package'],
          ['Machine learning', 'XGBoost + scikit-learn', 'Trained on H1 OHLCV candles'],
          ['Data wrangling', 'pandas + numpy', ''],
          ['Python HTTP server', 'FastAPI + uvicorn', 'Both mt5-bridge and signal-engine'],
          ['Backend framework', 'Spring Boot 3.3', 'Java 21, Maven'],
          ['UI templating', 'Thymeleaf', 'Server-side rendering + SSE live updates'],
          ['Real-time push', 'SseEmitter + EventSource', 'No WebSocket dependency'],
          ['Authentication', 'Spring Security + BCrypt + OAuth2', 'Google OAuth2 optional'],
          ['Email', 'Brevo SMTP via JavaMailSender', 'Console fallback in dev — no SMTP config needed locally'],
          ['DB migrations', 'Flyway', 'Versioned SQL files; Hibernate DDL auto disabled'],
          ['Database', 'MySQL 8', 'utf8mb4'],
          ['Containerisation', 'Docker + Docker Compose', 'MySQL only in dev (low-RAM); full stack for prod'],
          ['Cloud (Phase 5)', 'GCP Cloud Run + Cloud SQL', '~$15–30/month'],
        ],
        [2200, 2600, 2200]
      ),
      divider(),

      // ── 4. DATABASE SCHEMA ──
      pageBreakPara(),
      h1('4. Database Schema'),
      body('Schema is managed exclusively by Flyway. Never edit applied migrations — always create a new versioned file.'),
      spacer(),
      makeTable(
        ['Migration', 'Description'],
        [
          ['V1__init_schema.sql', 'Core tables: trades, signals, ohlcv, bot_config'],
          ['V2__users.sql', 'Users table — email, role, enabled, OAuth2 support'],
          ['V3__user_fullname_and_password_reset.sql', 'Full name, invite token, password reset token, expiry'],
          ['V4__symbol_settings.sql', 'Per-symbol SL/TP/volume/enabled — seeds EURUSD, GBPUSD, USDJPY, AUDUSD'],
        ],
        [2500, 6000]
      ),
      spacer(),
      h2('4.1 Key Tables'),
      h3('trades'),
      bullet('id, symbol, direction (BUY/SELL), status (OPEN/CLOSED)'),
      bullet('mt5_ticket — MT5 order ticket number'),
      bullet('open_price, close_price, profit, volume'),
      bullet('sl_pips, tp_pips — copied from symbol_settings at open time'),
      bullet('signal_confidence — ML confidence at time of signal'),
      bullet('paper_trade — boolean; TRUE = simulated, FALSE = live'),
      bullet('opened_at, closed_at — Instant (UTC stored, rendered in JVM timezone)'),
      spacer(),
      h3('symbol_settings'),
      bullet('symbol (UNIQUE), sl_pips, tp_pips, volume, enabled'),
      bullet('updated_at — auto-updated by JPA @PreUpdate'),
      bullet('getOrCreate() auto-seeds from BotProperties defaults if no row exists'),
      spacer(),
      h3('bot_config'),
      bullet('Key-value store for runtime settings (paper_mode, max_open_trades, min_ml_confidence, etc.)'),
      bullet('All values stored as VARCHAR — parsed by BotProperties.java at read time'),
      spacer(),
      h3('users'),
      bullet('username (UNIQUE), email (UNIQUE), password_hash'),
      bullet('role: ADMIN or USER'),
      bullet('enabled — admin can disable accounts'),
      bullet('invite_token, invite_token_expiry — 72-hour invite links'),
      bullet('reset_token, reset_token_expiry — password reset'),
      divider(),

      // ── 5. KEY SERVICES ──
      h1('5. Key Services & Classes'),
      makeTable(
        ['Class', 'Package', 'Purpose'],
        [
          ['SignalPollerService', 'service', '@Scheduled scan loop — polls signal-engine for all enabled symbols'],
          ['TradeService', 'service', 'openTrade() / closeTrade() — resolves per-symbol settings, calls mt5-bridge, fires email'],
          ['SymbolSettingsService', 'service', 'getOrCreate(symbol) — per-symbol risk; auto-seeds from BotProperties'],
          ['SseService', 'service', 'SseEmitter registry; broadcast(type, payload); broadcastStatus() for bot state changes'],
          ['MarketSchedulerService', 'service', 'Every 15 min — auto-disable bot on Friday 17:00 ET, auto-enable Sunday 17:00 ET'],
          ['MarketHoursService', 'service', 'isOpen() — computes current forex session state in ET timezone'],
          ['EmailService', 'service', 'sendTradeOpened(), sendTradeClosed(), sendWeeklyReview(), sendInvite(), sendPasswordReset()'],
          ['WeeklyEmailScheduler', 'service', '@Scheduled every Friday 18:00 UTC — triggers weekly review email to all users'],
          ['GlobalModelAdvice', 'controller', '@ModelAttribute — injects botEnabled + marketOpen into every Thymeleaf template'],
          ['AnalyticsController', 'controller', 'GET /analytics — computes equity curve, win rate trend, confidence scatter; serialises to JSON'],
          ['LiveDataController', 'controller', 'GET /api/live/signals|positions|stats — lightweight JSON endpoints for SSE-driven frontend'],
          ['BotProperties', 'config', '@ConfigurationProperties — global defaults for SL/TP/volume/symbols/confidence'],
        ],
        [2200, 1200, 5600]
      ),
      divider(),

      // ── 6. API ENDPOINTS ──
      pageBreakPara(),
      h1('6. Internal API Endpoints'),
      h2('6.1 mt5-bridge (port 8001)'),
      makeTable(
        ['Method', 'Path', 'Description'],
        [
          ['GET', '/health', '{"status":"ok","connected":true}'],
          ['GET', '/account', 'MT5 account info — balance, equity, currency'],
          ['GET', '/candles/{symbol}', '500 H1 OHLCV bars as JSON array'],
          ['POST', '/trade/open', 'Body: {symbol, direction, volume, sl_pips, tp_pips} — returns {ticket}'],
          ['POST', '/trade/close', 'Body: {ticket, symbol, direction, volume} — returns {profit}'],
          ['GET', '/positions', 'Currently open MT5 positions'],
        ],
        [800, 2200, 6000]
      ),
      spacer(),
      h2('6.2 signal-engine (port 8002)'),
      makeTable(
        ['Method', 'Path', 'Description'],
        [
          ['GET', '/health', '{"status":"ok"}'],
          ['GET', '/signal/{symbol}', '{"direction":"BUY|SELL|HOLD","ml_confidence":0.72,...}'],
          ['GET', '/debug/{symbol}', 'Raw indicator values + gate decisions — dev only'],
          ['POST', '/train/{symbol}', 'Triggers XGBoost model training for that symbol'],
        ],
        [800, 2200, 6000]
      ),
      spacer(),
      h2('6.3 Spring Boot backend (port 8080) — selected endpoints'),
      makeTable(
        ['Method', 'Path', 'Auth', 'Description'],
        [
          ['GET',  '/dashboard',              'Any user',  'Main trading dashboard'],
          ['GET',  '/analytics',              'Any user',  'Analytics page — charts'],
          ['GET',  '/api/live/stats',         'Any user',  'JSON: balance, equity, botEnabled, marketOpen, openTrades, totalPnl'],
          ['GET',  '/api/live/signals',       'Any user',  'JSON: recent 20 signals'],
          ['GET',  '/api/live/positions',     'Any user',  'JSON: current open positions'],
          ['GET',  '/api/dashboard/stream',   'Any user',  'SSE stream — event types: signal, trade, status, heartbeat'],
          ['POST', '/bot/enable',             'Any user',  'Enable signal poller'],
          ['POST', '/bot/disable',            'Any user',  'Disable signal poller'],
          ['POST', '/trade/{id}/close',       'Any user',  'Manually close a trade by DB id'],
          ['GET',  '/settings/bot',           'ADMIN',     'Bot settings page'],
          ['POST', '/settings/bot',           'ADMIN',     'Save global bot config'],
          ['POST', '/settings/bot/symbol',    'ADMIN',     'Save per-symbol SL/TP/volume/enabled'],
          ['GET',  '/admin/users',            'ADMIN',     'User management page'],
          ['POST', '/admin/users/{id}/role',  'ADMIN',     'Change user role'],
          ['POST', '/admin/invite',           'ADMIN',     'Send invite email'],
        ],
        [800, 2400, 1400, 4400]
      ),
      divider(),

      // ── 7. SIGNAL STRATEGY ──
      h1('7. Signal Strategy'),
      h2('7.1 Technical Gate'),
      body('The first gate uses traditional indicators computed over 500 H1 candles:'),
      bullet('RSI (14) — overbought/oversold thresholds (configurable in signal-engine/src/config.py)'),
      bullet('EMA crossover (20/50) — trend direction confirmation'),
      bullet('MACD histogram — momentum confirmation'),
      bullet('Bollinger Bands — volatility range confirmation'),
      bullet('ATR (14) — position sizing context'),
      bullet('OBV — volume confirmation'),
      body('All six indicators must agree on direction for the technical gate to pass (BUY or SELL). Otherwise: HOLD.'),
      spacer(),
      h2('7.2 ML Gate (XGBoost)'),
      body('The second gate is an XGBoost binary classifier trained on historical H1 candles. Features include the indicator values above plus lagged price returns. Output is a win probability (0–1). If probability ≥ MIN_CONFIDENCE (default 0.60), the ML gate passes.'),
      spacer(),
      body('A trade fires only when BOTH gates agree. This is the two-gate hybrid strategy.'),
      spacer(),
      makeTable(
        ['Symbol', 'Trained Accuracy', 'Training Data'],
        [
          ['EURUSD', '63%', '4,801 H1 candles (~7 months)'],
          ['GBPUSD', '60%', '4,801 H1 candles'],
          ['USDJPY', '70%', '4,801 H1 candles'],
          ['AUDUSD', '63%', '4,801 H1 candles'],
        ],
        [2000, 2000, 5000]
      ),
      divider(),

      // ── 8. ENV VARS ──
      pageBreakPara(),
      h1('8. Environment Variables (.env)'),
      body('All secrets live in .env (gitignored). application.yml references them via ${VAR} with no defaults. Never commit credentials.'),
      spacer(),
      makeTable(
        ['Variable', 'Used By', 'Description'],
        [
          ['MT5_BRIDGE_MT5_LOGIN',    'mt5-bridge', 'MT5 account number'],
          ['MT5_BRIDGE_MT5_PASSWORD', 'mt5-bridge', 'MT5 account password'],
          ['MT5_BRIDGE_MT5_SERVER',   'mt5-bridge', 'MT5 broker server name (e.g. MetaQuotes-Demo)'],
          ['DB_PASSWORD',             'backend',    'MySQL password for forexbot user'],
          ['JWT_SECRET',              'backend',    'Secret for Spring Security session tokens'],
          ['MAIL_USERNAME',           'backend',    'Brevo SMTP username (email address)'],
          ['MAIL_PASSWORD',           'backend',    'Brevo SMTP password (API key)'],
          ['MAIL_FROM',               'backend',    'From address for outbound email'],
          ['GOOGLE_CLIENT_ID',        'backend',    'Google OAuth2 client ID (optional)'],
          ['GOOGLE_CLIENT_SECRET',    'backend',    'Google OAuth2 client secret (optional)'],
        ],
        [2800, 1600, 4600]
      ),
      divider(),

      // ── 9. KNOWN LIMITATIONS ──
      h1('9. Known Limitations'),
      bullet('scan_interval_sec in Bot Settings UI writes to DB but does NOT change the running @Scheduled interval at runtime. A restart is required. Fix planned for Phase 5 (dynamic ScheduledExecutorService).', 'Scan interval —'),
      bullet('The MetaTrader5 Python package only runs on Windows. The mt5-bridge service cannot run on Linux or macOS. This is resolved in Phase 5 by replacing it with MetaAPI REST.', 'Windows-only bridge —'),
      bullet('All trades currently go to the single admin MT5 account. Multi-user trade execution is a Phase 5 feature.', 'Single-account —'),
      bullet('SSE emitter IOExceptions logged at WARN level are expected — they occur when a browser tab closes while the server tries to write. SseService handles cleanup automatically.', 'SSE IOException —'),
      divider(),

      // ── 10. ROADMAP ──
      h1('10. Development Roadmap'),
      h2('Phase 1 — Local Setup ✅'),
      body('MT5 bridge connected, signal engine running, MySQL and Flyway applied, Spring Boot dashboard live, balance confirmed ($100,000 demo).'),
      spacer(),
      h2('Phase 2 — First Signal & Paper Trade (in progress)'),
      body('Bot enabled. Awaiting first BUY/SELL signal at Sunday market open (~21:00 UTC). First paper trade open → close → history confirmation. Target: ≥20 paper trades, win rate ≥50%.'),
      spacer(),
      h2('Phase 3 — ML Training ✅'),
      body('XGBoost models trained on all four symbols. Accuracy: EURUSD 63%, GBPUSD 60%, USDJPY 70%, AUDUSD 63%.'),
      spacer(),
      h2('Phase 4 — Strategy Tuning'),
      body('Review signal log BUY/SELL vs HOLD ratio. Tune RSI thresholds, EMA periods, MIN_CONFIDENCE if needed. Collect ≥20 paper trades for statistical review.'),
      spacer(),
      h2('Phase 4b — UI & SaaS Polish ✅'),
      body('Real-time SSE dashboard, mobile responsive, email notifications (trade open/close, weekly review, invite, password reset), per-symbol risk management, analytics page, market-hours auto-detect, dynamic bot button state.'),
      spacer(),
      h2('Phase 5 — MetaAPI Rewrite + Multi-Trader SaaS'),
      body('Replace Windows-only mt5-bridge with MetaAPI REST API. Each trader connects their own MT5 account. Trade execution scoped per user. Add TRADER and INVESTOR roles. Deploy to GCP Cloud Run + Cloud SQL.'),
      spacer(),
      makeTable(
        ['Checkpoint', 'Description'],
        [
          ['CP-30', 'MetaAPI account created, MT5 demo connected'],
          ['CP-31–32', 'mt5-bridge rewritten to MetaAPI REST'],
          ['CP-33–34', 'Per-user metaapi_account_id in DB; MT5 connect flow in Account Settings'],
          ['CP-35–38', 'Trade execution, balance, emails all scoped per user'],
          ['CP-39', 'TRADER role (own MT5, trade alerts) and INVESTOR role (read-only, weekly report)'],
          ['CP-40–45', 'GCP containerisation, Cloud SQL, Cloud Run deploy, 48-hour unattended paper run'],
        ],
        [1200, 7800]
      ),
      spacer(),
      h2('Phase 6 — Production Live Trading'),
      body('Prerequisites: CP-45 complete + ≥100 paper trades + win rate satisfactory.'),
      bullet('Live MT5 account connected via MetaAPI'),
      bullet('PAPER_TRADING=false confirmed in logs'),
      bullet('Risk limits verified — SL/TP and max open trades enforced'),
      bullet('GCP Cloud Monitoring / alerting configured'),
      bullet('Monthly ML model retrain process established'),
      divider(),

      // ── 11. DEV SETUP ──
      h1('11. Development Setup (Quick Reference)'),
      h2('Prerequisites'),
      bullet('Python 3.11+, Java 21+, Maven, MySQL 8 (native Windows install)'),
      bullet('MetaTrader 5 desktop app installed and logged into demo account'),
      bullet('IntelliJ IDEA for backend; PyCharm for Python services'),
      spacer(),
      h2('Startup Order'),
      mono('1. Start MySQL (Windows Services or net start MySQL80)'),
      mono('2. Open MetaTrader 5 desktop app'),
      mono('3. cd mt5-bridge && venv\\Scripts\\activate && python src/main.py'),
      mono('4. cd signal-engine && venv\\Scripts\\activate && python src/main.py'),
      mono('5. cd backend && ./mvnw spring-boot:run'),
      mono('6. Navigate to http://localhost:8080'),
      spacer(),
      h2('ML Model Training (first run / monthly)'),
      mono('python train_all.py   # signal-engine must be running'),
      spacer(),
      h2('Git Workflow'),
      bullet('Feature branches: feature/<name>'),
      bullet('Never commit .env — it is gitignored'),
      bullet('Never commit credentials in application.yml — use ${ENV_VAR} references'),
      bullet('If .git/HEAD.lock exists on Windows: del .git\\HEAD.lock before git operations'),
    ],
  }],
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync('output/tech-ref.docx', buffer);
  console.log('Written: docs/output/tech-ref.docx');
});
