# Forex AI Bot — Development Checkpoints

Track your progress through each phase. Check off items as you complete them.

---

## Phase 1 — Local Development Setup

**Goal:** Everything running on your Windows machine, connected to an MT5 demo account.

- [x] **CP-01** Repository initialised / project folder open in IntelliJ
- [x] **CP-02** `.env` file created with real MT5 demo credentials (MetaQuotes-Demo, account 109814567)
- [x] **CP-03** MySQL running locally (native install — Docker skipped on 8GB RAM dev machine)
- [x] **CP-04** Flyway migrations applied — tables exist in `forexbot` DB
- [x] **CP-05** `mt5-bridge` starts without error (`python mt5-bridge/src/main.py`)
- [x] **CP-06** MT5 bridge health check passes: `GET http://localhost:8001/health` → `{"status":"ok","connected":true}`
- [x] **CP-07** Account info visible: `GET http://localhost:8001/account` returns balance $100,000 USD
- [x] **CP-08** Candles flowing: `GET http://localhost:8001/candles/EURUSD` returns 500 rows
- [x] **CP-09** `signal-engine` starts without error (`python signal-engine/src/main.py`)
- [x] **CP-10** Signal engine health check passes: `GET http://localhost:8002/health`
- [x] **CP-11** Spring Boot backend starts and dashboard loads at `http://localhost:8080`
- [x] **CP-12** Dashboard shows MT5 account balance (confirms full connectivity chain)

---

## Phase 2 — First Signal & Trade (Paper)

**Goal:** The bot produces signals and opens paper trades end-to-end.

- [x] **CP-13** Signal scan works: `GET http://localhost:8002/signal/EURUSD` returns a result
- [x] **CP-14** Bot enabled via dashboard — scan loop starts (confirmed in backend logs)
- [x] **CP-15** Signals being fetched every 60 seconds for all 4 symbols (HOLD — awaiting trend)
- [ ] **CP-16** First BUY or SELL signal produced — markets reopen Sunday ~21:00 UTC
- [ ] **CP-17** First paper trade opened and visible in dashboard open positions
- [ ] **CP-18** Paper trade manually closed via dashboard Close button
- [ ] **CP-19** Closed trade appears in trade history with status CLOSED

---

## Phase 3 — ML Model Training

**Goal:** XGBoost model trained and influencing signals.

- [x] **CP-20** ML training endpoint called for all symbols via `python train_all.py`
- [x] **CP-21** Training completed — model files saved to `signal-engine/models/`
- [x] **CP-22** Post-training signals include ML confidence (e.g. EURUSD ml_confidence=0.44)
- [x] **CP-23** All four symbols trained — accuracy: EURUSD 63%, GBPUSD 60%, USDJPY 70%, AUDUSD 63%
- [ ] **CP-24** At least one hybrid signal (both gates agree) produced and acted on

---

## Phase 4 — Strategy Tuning

**Goal:** Strategy parameters reviewed and adjusted based on initial signal quality.

- [ ] **CP-25** Review signal log — measure BUY/SELL vs HOLD ratio (aim for 10–20% actionable)
- [ ] **CP-26** Tune RSI thresholds / EMA periods in `signal-engine/src/config.py` if needed
- [ ] **CP-27** Tune ML confidence threshold (`MIN_CONFIDENCE` in `hybrid.py`) if too many/few signals
- [ ] **CP-28** At least 20 paper trades recorded for basic statistical review
- [ ] **CP-29** Win rate ≥ 50% on paper trades (prerequisite for UAT consideration)

---

## Phase 4b — UI, SaaS Polish & Real-time Features

**Goal:** Production-quality dashboard, emails, live updates, and per-symbol risk control.

### Auth & Design System
- [x] **CP-UI-01** Harvest Technologies design system (`static/css/app.css`) — Outfit font, navy/blue/green palette, glass cards, animations
- [x] **CP-UI-02** Login page — glass card, orbs, Lucide icons, Google OAuth + username/password
- [x] **CP-UI-03** Register page — full name + username grid, live password strength indicators, Google OAuth
- [x] **CP-UI-04** Forgot password page — email field, anti-enumeration success message
- [x] **CP-UI-05** Reset password page — token via URL, live strength indicators, error states
- [x] **CP-UI-06** Role-aware post-login redirect — admin → `/admin/users`, user → `/dashboard`
- [ ] **CP-UI-07** Google OAuth2 credentials wired up (see Notes — optional, standalone login works)

### Dashboard & Navigation
- [x] **CP-UI-08** Dashboard revamped — glass stat cards, topnav with role-aware nav links, proper badge system
- [x] **CP-UI-09** Mobile slide-in drawer — all app pages (dashboard, admin, bot settings, account) + landing page
- [x] **CP-UI-10** Scrollable tables — `max-height` + sticky `thead` on all table containers; page no longer scrolls for tables
- [x] **CP-UI-11** Market auto-detect — `MarketHoursService` drives nav badge (Bot Running / Market Closed / Bot Stopped)
- [x] **CP-UI-12** Global model advice (`GlobalModelAdvice`) — `botEnabled` / `marketOpen` injected into every template automatically

### Real-time Updates (SSE)
- [x] **CP-UI-13** `SseService` — `CopyOnWriteArrayList` of `SseEmitter`s, heartbeat on connect, dead-emitter cleanup
- [x] **CP-UI-14** `SseController` — `GET /api/dashboard/stream` (text/event-stream, auth-protected)
- [x] **CP-UI-15** `LiveDataController` — `GET /api/live/signals`, `/positions`, `/stats` (JSON endpoints for frontend)
- [x] **CP-UI-16** `SignalPollerService` broadcasts `signal` and `trade` SSE events after each poll cycle
- [x] **CP-UI-17** Dashboard `EventSource` JS — auto-reconnect, live signal table, live positions table, live stat cards
- [x] **CP-UI-18** 30-second hard reload replaced with soft toast — only appears if SSE is not connected

### Email Notifications
- [x] **CP-UI-19** `EmailService` — Brevo SMTP relay in prod; dev mode logs to console (zero SMTP config needed locally)
- [x] **CP-UI-20** Invite flow — admin sends email+role only; user sets name+password via 72-hour token link
- [x] **CP-UI-21** Password reset flow — forgot-password → email → reset page → login (completed manually)
- [x] **CP-UI-22** Branded email templates — invite, password reset, trade notification, weekly review
- [x] **CP-UI-23** Trade open email — fires to ADMIN users only on every BUY/SELL (fire-and-forget, never blocks trade)
- [x] **CP-UI-24** Weekly review email — `WeeklyEmailScheduler` fires every Friday 18:00 UTC; includes signals, trades, P&L, best pair
- [x] **CP-UI-25** Trade close email — fires to ADMIN users when a trade is closed (already wired in `TradeService.closeTrade()`)

### Admin & Settings
- [x] **CP-UI-26** Admin panel — user list, role changes, enable/disable, self-modification guard
- [x] **CP-UI-27** Bot settings page — paper mode toggle, scan interval, global SL/TP/volume/max trades, min ML confidence, symbols
- [x] **CP-UI-28** Per-symbol risk override — individual SL, TP, volume, enabled toggle per pair; live R:R display; `symbol_settings` table (V4 migration)
- [x] **CP-UI-29** `SignalPollerService` respects per-symbol `enabled` flag — disabled symbols are skipped entirely

### Public Pages & Misc
- [x] **CP-UI-30** Public landing page — hero, features, how it works, signal preview, CTA sections
- [x] **CP-UI-31** Route split — `/` redirects to `/login` (unauthenticated) or `/dashboard` (authenticated)
- [x] **CP-UI-32** Custom error pages — branded 404, 403, 500
- [x] **CP-UI-33** Favicon — ⚡ SVG icon across all pages
- [x] **CP-UI-34** Auto-dismiss flash alerts — fade out after 4 seconds
- [x] **CP-UI-35** Account settings page — update name, change password

### Branch hygiene
- [x] **CP-UI-36** `feature/ui-polish` merged to `main` via PR
- [x] **CP-UI-37** `feature/admin-panel` merged to `main` via PR
- [x] **CP-UI-38** `feature/public-pages` merged to `main` via PR
- [x] **CP-UI-39** `feature/enhancements` merged to `main` via PR
- [x] **CP-UI-40** `feature/ux-improvements` merged to `main` via PR
- [x] **CP-UI-41** `feature/security-hardening` merged to `main` via PR
- [x] **CP-UI-42** `feature/signal-fixes` merged to `main` via PR

---

## Phase 4c — Security Hardening

**Goal:** Brute-force protection, email verification, persistent account lockout, and input validation.

- [x] **CP-SEC-01** Login rate limiting — `LoginRateLimitFilter` blocks IPs after 5 failed attempts for 15 minutes
- [x] **CP-SEC-02** Email verification on registration — UUID token, 24h expiry, resend flow
- [x] **CP-SEC-03** Persistent account lockout — `failed_login_attempts` + `locked_until` in DB (V6 migration)
- [x] **CP-SEC-04** `@ValidEmailDomain` — DNS MX lookup with Google/Cloudflare fallback, fail-open, configurable via `EMAIL_DOMAIN_VALIDATION` env var
- [x] **CP-SEC-05** SA phone number validation — `@Pattern(regexp = "^(\\+27[0-9]{9})?$")` on register + profile forms
- [x] **CP-SEC-06** Strong password validation — `@Pattern` (8+ chars, upper, lower, digit, special) on all password fields
- [x] **CP-SEC-07** Cross-field password match — class-level `@PasswordsMatch` annotation using `BeanWrapperImpl`
- [x] **CP-SEC-08** Custom auth failure handler — routes to `?locked`, `?unverified`, or `?error` based on exception type
- [x] **CP-SEC-09** Flyway V6 — `email_verified`, `email_verification_token`, `email_verification_exp`, `failed_login_attempts`, `locked_until`
- [x] **CP-SEC-10** Phone field added to `users` table (Flyway V5) and account settings UI

---

## Phase 4d — Signal Engine Tuning

**Goal:** Fix gates that were blocking valid signals; expose AI confidence properly; UX improvements.

- [x] **CP-SIG-01** RSI buy cap loosened: `< 60` → `< 65` (was blocking USDJPY/AUDUSD at London/NY overlap)
- [x] **CP-SIG-02** RSI sell floor loosened: `> 40` → `> 35` (symmetric with buy side)
- [x] **CP-SIG-03** MACD gate loosened: sign-only (`hist > 0`), dropped slope requirement (`hist > prev_hist`)
- [x] **CP-SIG-04** Gate optimisation: skip AI (XGBoost) entirely if technical gate = HOLD — saves CPU on HOLD cycles
- [x] **CP-SIG-05** H1 candle TTL cache (55 min) in signal-engine — eliminates redundant HTTP calls to MT5 bridge
- [x] **CP-SIG-06** `mlConfidence` exposed in `/api/live/signals` — dashboard confidence column now shows AI confidence, not the always-zero signal confidence
- [x] **CP-SIG-07** User-friendly reason strings — plain English, confidence shown as % (e.g. `AI confidence too low to trade (52% — minimum 55% required)`)
- [x] **CP-SIG-08** ML → AI rename across UI column headers and all reason strings
- [x] **CP-SIG-09** Table search added — client-side filter on signals, positions, trade history, and admin users tables
- [x] **CP-SIG-10** Auto-reconnect on MT5 IPC pipe failure (`-10001`) in `feed.py` — retries once after `try_reconnect()`
- [x] **CP-SIG-11** MT5 503 errors downgraded from ERROR to WARN in `DashboardController` and `SignalPollerService`
- [ ] **CP-SIG-12** First hybrid signal confirmed — both technical and AI gates agree, trade opened (CP-16/17)

---

## Phase 4f — Strategy V2 + UX Improvements

**Goal:** Replace the original EMA/MACD strategy with a more robust indicator set, expand to 9 symbols, improve ML labeling, and complete UX polish for production.

### Signal Engine — Strategy V2
- [x] **CP-V2-01** Indicator overhaul — replaced EMA/MACD with SMA(5/30/62/100/200), ADX(14), RSI(9), ATR(14), Bollinger(20), OBV, price action
- [x] **CP-V2-02** DXY filter — fetches USD Index candles; filters USD-pair signals against dollar trend
- [x] **CP-V2-03** Symbol expansion — 4 → 9 symbols: EURUSD, GBPUSD, AUDUSD, XAUUSD, EURJPY, AUDJPY, US100, US500, US30
- [x] **CP-V2-04** US session gate — US100/US500/US30 only scanned 13:30–20:00 UTC Mon–Fri (15:30–22:00 SAST)
- [x] **CP-V2-05** V8 migration — removed USDJPY, seeded 9 V2 symbols in `symbol_settings`
- [x] **CP-V2-06** `application.yml` default symbols updated to full 9-symbol list
- [x] **CP-V2-07** `LiveDataController` symbol order updated (EURUSD … US30)

### ML Improvements
- [x] **CP-V2-08** Forward ATR-outcome labeling — replaces next-candle direction; simulates TP/SL hit within 20 H1 candles (R:R 1:3 → TP=4.5×ATR, SL=1.5×ATR)
- [x] **CP-V2-09** 3 new ML features — ATR percentile (rolling 50-period rank), VWAP deviation (20-candle), time-of-day sin/cos encoding (16 features total, up from 13)
- [x] **CP-V2-10** Models retrained on V2 — accuracy lifted from ~40–63% to 75%+ across all symbols

### Dashboard UX
- [x] **CP-V2-11** Market overview cards — replaced stale table with live indicator cards (trend, RSI, ADX, SMA cross, ML signal) via `/market-overview`
- [x] **CP-V2-12** Parallel market overview — `asyncio.gather` with 6s per-symbol timeout; ~1s load vs previous 15s+ sequential
- [x] **CP-V2-13** Silent market overview refresh — SSE-triggered refresh keeps existing cards visible, shows "Updating…" instead of spinner
- [x] **CP-V2-14** US session status badge on US100/US500/US30 cards — green "Scanning now · closes 22:00 SAST" or grey with next open time
- [x] **CP-V2-15** Friendly error cards — dashed border, Disabled/Unavailable badge, explains no-history vs genuine error
- [x] **CP-V2-16** Signal history filters — symbol dropdown, direction chips (All/BUY/SELL/HOLD), "Traded only" checkbox; all three apply with AND logic
- [x] **CP-V2-17** US100 auto-enable — weekly retrain probes disabled symbols; auto-enables when broker history reaches 400 samples

### Trailing Stop & Risk
- [x] **CP-V2-18** V9 migration — `atr` column added to `trades` table
- [x] **CP-V2-19** `TradeService.openTrade()` — now persists `slPrice`, `tpPrice`, `atr` on every trade row (were previously missing from builder)
- [x] **CP-V2-20** `TrailingStopService` — @Scheduled every 5 min; phase 1: break-even at 2×ATR profit; phase 2: trail at current − 1×ATR from 3×ATR profit onwards

### Signal Engine Health & Observability
- [x] **CP-V2-21** Signal engine health alerting — `SignalPollerService` pings `/health` every 2 min; alert email to all ADMIN users after 2 consecutive failures; recovery email on restore
- [x] **CP-V2-22** `EmailService.sendSystemAlert()` — generic inline-HTML alert method (no Thymeleaf template needed)
- [x] **CP-V2-23** `/price/{symbol}` endpoint on signal engine — returns last H1 close + ATR from candle cache; used by `TrailingStopService`
- [x] **CP-V2-24** Model accuracy in bot settings — signal engine stores stats after training; `/models/stats` endpoint; purple ML row in each per-symbol card showing accuracy %, sample count, and training date

---

## Phase 4e — Pre-Market Readiness Check

**Goal:** Confirm the system is ready before each trading week.

**Goal:** Confirm the system is ready before markets reopen Sunday ~21:00 UTC.

- [x] **CP-PRE-01** All feature branches merged to `main` (CP-UI-36 to CP-UI-42 all done)
- [x] **CP-PRE-02** Trade close email wired (CP-UI-25) — confirms CP-18 in your inbox
- [x] **CP-PRE-03** MySQL running, Flyway V1–V9 migrations applied
- [x] **CP-PRE-04** MT5 bridge healthy: `GET http://localhost:8001/health` → `connected:true`
- [x] **CP-PRE-05** Signal engine healthy: `GET http://localhost:8002/health`
- [x] **CP-PRE-06** Backend running, dashboard loads, balance shows $100,000 USD
- [x] **CP-PRE-07** Bot enabled via dashboard — confirm green "Bot Running" badge in nav
- [x] **CP-PRE-08** SSE connected — confirm green dot in dashboard header, no 30s toast
- [x] **CP-PRE-09** Backend logs show `[EURUSD] Signal:` entries every ~60 seconds (scan running)
- [x] **CP-PRE-10** Per-symbol overrides visible in Bot Settings — all 4 pairs shown with SL/TP/volume

---

## Phase 5 — MetaAPI Rewrite + GCP Cloud Deployment (UAT)

**Goal:** Replace the Windows-only MT5 bridge with MetaAPI, deploy everything to GCP, and run UAT on cloud with paper trading + demo account.

**UAT scope:** Paper trading ON. Demo account. Always-on cloud environment — no dependency on a local Windows machine. URL: https://blue-ocean-hub.com

### Step 1 — MetaAPI Setup
- [x] **CP-30** MetaAPI account created at https://metaapi.cloud (Cloud-G2, London region)
- [x] **CP-31** MT5 demo account 109814567 connected to MetaAPI — status: Connected
- [x] **CP-32** MetaAPI account ID and API token added to `.env` (never committed)

### Step 2 — mt5-bridge Rewrite
- [x] **CP-33** `mt5_client.py` rewritten — MetaAPI SDK replaces `MetaTrader5` Python package
- [x] **CP-34** `feed.py` rewritten — candles via `account.get_historical_candles()`, ticks via `conn.get_symbol_price()`
- [x] **CP-35** `executor.py` rewritten — orders via `conn.create_market_buy/sell_order()`, close via `conn.close_position()`
- [x] **CP-36** `config.py` updated — `MT5_BRIDGE_METAAPI_TOKEN`, `MT5_BRIDGE_METAAPI_ACCOUNT_ID` via pydantic-settings
- [x] **CP-37** mt5-bridge tested via MetaAPI — balance $100,000 USD confirmed in logs
- [x] **CP-38** Signal engine producing signals end-to-end via MetaAPI bridge

### Step 3 — GCP Infrastructure
- [x] **CP-39** GCP e2-medium VM (2 vCPU, 4GB RAM) created — Ubuntu/Debian 22, us-central1 region
- [x] **CP-40** Docker + Docker Compose installed on VM (Debian repo, not Ubuntu)
- [x] **CP-41** Nginx installed and configured as reverse proxy (port 80 → Spring Boot 8080)
- [x] **CP-42** Cloudflare Tunnel (`cloudflared`) installed as systemd service — no firewall rules needed
- [x] **CP-43** All secrets in `.env` on VM only — never committed; `.gitignore`d

### Step 4 — Docker Compose Deployment
- [x] **CP-44** `mt5-bridge` container running — MetaAPI connected, candles flowing
- [x] **CP-45** `signal-engine` container running — signals producing every 60 seconds
- [x] **CP-46** `backend` container running — Flyway V1–V6 applied, dashboard loads
- [x] **CP-47** `mysql` container running — persistent volume, not exposed externally
- [x] **CP-48** All 4 services healthy — `docker compose ps` all green
- [x] **CP-49** Dashboard accessible at https://blue-ocean-hub.com
- [x] **CP-50** ML models trained on GCP — all 4 symbols via `POST /train/{symbol}` (~59% accuracy, 801 samples)
- [x] **CP-51** Management scripts added — `deploy.sh`, `restart.sh`, `status.sh`, `logs.sh`
- [x] **CP-52** Cloudflared as systemd service — survives VM reboots; Docker `restart: unless-stopped` for containers

### Step 5 — UAT Validation
- [ ] **CP-53** First BUY or SELL signal produced on cloud — both gates agree
- [ ] **CP-54** First paper trade opened and visible on cloud dashboard
- [ ] **CP-55** 48-hour unattended run — no crashes, no missed scans
- [ ] **CP-56** 20+ paper trades accumulated — win rate calculated
- [ ] **CP-57** Weekly review email received from cloud (proves scheduler running)

### Step 6 — Multi-Trader (Post-UAT, Phase 5b)
> Only start this after CP-57 is complete and win rate is satisfactory.
> Currently all users share the single MetaAPI demo account — acceptable for UAT/preview.
- [ ] **CP-58** Per-user `metaapi_account_id` added to `users` table (new migration)
- [ ] **CP-59** MT5 connect flow in Account Settings — user enters their own MetaAPI account ID
- [ ] **CP-60** Trade execution, balance, and trade history scoped per user
- [ ] **CP-61** TRADER role added (own MT5 + trade alerts) vs INVESTOR (read-only)

---

## Phase 6 — Production (Live Trading)

> ⚠️ Only proceed here after CP-56 is complete and win rate is satisfactory over ≥ 20 paper trades on cloud. Do not rush this.

- [ ] **CP-62** Live MT5 account connected via MetaAPI
- [ ] **CP-63** `PAPER_TRADING=false` confirmed in logs
- [ ] **CP-64** First live trade opened (minimum lot 0.01)
- [ ] **CP-65** Risk limits verified — SL/TP firing correctly, max open trades respected
- [ ] **CP-66** GCP Cloud Monitoring alerts configured (service crash, trade error, low balance)
- [ ] **CP-67** Monthly retrain schedule established — `POST /train/{symbol}` against fresh candles

---

## Notes

```
CP-03: MySQL installed natively on Windows (not Docker) — 8GB RAM machine.
       Dev profile uses forexbot/forexbot123. DB user created with mysql_native_password plugin.

CP-05: MT5 path: C:\Program Files\MetaTrader 5\terminal64.exe (non-standard install).
       MT5 desktop app must be open and logged in before starting mt5-bridge.

CP-07: MetaQuotes-Demo account 109814567 — balance $100,000 USD (paper).

CP-13: Debug endpoint available: GET http://localhost:8002/debug/{symbol}
       Shows raw indicator values and both gate decisions. Useful for diagnosing HOLD reasons.

CP-15: H1 signals all HOLD over weekend (expected — forex markets closed Sat/Sun).
       Markets reopen Sunday ~21:00 UTC. First actionable signals expected during
       London session Monday 08:00–12:00 UTC.

CP-23: Models trained on 4,801 H1 candles each (~7 months of data).
       USDJPY best accuracy at 70%. Retrain monthly or after significant market regime change.

SIGNAL GATE THRESHOLDS — Strategy V2 (current):
  Technical gate — BUY:  SMA5 > SMA30, SMA30 > SMA62, close > SMA200, ADX ≥ 20, RSI 30–65
  Technical gate — SELL: SMA5 < SMA30, SMA30 < SMA62, close < SMA200, ADX ≥ 20, RSI 35–70
  DXY filter — if DXY is trending strongly, filters out opposing USD pairs
  US session gate — US100/US500/US30 only scanned 13:30–20:00 UTC Mon–Fri
  AI gate — XGBoost predicts same direction with ≥ 55% confidence
  Both gates must agree on direction for a trade to open.
  If technical = HOLD, AI gate is skipped entirely (gate optimisation).

ML MODEL (V2):
  Features: 16 total (SMA momentum, ADX, RSI, BB position, ATR%, candle body,
            OBV change, ATR percentile, VWAP deviation, time-of-day sin/cos)
  Labeling: forward ATR-outcome — did TP (4.5×ATR) hit before SL (1.5×ATR) within 20 H1 bars?
  Accuracy: 75%+ across all 9 symbols after V2 retraining

TRAILING STOP LOGIC:
  Runs every 5 minutes via TrailingStopService.
  Phase 1 (profit ≥ 2×ATR): move SL to entry price (break-even)
  Phase 2 (profit ≥ 3×ATR): trail SL at (current − 1×ATR) for BUY, (current + 1×ATR) for SELL
  SL only moves in the favourable direction — never widens.
  Price data from signal engine /price/{symbol} (H1 candle cache).

FLYWAY MIGRATIONS:
  V1 — Initial schema (trades, signals, ohlcv, bot_config)
  V2 — Users table (email, role, enabled, OAuth2)
  V3 — Full name, invite token, password reset token
  V4 — Per-symbol settings (symbol_settings, seeds 4 pairs: EURUSD GBPUSD USDJPY AUDUSD)
  V5 — Phone field on users (VARCHAR 20, SA format +27XXXXXXXXX)
  V6 — Email verification (email_verified, token, expiry) + account lockout (failed_attempts, locked_until)
  V7 — sl_atr_mult / tp_atr_mult on symbol_settings (per-symbol ATR multipliers; default 1.5/4.5)
  V8 — Strategy V2 symbols: remove USDJPY, seed XAUUSD, EURJPY, AUDJPY, US100, US500, US30
  V9 — atr column on trades (used by TrailingStopService for break-even / trail calculations)

BRAND: Blue Ocean Hub (renamed from Harvest Technologies — all UI and docs updated)

GCP UAT ENVIRONMENT:
  URL:      https://blue-ocean-hub.com
  VM:       GCP e2-medium, us-central1, Debian — external IP 35.226.105.18
  Tunnel:   Cloudflare Tunnel (cloudflared systemd service) — no open inbound ports
  Nginx:    /etc/nginx/sites-available/blueocean → proxy to localhost:8080
  Compose:  ~/forex-ai-bot/docker-compose.yml — 4 services (mysql, mt5-bridge, signal-engine, backend)
  Scripts:  deploy.sh / restart.sh / status.sh / logs.sh [SERVICE]
  MetaAPI:  Cloud-G2, London region — account 109814567, balance $100,000 USD demo
  Models:   Trained on GCP — Strategy V2; 75%+ accuracy across 9 symbols (retrain monthly)
            Trigger: POST http://localhost:8002/train/{symbol} per symbol
            Symbols: EURUSD, GBPUSD, AUDUSD, XAUUSD, EURJPY, AUDJPY, US100, US500, US30
            US100/US500/US30 need ≥400 samples to auto-enable (broker accumulates history over time)
            Model stats visible in Bot Settings → per-symbol cards after training

DEFAULT ADMIN (seed on first startup — DataInitializer.java):
  Username: admin
  Password: Harvest2025!   ← CHANGE IMMEDIATELY after first login
  Note: emailVerified=true is set in code — no email confirmation needed for seeded admin

CP-UI-07: Google OAuth2 setup steps (optional — password login works standalone):
  1. Go to https://console.cloud.google.com → APIs & Services → Credentials
  2. Create OAuth 2.0 Client ID (Web application)
  3. Add Authorized redirect URI: https://blue-ocean-hub.com/login/oauth2/code/google
  4. Set env vars: GOOGLE_CLIENT_ID=... and GOOGLE_CLIENT_SECRET=... in .env on VM

KNOWN LIMITATION — scan_interval_sec UI field:
  The scan interval setting in Bot Settings writes to the DB but does NOT affect the
  running scheduler at runtime. The @Scheduled annotation reads bot.scan-interval-seconds
  from application.yml at startup only. Changing this field requires a restart to take
  effect. Fix planned for Phase 5b.

KNOWN LIMITATION — single shared MT5 account:
  All users currently share the same MetaAPI demo account (from .env).
  Per-user account linking is planned for Phase 5b (CP-58 to CP-61).
  For UAT/preview, only invite users you are comfortable sharing demo account data with.

KNOWN LIMITATION — signal timestamps:
  Dashboard timestamps display in UTC. SAST is UTC+2. If dashboard shows 17:02 and
  local time is 19:02, the display is correct UTC — not a bug, but a UX improvement
  to add SAST conversion (planned).

ROLE MODEL (current vs future):
  Current: ADMIN (operator + trader) / USER (observer). Trade emails go to ADMIN only.
  Phase 5b: ADMIN / TRADER (own MT5, get trade alerts) / INVESTOR (read-only, weekly review).
  The current ADMIN role effectively IS the trader in the single-account UAT setup.
```
