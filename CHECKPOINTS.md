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
- [ ] **CP-16** First BUY or SELL signal produced — waiting for H1 trend to form (markets open Sunday evening)
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

## Phase 5 — UAT (MetaApi + GCP)

**Goal:** System running 24/7 on GCP with MetaApi replacing the Windows MT5 bridge.

- [ ] **CP-30** MetaApi account created at https://metaapi.cloud (free tier)
- [ ] **CP-31** MT5 demo account connected to MetaApi
- [ ] **CP-32** `mt5-bridge` rewritten to use MetaApi REST API (replaces `MetaTrader5` Python package)
- [ ] **CP-33** All services containerised and pushed to GCP Artifact Registry
- [ ] **CP-34** Cloud SQL MySQL instance provisioned and Flyway migrations applied
- [ ] **CP-35** Services deployed to Cloud Run — health checks green
- [ ] **CP-36** Dashboard accessible at GCP-provisioned URL
- [ ] **CP-37** Bot enabled on GCP — first signal produced in cloud environment
- [ ] **CP-38** 48-hour unattended run with paper trades — no crashes or missed scans

---

## Phase 6 — Production (Live Trading)

> ⚠️ Only proceed here after CP-38 is complete and win rate is satisfactory over ≥ 100 paper trades.

- [ ] **CP-39** Live MT5 account connected to MetaApi
- [ ] **CP-40** `PAPER_TRADING=false` set in GCP environment — confirmed in logs
- [ ] **CP-41** First live trade opened (minimum lot size 0.01)
- [ ] **CP-42** Risk limits verified: max open trades, SL/TP firing correctly
- [ ] **CP-43** Monitoring / alerting configured (GCP Cloud Monitoring or similar)
- [ ] **CP-44** Weekly review process established — retrain ML model monthly

---

## Phase 4b — UI Polish (SaaS Design System)

**Goal:** Dashboard and auth screens look like a real SaaS product.

- [x] **CP-UI-01** Harvest Technologies design system created (`static/css/app.css`) — Outfit font, navy/blue/green palette, glass cards, animations
- [x] **CP-UI-02** Login page revamped — glass card, orbs, Lucide icons, Google OAuth + username/password
- [x] **CP-UI-03** Register page created — full name + username grid, live password strength indicators, Google OAuth
- [x] **CP-UI-04** Forgot password page created — email field, anti-enumeration success message
- [x] **CP-UI-05** Reset password page created — token via URL, live strength indicators, error states
- [x] **CP-UI-06** Dashboard revamped — glass stat cards, topnav with mobile hamburger, proper badge system, Lucide icons
- [ ] **CP-UI-07** Google OAuth2 credentials wired up (see Notes)
- [ ] **CP-UI-08** feature/ui-polish branch merged to main via PR
- [x] **CP-UI-09** Admin panel — user list, role changes, enable/disable, self-modification guard
- [x] **CP-UI-10** Invite flow — admin sends email+role only, Brevo SMTP, user sets name+password via 72h link
- [ ] **CP-UI-11** feature/admin-panel branch merged to main via PR
- [x] **CP-UI-12** Public landing page — hero, features, how it works, signal preview, CTA
- [x] **CP-UI-13** Route split — `/` public landing, `/dashboard` authenticated app
- [x] **CP-UI-14** Custom error pages — branded 404, 403, 500
- [x] **CP-UI-15** Branded HTML email templates — invite + password reset (Thymeleaf + MimeMessage)
- [ ] **CP-UI-16** feature/public-pages branch merged to main via PR
- [x] **CP-UI-17** Favicon — ⚡ SVG icon across all pages
- [x] **CP-UI-18** Conditional Google OAuth button — hidden when credentials not configured
- [x] **CP-UI-19** Auto-dismiss flash alerts — fade out after 4 seconds
- [x] **CP-UI-20** Account settings page (/account) — update name, change password
- [x] **CP-UI-21** Bot settings page (/settings/bot, admin only) — edit all trading params from UI
- [ ] **CP-UI-22** feature/enhancements branch merged to main via PR

---

## Notes

```
CP-03: MySQL installed natively on Windows (not Docker) — 8GB RAM machine.
       Dev profile uses forexbot/forexbot123. DB user created with mysql_native_password plugin.
CP-05: MT5 path: C:\Program Files\MetaTrader 5\terminal64.exe (non-standard install).
       MT5 desktop app must be open and logged in before starting mt5-bridge.
CP-07: MetaQuotes-Demo account 109814567 — balance $100,000 USD (paper).
CP-13: Debug endpoint added: GET http://localhost:8002/debug/{symbol} — shows raw indicator
       values and both gate decisions. Useful for diagnosing HOLD reasons.
CP-15: H1 signals all HOLD over weekend (expected — forex markets closed Sat/Sun).
       Markets reopen Sunday ~21:00 UTC. First actionable signals expected during
       London session Monday (08:00–12:00 UTC).
CP-23: Models trained on 4,801 H1 candles each (~7 months of data).
       USDJPY best accuracy at 70%. Retrain monthly or after significant market regime change.
CP-UI-07: Google OAuth2 setup steps:
  1. Go to https://console.cloud.google.com → APIs & Services → Credentials
  2. Create OAuth 2.0 Client ID (Web application)
  3. Add Authorized redirect URI: http://localhost:8080/login/oauth2/code/google
  4. Set env vars: GOOGLE_CLIENT_ID=... and GOOGLE_CLIENT_SECRET=...
  5. Add to .env or application-dev.yml (never commit credentials)
```
