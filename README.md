# forex-ai-bot

An AI-powered forex trading SaaS built with Python, Spring Boot, and MySQL. Uses a hybrid technical + ML strategy. Designed for local dev on Windows (MT5 bridge) with a cloud-native path via MetaAPI + GCP.

> **Track your progress:** See [CHECKPOINTS.md](./CHECKPOINTS.md) for the full development-to-production milestone list.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        User Browser                          │
│          Thymeleaf Dashboard + EventSource (SSE)            │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP / SSE
┌──────────────────────────▼──────────────────────────────────┐
│              Spring Boot Backend (Java 21)                   │
│   Dashboard · Admin · Settings · Auth · SSE · Email         │
└───────┬─────────────────────────────────────┬───────────────┘
        │ REST/HTTP                            │ JDBC + Flyway
┌───────▼───────────┐               ┌─────────▼──────────────┐
│  signal-engine    │               │       MySQL 8           │
│    (Python)       │               │  trades · signals       │
│  Technical Ind.   │               │  bot_config             │
│  + XGBoost ML     │               │  symbol_settings        │
└───────┬───────────┘               │  users · ohlcv          │
        │ REST/HTTP                 └────────────────────────┘
┌───────▼───────────┐
│   mt5-bridge      │  ← Windows only for dev
│    (Python)       │     MetaAPI replaces this in Phase 5
│  MetaTrader5 API  │
│  Price feed +     │
│  Order executor   │
└───────┬───────────┘
        │ MT5 Protocol
┌───────▼───────────┐
│  MetaTrader 5     │
│  (Demo Account)   │
└───────────────────┘
```

---

## Services

| Service | Language | Port | Purpose |
|---|---|---|---|
| `mt5-bridge` | Python 3.11 | 8001 | MT5 data feed + order execution |
| `signal-engine` | Python 3.11 | 8002 | Hybrid indicator + ML signals |
| `backend` | Java 21 / Spring Boot 3.3 | 8080 | REST API + Thymeleaf SaaS dashboard |
| `mysql` | MySQL 8 | 3306 | Persistence (Flyway migrations) |

---

## ⚠️ MT5 Windows Constraint

The `MetaTrader5` Python package **only runs on Windows**. Deployment strategy:

| Environment | MT5 Bridge | Everything Else |
|---|---|---|
| **Development** | Local Windows machine (MetaTrader 5 desktop open) | Local |
| **Phase 5 / Prod** | MetaAPI REST API (cloud, Linux-friendly) | GCP Cloud Run + Cloud SQL |

---

## Quick Start (Development)

### Prerequisites
- Python 3.11+
- Java 21+
- MetaTrader 5 installed on Windows with a demo account
- MySQL 8 (native Windows install recommended for low-RAM machines — Docker optional)
- IntelliJ IDEA (Java backend) + PyCharm or Python plugin (Python services)

### 1. Configure environment
```bash
cp .env.example .env
# Fill in: MT5_BRIDGE_MT5_LOGIN, MT5_BRIDGE_MT5_PASSWORD, MT5_BRIDGE_MT5_SERVER
# Also set: DB_PASSWORD, JWT_SECRET (or Spring will auto-generate a warning)
# For email: MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM (Brevo SMTP — optional for dev)
```

### 2. Start MySQL
**Native install (recommended):**
```sql
CREATE DATABASE IF NOT EXISTS forexbot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'forexbot'@'localhost' IDENTIFIED WITH mysql_native_password BY 'forexbot123';
GRANT ALL PRIVILEGES ON forexbot.* TO 'forexbot'@'localhost';
```
Flyway runs automatically on Spring Boot startup and applies all migrations in order.

**Docker (optional):**
```bash
docker compose up mysql -d
```

### 3. Start mt5-bridge (MT5 desktop must be open and logged in)
```bash
cd mt5-bridge
python -m venv venv && venv\Scripts\activate
pip install -r requirements.txt
python src/main.py
# Verify: GET http://localhost:8001/health → {"status":"ok","connected":true}
```

### 4. Start signal-engine
```bash
cd signal-engine
python -m venv venv && venv\Scripts\activate
pip install -r requirements.txt
python src/main.py
# Verify: GET http://localhost:8002/health
```

### 4b. Train ML models (first run only, or after market regime change)
```bash
# signal-engine must be running
python train_all.py
```

### 5. Start backend (Flyway migrations run on startup)
```bash
cd backend
./mvnw spring-boot:run
```

### 6. Open dashboard
Navigate to `http://localhost:8080` — login with the seed admin account (see `DataInitializer`).

---

## Database Migrations (Flyway)

Schema is managed exclusively by Flyway — never by Hibernate DDL auto.

| File | Description |
|---|---|
| `V1__init_schema.sql` | Initial schema: trades, signals, ohlcv, bot_config |
| `V2__users.sql` | Users table — email, role, enabled, OAuth2 support |
| `V3__user_fullname_and_password_reset.sql` | Full name, invite token, password reset token |
| `V4__symbol_settings.sql` | Per-symbol SL/TP/volume/enabled — seeds 4 default pairs |
| `V5__add_phone_to_users.sql` | Phone field on users (SA format `+27XXXXXXXXX`) |
| `V6__email_verification_and_account_lockout.sql` | Email verified flag + verification token + persistent login lockout |

> Never edit an existing migration after it has been applied. Always create a new versioned file.

---

## Key Features

### Real-time Dashboard
- Live signal and position tables via SSE (`EventSource`) — no page reload
- Soft toast notification if SSE disconnects (replaces previous 30s hard reload)
- Stat cards (Balance, Equity, Open Trades, Total P&L) update live

### Bot Control
- Enable/disable bot from dashboard with a single click
- Market-hours auto-detection — nav badge shows Bot Running / Market Closed / Bot Stopped
- Paper trading mode — toggle in Bot Settings (no real orders placed)

### Signal Strategy (Hybrid Two-Gate)
- **Technical gate** — EMA trend alignment + RSI (30–65 BUY / 35–70 SELL) + MACD sign confirmation; all three must pass
- **AI gate** — XGBoost model must predict the same direction with ≥ 55% confidence
- Gate optimisation: AI model is skipped entirely if the technical gate returns HOLD (saves CPU)
- H1 candle cache (55 min TTL) eliminates redundant MT5 bridge calls on every scan cycle
- Debug endpoint: `GET http://localhost:8002/debug/{symbol}` — shows all indicator values and gate decisions

### Risk Management
- Global defaults: SL pips, TP pips, lot size, max open trades
- Per-symbol overrides: individual SL/TP/volume/enabled per pair (EURUSD, GBPUSD, USDJPY, AUDUSD)
- Live R:R ratio display in Bot Settings UI

### Email Notifications
- Trade open alerts → ADMIN users (Brevo SMTP; console log in dev mode)
- Weekly review email → all users → every Friday 18:00 UTC (signals, P&L, best/worst pair)
- Invite + password reset emails with branded templates

### Auth & Security
- Username/password login with BCrypt
- Google OAuth2 (optional — configure `GOOGLE_CLIENT_ID` + `GOOGLE_CLIENT_SECRET`)
- Role-aware redirect on login: ADMIN → `/admin/users`, USER → `/dashboard`
- Admin panel: manage users, change roles, enable/disable accounts
- Email verification on registration — 24h token, resend flow
- Login rate limiting — IP blocked after 5 failed attempts for 15 minutes
- Persistent account lockout — DB-backed, auto-clears on expiry
- Input validation — MX email domain check, SA phone format, strong password, cross-field match

---

## Project Structure

```
forex-ai-bot/
├── CHECKPOINTS.md
├── README.md
├── train_all.py                               # Train ML models for all 4 symbols
├── .env.example                               # Copy to .env — never commit .env
├── docker-compose.yml
│
├── mt5-bridge/                                # Python: MT5 connectivity
│   └── src/
│       ├── main.py                            # FastAPI entry point
│       ├── config.py                          # Settings (pydantic-settings)
│       ├── mt5_client.py                      # MT5 connection lifecycle
│       ├── feed.py                            # OHLCV + tick data
│       └── executor.py                        # Order open/close
│
├── signal-engine/                             # Python: strategy + ML
│   └── src/
│       ├── main.py
│       ├── config.py
│       ├── indicators/technical.py            # RSI, MACD, EMA, Bollinger, ATR, OBV
│       ├── ml/model.py                        # XGBoost train + predict
│       └── strategy/hybrid.py                # Two-gate signal combiner
│
└── backend/                                   # Java 21: Spring Boot SaaS
    └── src/main/
        ├── java/com/forexbot/
        │   ├── ForexBotApplication.java
        │   ├── config/
        │   │   ├── BotProperties.java         # Global defaults (SL/TP/volume/symbols)
        │   │   ├── DataInitializer.java        # Seed admin user on first run
        │   │   ├── PasswordEncoderConfig.java
        │   │   ├── SecurityConfig.java         # Spring Security + OAuth2 + role redirect
        │   │   └── WebClientConfig.java        # MT5 + signal-engine WebClient beans
        │   ├── controller/
        │   │   ├── AdminController.java        # /admin/users — user management
        │   │   ├── AccountController.java      # /account — profile + password
        │   │   ├── AuthController.java         # /login, /register, /forgot-password
        │   │   ├── BotSettingsController.java  # /settings/bot — global + per-symbol
        │   │   ├── DashboardController.java    # /dashboard, /bot/enable, /trade/{id}/close
        │   │   ├── GlobalModelAdvice.java      # botEnabled + marketOpen → all templates
        │   │   ├── LiveDataController.java     # /api/live/signals|positions|stats (JSON)
        │   │   ├── PublicController.java       # / landing page
        │   │   └── SseController.java          # /api/dashboard/stream (SSE)
        │   ├── dto/
        │   │   ├── SignalDto.java
        │   │   ├── WeeklyStatsDto.java         # Weekly review aggregation DTO + helpers
        │   │   └── (form DTOs: Invite, Register, ChangePassword, etc.)
        │   ├── model/
        │   │   ├── BotConfig.java             # Key-value runtime config store
        │   │   ├── Signal.java
        │   │   ├── SymbolSettings.java        # Per-symbol SL/TP/volume/enabled
        │   │   ├── Trade.java
        │   │   └── User.java                  # Roles: ADMIN / USER
        │   ├── repository/
        │   │   ├── BotConfigRepository.java
        │   │   ├── SignalRepository.java
        │   │   ├── SymbolSettingsRepository.java
        │   │   ├── TradeRepository.java        # Weekly review queries + countOpen
        │   │   └── UserRepository.java
        │   └── service/
        │       ├── EmailService.java           # Brevo SMTP; console fallback in dev
        │       ├── MarketHoursService.java     # Forex session open/close detection
        │       ├── MarketSchedulerService.java # Scheduled market-open/close events
        │       ├── SignalPollerService.java    # @Scheduled scan loop; SSE broadcast
        │       ├── SseService.java             # SseEmitter registry + broadcast
        │       ├── SymbolSettingsService.java  # Per-symbol risk CRUD + getOrCreate
        │       ├── TradeService.java           # openTrade / closeTrade
        │       ├── UserService.java
        │       ├── WeeklyEmailScheduler.java   # Friday 18:00 UTC email trigger
        │       ├── WeeklyReviewService.java    # Weekly stats aggregation
        │       └── UserDetailsServiceImpl.java
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            ├── db/migration/
            │   ├── V1__init_schema.sql
            │   ├── V2__users.sql
            │   ├── V3__user_fullname_and_password_reset.sql
            │   └── V4__symbol_settings.sql
            ├── static/
            │   ├── css/app.css                # Blue Ocean Hub design system
            │   └── favicon.svg
            └── templates/
                ├── dashboard/index.html       # Main trading dashboard (SSE live)
                ├── admin/users.html
                ├── settings/bot.html          # Global + per-symbol risk controls
                ├── account/settings.html
                ├── public/landing.html
                ├── auth/                      # Login, register, forgot/reset password
                ├── email/                     # Invite, password-reset, trade-notification, weekly-review
                └── error/                     # Branded 404, 403, 500
```

---

## Tech Stack

| Layer | Tool | Cost |
|---|---|---|
| MT5 connectivity (dev) | `MetaTrader5` Python package | Free |
| MT5 connectivity (prod) | MetaAPI | Free tier / ~$8+/mo |
| Technical indicators | `ta` (RSI, MACD, EMA, Bollinger, ATR, OBV) | Free |
| ML model | `xgboost`, `scikit-learn` | Free |
| Data wrangling | `pandas`, `numpy` | Free |
| HTTP server (Python) | `FastAPI` + `uvicorn` | Free |
| Backend framework | `Spring Boot 3.3` | Free |
| UI templating | `Thymeleaf` | Free |
| Real-time updates | `SseEmitter` (SSE) + `EventSource` | Free |
| Email | `Brevo SMTP` (JavaMailSender) | Free tier |
| Database migrations | `Flyway` | Free |
| Database | `MySQL 8` | Free |
| Containerisation | `Docker` + `Docker Compose` | Free |
| Cloud (Phase 5+) | GCP Cloud Run + Cloud SQL | ~$15–30/mo |

**Total cost: $0 for local development.**

---

## Python Interpreter (IntelliJ)

IntelliJ IDEA treats this as a Java project. For Python services:

**Option A (recommended):** Open `mt5-bridge/` and `signal-engine/` as separate PyCharm projects.

**Option B — Python plugin inside IntelliJ:**
1. Install Python plugin: `Settings → Plugins → Marketplace → Python`
2. `File → Project Structure → Modules → + → Import Module`
3. Select `mt5-bridge/` → Python module type → set SDK to Python 3.11
4. Repeat for `signal-engine/`

---

## Documentation

Word documents for the technical reference and trader overview are generated from scripts — the `.docx` outputs are **not** committed to the repo.

**To regenerate:**

```bash
cd docs
npm install        # first time only — installs the docx package
npm run docs       # generates both docs into docs/output/
```

Individual commands:

```bash
npm run docs:tech-ref    # Technical Reference Document
npm run docs:overview    # Platform Overview (non-technical)
```

Output files land in `docs/output/` which is `.gitignore`d. Share them directly with clients or team members from there.
