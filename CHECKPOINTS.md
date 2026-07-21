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
- [ ] **CP-UI-25** Trade close email — fires to ADMIN users when a trade is closed (quick win — see pre-Sunday checklist)

### Admin & Settings
- [x] **CP-UI-26** Admin panel — user list, role changes, enable/disable, self-modification guard
- [x] **CP-UI-27** Bot settings page — paper mode toggle, scan interval, global SL/TP/volume/max trades, min ML confidence, symbols
- [x] **CP-UI-28** Per-symbol risk override — individual SL, TP, volume, enabled toggle per pair; live R:R display; `symbol_settings` table (V4 migration)
- [x] **CP-UI-29** `SignalPollerService` respects per-symbol `enabled` flag — disabled symbols are skipped entirely

### Public Pages & Misc
- [x] **CP-UI-30** Public landing page — hero, features, how it works, signal preview, CTA sections
- [x] **CP-UI-31** Route split — `/` public landing, `/dashboard` authenticated app
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

## Phase 4e — Pre-Market Readiness Check

**Goal:** Confirm the system is ready before each trading week.

**Goal:** Confirm the system is ready before markets reopen Sunday ~21:00 UTC.

- [x] **CP-PRE-01** All feature branches merged to `main` (CP-UI-36 to CP-UI-42 all done)
- [ ] **CP-PRE-02** Trade close email added (CP-UI-25) — confirms CP-18 in your inbox
- [x] **CP-PRE-03** MySQL running, Flyway V1–V6 migrations applied
- [x] **CP-PRE-04** MT5 bridge healthy: `GET http://localhost:8001/health` → `connected:true`
- [x] **CP-PRE-05** Signal engine healthy: `GET http://localhost:8002/health`
- [x] **CP-PRE-06** Backend running, dashboard loads, balance shows $100,000 USD
- [x] **CP-PRE-07** Bot enabled via dashboard — confirm green "Bot Running" badge in nav
- [x] **CP-PRE-08** SSE connected — confirm green dot in dashboard header, no 30s toast
- [x] **CP-PRE-09** Backend logs show `[EURUSD] Signal:` entries every ~60 seconds (scan running)
- [x] **CP-PRE-10** Per-symbol overrides visible in Bot Settings — all 4 pairs shown with SL/TP/volume

---

## Phase 5 — MetaAPI Rewrite + GCP Cloud Deployment (UAT)

**Goal:** Replace the Windows-only MT5 bridge with MetaAPI, deploy everything to GCP, and run UAT on cloud with paper trading + demo account. This phase is now the immediate priority — local dev is no longer viable for consistent testing.

**Why MetaAPI first:** The `MetaTrader5` Python package is a Windows-only DLL. It cannot run on Linux/Docker/GCP. MetaAPI provides a cloud REST API for MT5 — it is the only path to cloud deployment.

**UAT scope:** Paper trading stays ON. Demo account stays. The goal is a stable, always-on cloud environment where signals fire, trades open/close, and we accumulate enough data to validate the strategy — without depending on a local Windows machine.

### Step 1 — MetaAPI Setup
- [ ] **CP-30** MetaAPI account created at https://metaapi.cloud (free tier — 1 account, sufficient for UAT)
- [ ] **CP-31** MT5 demo account connected to MetaAPI dashboard — status: Connected
- [ ] **CP-32** MetaAPI account ID and API token added to `.env` (never committed)

### Step 2 — mt5-bridge Rewrite
- [ ] **CP-33** `mt5_client.py` rewritten — MetaAPI REST replaces `MetaTrader5` Python package
- [ ] **CP-34** `feed.py` rewritten — candles and tick data via MetaAPI endpoints
- [ ] **CP-35** `executor.py` rewritten — order open/close via MetaAPI
- [ ] **CP-36** `config.py` updated — `METAAPI_ACCOUNT_ID`, `METAAPI_TOKEN` replace MT5 path/login/password
- [ ] **CP-37** mt5-bridge tested locally via MetaAPI (no local MT5 terminal required)
- [ ] **CP-38** Signal engine still produces signals end-to-end via MetaAPI bridge

### Step 3 — GCP Infrastructure
- [ ] **CP-39** GCP project created, billing enabled, required APIs enabled (Cloud Run, Cloud SQL, Artifact Registry, Secret Manager)
- [ ] **CP-40** Cloud SQL MySQL 8 instance provisioned — `forexbot` database created
- [ ] **CP-41** All secrets stored in Secret Manager (`DB_PASSWORD`, `METAAPI_TOKEN`, `MAIL_PASSWORD`, etc.)
- [ ] **CP-42** Dockerfiles verified for all 3 services (mt5-bridge, signal-engine, backend)
- [ ] **CP-43** Docker images built and pushed to GCP Artifact Registry

### Step 4 — Cloud Run Deployment
- [ ] **CP-44** `mt5-bridge` deployed to Cloud Run — health check green, MetaAPI connected
- [ ] **CP-45** `signal-engine` deployed to Cloud Run — health check green, candles flowing
- [ ] **CP-46** `backend` deployed to Cloud Run — Flyway migrations applied, dashboard loads
- [ ] **CP-47** All 3 services communicating — dashboard shows balance from MetaAPI demo account
- [ ] **CP-48** Bot enabled on GCP — signals firing every scan cycle in Cloud Run logs

### Step 5 — UAT Validation
- [ ] **CP-49** First BUY or SELL signal produced on cloud (CP-16 equivalent)
- [ ] **CP-50** First paper trade opened and visible on cloud dashboard (CP-17 equivalent)
- [ ] **CP-51** 48-hour unattended run — no crashes, no missed scans, trades opening/closing
- [ ] **CP-52** 20+ paper trades accumulated — win rate calculated
- [ ] **CP-53** Weekly review email received from cloud environment (proves scheduler running)

### Step 6 — Multi-Trader (Post-UAT, Phase 5b)
> Only start this after CP-53 is complete and win rate is satisfactory.
- [ ] **CP-54** Per-user `metaapi_account_id` added to `users` table (new migration)
- [ ] **CP-55** MT5 connect flow in Account Settings — user enters their own MetaAPI account ID
- [ ] **CP-56** Trade execution, balance, and trade history scoped per user
- [ ] **CP-57** TRADER role added (has MT5 + trade alerts) vs INVESTOR (read-only)

---

## Phase 6 — Production (Live Trading)

> ⚠️ Only proceed here after CP-52 is complete and win rate is satisfactory over ≥ 20 paper trades on cloud. Do not rush this.

- [ ] **CP-58** Live MT5 account connected via MetaAPI
- [ ] **CP-59** `PAPER_TRADING=false` confirmed in logs
- [ ] **CP-60** First live trade opened (minimum lot 0.01)
- [ ] **CP-61** Risk limits verified — SL/TP firing correctly, max open trades respected
- [ ] **CP-62** GCP Cloud Monitoring alerts configured (service crash, trade error, low balance)
- [ ] **CP-63** Monthly retrain schedule established — `train_all.py` run against fresh candles

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

SIGNAL GATE THRESHOLDS (current):
  Technical gate — BUY:  EMA fast > slow, close > EMA200, RSI 30–65, MACD hist > 0
  Technical gate — SELL: EMA fast < slow, close < EMA200, RSI 35–70, MACD hist < 0
  AI gate — minimum confidence: 55% (MIN_CONFIDENCE = 0.55)
  Both gates must agree on direction for a trade to open.
  If technical = HOLD, AI gate is skipped entirely (gate optimisation).

FLYWAY MIGRATIONS:
  V1 — Initial schema (trades, signals, ohlcv, bot_config)
  V2 — Users table (email, role, enabled, OAuth2)
  V3 — Full name, invite token, password reset token
  V4 — Per-symbol settings (symbol_settings, seeds 4 pairs)
  V5 — Phone field on users (VARCHAR 20, SA format +27XXXXXXXXX)
  V6 — Email verification (email_verified, token, expiry) + account lockout (failed_attempts, locked_until)

BRAND: Blue Ocean Hub (renamed from Harvest Technologies — all UI and docs updated)

CP-UI-07: Google OAuth2 setup steps (optional — password login works standalone):
  1. Go to https://console.cloud.google.com → APIs & Services → Credentials
  2. Create OAuth 2.0 Client ID (Web application)
  3. Add Authorized redirect URI: http://localhost:8080/login/oauth2/code/google
  4. Set env vars: GOOGLE_CLIENT_ID=... and GOOGLE_CLIENT_SECRET=...
  5. Add to .env (never commit credentials)

KNOWN LIMITATION — scan_interval_sec UI field:
  The scan interval setting in Bot Settings writes to the DB but does NOT affect the
  running scheduler at runtime. The @Scheduled annotation reads bot.scan-interval-seconds
  from application.yml at startup only. Changing this field requires a restart to take
  effect. Fix planned for Phase 5 (dynamic ScheduledExecutorService).

ROLE MODEL (current vs future):
  Current: ADMIN (operator + trader) / USER (observer). Trade emails go to ADMIN only.
  Phase 5: ADMIN / TRADER (own MT5, get trade alerts) / INVESTOR (read-only, weekly review).
  The current ADMIN role effectively IS the trader in the single-account local setup.
```
