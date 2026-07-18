const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell,
  WidthType, AlignmentType, ShadingType, BorderStyle, PageBreak, convertInchesToTwip,
} = require('docx');
const fs = require('fs');

const NAVY    = '0D1F35';
const BLUE    = '3B82F6';
const GREEN   = '10B981';
const GREY_BG = 'F1F5F9';
const MID     = '64748B';
const BLACK   = '1E293B';
const WHITE   = 'FFFFFF';
const AMBER   = 'D97706';
const TEAL    = '0891B2';

function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 320, after: 120 },
    children: [new TextRun({ text, bold: true, size: 30, color: NAVY, font: 'Calibri' })],
  });
}
function h2(text, color = BLUE) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 240, after: 80 },
    children: [new TextRun({ text, bold: true, size: 24, color, font: 'Calibri' })],
  });
}
function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 160, after: 60 },
    children: [new TextRun({ text, bold: true, size: 21, color: BLACK, font: 'Calibri' })],
  });
}
function body(runs) {
  if (typeof runs === 'string') runs = [{ text: runs }];
  return new Paragraph({
    spacing: { before: 60, after: 80 },
    children: runs.map(r => new TextRun({ size: 21, color: BLACK, font: 'Calibri', ...r })),
  });
}
function bullet(text, boldPrefix = '') {
  return new Paragraph({
    spacing: { before: 60, after: 60 },
    indent: { left: convertInchesToTwip(0.35), hanging: convertInchesToTwip(0.2) },
    children: [
      new TextRun({ text: '• ', size: 21, font: 'Calibri', color: BLUE }),
      ...(boldPrefix ? [new TextRun({ text: boldPrefix + '  ', bold: true, size: 21, font: 'Calibri', color: BLACK })] : []),
      new TextRun({ text, size: 21, font: 'Calibri', color: BLACK }),
    ],
  });
}
function spacer() {
  return new Paragraph({ spacing: { before: 80, after: 80 }, children: [new TextRun('')] });
}
function divider() {
  return new Paragraph({
    spacing: { before: 120, after: 120 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: 'CBD5E1' } },
    children: [new TextRun('')],
  });
}
function pageBreakPara() {
  return new Paragraph({ children: [new PageBreak()] });
}
function callout(text, color = BLUE, bg = 'EFF6FF') {
  return new Paragraph({
    spacing: { before: 120, after: 120 },
    indent: { left: convertInchesToTwip(0.25), right: convertInchesToTwip(0.25) },
    border: {
      left: { style: BorderStyle.SINGLE, size: 16, color },
    },
    shading: { type: ShadingType.CLEAR, fill: bg },
    children: [new TextRun({ text, size: 20, color: BLACK, font: 'Calibri', italics: true })],
  });
}

function makeTable(headers, rows, colWidths) {
  const headerRow = new TableRow({
    tableHeader: true,
    children: headers.map((h, i) => new TableCell({
      width: { size: colWidths[i], type: WidthType.DXA },
      shading: { type: ShadingType.CLEAR, fill: NAVY },
      children: [new Paragraph({
        spacing: { before: 80, after: 80 },
        children: [new TextRun({ text: h, bold: true, size: 19, color: WHITE, font: 'Calibri' })],
      })],
    })),
  });
  const dataRows = rows.map((row, ri) =>
    new TableRow({
      children: row.map((cell, ci) => new TableCell({
        width: { size: colWidths[ci], type: WidthType.DXA },
        shading: { type: ShadingType.CLEAR, fill: ri % 2 === 0 ? WHITE : GREY_BG },
        children: [new Paragraph({
          spacing: { before: 80, after: 80 },
          children: [new TextRun({ text: String(cell), size: 19, font: 'Calibri', color: BLACK })],
        })],
      })),
    })
  );
  return new Table({
    width: { size: colWidths.reduce((a, b) => a + b, 0), type: WidthType.DXA },
    columnWidths: colWidths,
    rows: [headerRow, ...dataRows],
  });
}

// ── Cover ──────────────────────────────────────────────────────────────────────
function coverPage() {
  return [
    new Paragraph({ spacing: { before: 1200 }, children: [new TextRun('')] }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 80 },
      children: [new TextRun({ text: 'BLUE OCEAN HUB', bold: true, size: 56, color: NAVY, font: 'Calibri' })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 160 },
      children: [new TextRun({ text: 'AI Forex Trading Platform', size: 38, color: BLUE, font: 'Calibri' })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 80 },
      children: [new TextRun({ text: 'Platform Overview', bold: true, size: 28, color: MID, font: 'Calibri' })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 80 },
      children: [new TextRun({ text: 'For Traders & Investors', size: 24, color: MID, font: 'Calibri', italics: true })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 80 },
      children: [new TextRun({ text: 'July 2026', size: 22, color: MID, font: 'Calibri' })],
    }),
    spacer(),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 80, after: 0 },
      children: [new TextRun({ text: 'Let the AI trade. You watch, learn, and grow.', size: 24, color: GREEN, font: 'Calibri', italics: true })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 120, after: 0 },
      children: [new TextRun({ text: 'Developed by Harvest Technologies', size: 18, color: MID, font: 'Calibri', italics: true })],
    }),
    pageBreakPara(),
  ];
}

const doc = new Document({
  numbering: { config: [] },
  sections: [{
    properties: {
      page: { margin: { top: 1080, right: 1080, bottom: 1080, left: 1080 } },
    },
    children: [
      ...coverPage(),

      // ── 1. WHAT IS THIS ──
      h1('1. What Is Blue Ocean Hub?'),
      body('Blue Ocean Hub is an AI-powered forex trading platform that monitors the foreign exchange market around the clock and automatically executes trades on your behalf.'),
      spacer(),
      body('You do not need to watch charts, read indicators, or make split-second decisions. The system does that for you — using a combination of time-tested trading signals and a machine learning model trained on months of historical market data.'),
      spacer(),
      callout('Think of it as a disciplined trading assistant that never sleeps, never panics, and always follows the rules you set.', BLUE, 'EFF6FF'),
      divider(),

      // ── 2. HOW IT WORKS ──
      h1('2. How It Works'),
      body('The platform runs three things behind the scenes, all working together automatically:'),
      spacer(),
      h2('Step 1 — Watching the Market', GREEN),
      body('Every minute, the system pulls the latest price data for four major currency pairs directly from MetaTrader 5 (the industry-standard trading platform):'),
      bullet('EUR/USD (Euro vs US Dollar)'),
      bullet('GBP/USD (British Pound vs US Dollar)'),
      bullet('USD/JPY (US Dollar vs Japanese Yen)'),
      bullet('AUD/USD (Australian Dollar vs US Dollar)'),
      spacer(),
      h2('Step 2 — Analysing the Signal', GREEN),
      body('The signal engine runs two checks before deciding to trade:'),
      spacer(),
      bullet('Six technical indicators (RSI, MACD, EMA crossover, Bollinger Bands, ATR, OBV) are calculated. All six must agree on a direction (BUY or SELL) before proceeding.', 'Technical check —'),
      bullet('A machine learning model (trained on months of historical data) estimates the probability that this trade will be profitable. If the confidence is below a set threshold (e.g. 60%), the trade is skipped.', 'AI check —'),
      spacer(),
      callout('Only when BOTH checks agree does the system place a trade. This two-gate approach filters out weak or uncertain signals.', GREEN, 'F0FDF4'),
      spacer(),
      h2('Step 3 — Placing the Trade', GREEN),
      body('If both gates agree, the system opens a trade automatically with your configured stop loss, take profit, and lot size. It then monitors the trade and closes it when the target or stop loss is hit — or when you manually close it from the dashboard.'),
      divider(),

      // ── 3. DASHBOARD ──
      pageBreakPara(),
      h1('3. Your Dashboard'),
      body('Log in to the web dashboard from any browser. Everything you need to monitor the bot is in one place:'),
      spacer(),
      makeTable(
        ['What You See', 'What It Tells You'],
        [
          ['Account Balance & Equity', 'Your current MT5 demo (or live) account value, updated in real time'],
          ['Open Trades', 'How many positions the bot currently has open'],
          ['Total P&L', 'Profit or loss across all closed trades to date'],
          ['Signal Feed', 'Every signal the bot analyses — BUY, SELL, or HOLD — with confidence score'],
          ['Open Positions Table', 'Live view of all current trades: symbol, direction, open price, current P&L'],
          ['Trade History Table', 'All closed trades with outcome, profit/loss, and duration'],
          ['Bot Status Badge', 'Green = bot running · Red = bot stopped · Grey = market closed'],
        ],
        [3200, 5800]
      ),
      spacer(),
      body([
        { text: 'Everything updates live ', bold: true },
        { text: '— no need to refresh the page. The moment a new signal arrives or a trade opens/closes, your screen updates automatically.' },
      ]),
      divider(),

      // ── 4. ANALYTICS ──
      h1('4. Analytics Page'),
      body('The Analytics page gives you a deeper view of how the bot is performing over time:'),
      spacer(),
      bullet('Equity Curve — a running total of your cumulative profit or loss, day by day. An upward slope means the bot is growing your account.'),
      bullet('Win Rate by Day — how many of the day\'s closed trades were profitable. Green bars = days above 50% win rate.'),
      bullet('Confidence vs Outcome — a scatter chart showing whether higher-confidence signals actually produce better results. Useful for tuning the confidence threshold.'),
      bullet('Summary Stats — total trades, overall win rate (%), total P&L, average win size, and average loss size.'),
      spacer(),
      callout('These charts are empty until the bot has closed its first trades. After Sunday\'s market open, data will start appearing automatically.', AMBER, 'FFFBEB'),
      divider(),

      // ── 5. EMAIL NOTIFICATIONS ──
      h1('5. Email Notifications'),
      body('You stay informed without needing to watch the dashboard. The platform sends automatic emails for key events:'),
      spacer(),
      makeTable(
        ['Email', 'When It Fires', 'What It Contains'],
        [
          ['Trade Opened', 'Every time the bot opens a trade', 'Symbol, direction (BUY/SELL), lot size, stop loss, take profit, open price, ML confidence'],
          ['Trade Closed', 'Every time a trade is closed', 'WIN or LOSS outcome, profit/loss amount, duration, open and close prices'],
          ['Weekly Review', 'Every Friday at 8:00 PM (SAST)', 'Past week\'s signals, total trades, win rate, best/worst pair, total P&L'],
          ['Invite', 'When an admin invites a new user', 'Secure link to set up name and password (expires in 72 hours)'],
          ['Password Reset', 'When you request a reset', 'Secure link to set a new password'],
        ],
        [2000, 2200, 4800]
      ),
      divider(),

      // ── 6. RISK MANAGEMENT ──
      h1('6. Risk Management'),
      body('You are always in control of how much the bot risks on each trade. Settings can be adjusted from the Bot Settings page at any time.'),
      spacer(),
      h2('Global Settings'),
      bullet('Stop Loss (pips) — how far the market can move against you before the trade closes automatically'),
      bullet('Take Profit (pips) — how far the market must move in your favour before the trade closes with a win'),
      bullet('Lot Size — how large each trade is (0.01 = micro lot, the smallest available)'),
      bullet('Max Open Trades — the bot will never open more than this number of trades at once'),
      bullet('Min ML Confidence — signals below this threshold are ignored (default: 60%)'),
      spacer(),
      h2('Per-Symbol Overrides'),
      body('Each currency pair can have its own independent stop loss, take profit, and lot size. For example, USD/JPY can have a tighter stop loss than EUR/USD because of how it typically moves. You can also disable individual pairs entirely.'),
      spacer(),
      callout('During the paper trading phase, no real money changes hands. The bot simulates all trades using your configured settings so you can validate performance risk-free.', TEAL, 'F0F9FF'),
      divider(),

      // ── 7. PAPER vs LIVE ──
      pageBreakPara(),
      h1('7. Paper Trading vs Live Trading'),
      makeTable(
        ['', 'Paper Trading (Current)', 'Live Trading (Phase 6)'],
        [
          ['Real money at risk?', 'No — simulated only', 'Yes — real MT5 account'],
          ['Trades execute on MT5?', 'Yes — on demo account', 'Yes — on live account'],
          ['Results visible in dashboard?', 'Yes', 'Yes'],
          ['Email notifications?', 'Yes', 'Yes'],
          ['Purpose', 'Validate strategy, tune settings', 'Generate real returns'],
          ['How to switch', 'Toggle in Bot Settings', 'Set Paper Mode = OFF after ≥100 paper trades'],
        ],
        [2800, 2800, 3400]
      ),
      spacer(),
      callout('Do not switch to live trading until the bot has completed at least 100 paper trades with a consistent win rate above 50%. The platform enforces no minimum — this is a judgment call for you as the trader.', 'EF4444', 'FEF2F2'),
      divider(),

      // ── 8. MARKET HOURS ──
      h1('8. Market Hours'),
      body('The forex market is open 24 hours a day, five days a week. It closes Friday evening and reopens Sunday evening:'),
      spacer(),
      makeTable(
        ['Event', 'Time (UTC)', 'Time (SAST)'],
        [
          ['Market closes (Friday)', 'Friday 17:00', 'Friday 19:00'],
          ['Market reopens (Sunday)', 'Sunday 17:00', 'Sunday 19:00'],
          ['Bot auto-disables', 'Friday 17:00 ET (~22:00 UTC)', 'Saturday ~00:00'],
          ['Bot auto-enables', 'Sunday 17:00 ET (~22:00 UTC)', 'Monday ~00:00'],
        ],
        [3000, 2500, 3500]
      ),
      spacer(),
      body('You do not need to start or stop the bot manually. The platform detects market open and close times and enables/disables the bot automatically. Your dashboard will show a "Market Closed" badge on weekends.'),
      divider(),

      // ── 9. USER ROLES ──
      h1('9. User Roles'),
      h2('Current Setup'),
      makeTable(
        ['Role', 'What They Can Do'],
        [
          ['Admin (Trader)', 'Full access — dashboard, bot control, settings, trade history, analytics, user management, all emails'],
          ['User (Observer)', 'Dashboard and analytics — can view signals, positions, and performance but cannot change settings'],
        ],
        [2000, 7000]
      ),
      spacer(),
      h2('Future Setup (Phase 5)'),
      makeTable(
        ['Role', 'What They Can Do'],
        [
          ['Admin', 'Platform operator — manages all users and system configuration'],
          ['Trader', 'Connects their own MT5 account — bot trades on their behalf, they receive all trade alerts'],
          ['Investor', 'Read-only view of a trader\'s performance — receives weekly review emails, no trade control'],
        ],
        [2000, 7000]
      ),
      divider(),

      // ── 10. ROADMAP ──
      h1('10. Roadmap'),
      body('Here is where the platform is today and where it is going:'),
      spacer(),
      h2('✅  Phase 1 & 3 — Foundation Complete'),
      body('The full trading pipeline is built and working: price data flowing from MT5, signals generated every minute, trades opening and closing automatically, real-time dashboard, emails firing, risk controls in place.'),
      spacer(),
      h2('🔄  Phase 2 — First Live Sunday Test (This Weekend)'),
      body('Markets reopen Sunday evening. The bot will attempt its first real paper trades on the live market. This is the critical validation step — does the AI actually pick signals when real prices are moving?'),
      spacer(),
      h2('📊  Phase 4 — Strategy Tuning'),
      body('After collecting at least 20 paper trades, the team will review signal quality, win rate, and indicator performance. Thresholds may be adjusted to improve signal frequency or accuracy before moving forward.'),
      spacer(),
      h2('☁️  Phase 5 — Cloud Launch + Multi-Trader (Q3–Q4 2026)'),
      body('The biggest upgrade. The platform moves to the cloud (Google Cloud Platform), the Windows dependency is removed, and multiple traders can connect their own MT5 accounts. Investor accounts launch here.'),
      spacer(),
      bullet('Cloud hosting — accessible from anywhere, no local machine required'),
      bullet('MetaAPI integration — connect any MT5 account from any broker'),
      bullet('Multi-trader support — each trader\'s bot is independent'),
      bullet('Investor accounts — read-only observers who track a trader\'s performance'),
      spacer(),
      h2('💰  Phase 6 — Live Trading (Target: 2026–2027)'),
      body('After a successful cloud deployment and satisfactory paper trading results, the platform opens for live trading. Real money, real accounts, real returns.'),
      spacer(),
      callout('Live trading will only be enabled once consistent profitability is demonstrated over at least 100 paper trades. Capital preservation comes first.', GREEN, 'F0FDF4'),
      divider(),

      // ── 11. FAQ ──
      h1('11. Frequently Asked Questions'),
      h3('Does the bot trade on weekends?'),
      body('No. The forex market is closed from Friday ~17:00 ET to Sunday ~17:00 ET. The bot auto-disables and auto-enables around those times.'),
      spacer(),
      h3('Can I override or close a trade manually?'),
      body('Yes. Any open trade in the dashboard has a Close button. You can close any position at any time, regardless of whether it is in profit or loss.'),
      spacer(),
      h3('How do I change the risk settings?'),
      body('Log in as an Admin and go to Bot Settings. You can change stop loss, take profit, lot size, confidence threshold, and per-pair settings from there. Changes take effect on the next trade.'),
      spacer(),
      h3('How do I know if the bot is actually running?'),
      body('The nav bar shows a green "Bot Running" badge when the bot is active, red "Bot Stopped" when it is paused, and grey "Market Closed" on weekends. You will also see signals appearing in the signal feed every ~60 seconds.'),
      spacer(),
      h3('What happens if my internet goes down?'),
      body('The platform is running on your local machine during Phase 1–4. If your machine or internet goes offline, the bot pauses. In Phase 5, the system moves to the cloud and runs 24/7 regardless of your local connection.'),
      spacer(),
      h3('Is my money safe during paper trading?'),
      body('Paper trading uses a demo account — no real money is involved. The trades look and behave like real trades, but no funds change hands. This phase exists precisely so you can validate the strategy safely.'),
      spacer(),
      h3('How accurate is the AI?'),
      body('The XGBoost model was trained on ~7 months of hourly data. Accuracy on historical data ranges from 60% (GBP/USD) to 70% (USD/JPY). Historical accuracy does not guarantee future results — that is why the paper trading phase is essential before going live.'),
      divider(),

      // ── 12. CONTACT ──
      h1('12. Getting Started'),
      body('You have been invited to the platform as an early user. Here is what to expect:'),
      spacer(),
      bullet('You will receive an invite email — click the link to set your name and password.'),
      bullet('Log in at the platform URL shared with you.'),
      bullet('Your dashboard will show the bot status and any signals or trades from the current session.'),
      bullet('During paper trading, all trades are simulated — nothing is at risk.'),
      bullet('You will receive trade open and close emails automatically, plus a weekly review every Friday.'),
      spacer(),
      callout('For any questions, reach out to your platform admin. This is an early-access product — your feedback directly shapes what gets built next.', BLUE, 'EFF6FF'),
    ],
  }],
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync('output/overview.docx', buffer);
  console.log('Written: docs/output/overview.docx');
});
