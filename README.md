# forex-ai-bot

An AI-powered forex trading bot using a hybrid technical + ML strategy, built with Python, Spring Boot, MySQL, and Docker — deployable to GCP.

> **Track your progress:** See [CHECKPOINTS.md](./CHECKPOINTS.md) for the full development-to-production milestone list.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        User Browser                          │
│                   (Thymeleaf Dashboard)                      │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP
┌──────────────────────────▼──────────────────────────────────┐
│              Spring Boot Backend (Java)                      │
│         REST API · Trade History · Bot Control              │
└───────┬─────────────────────────────────────┬───────────────┘
        │ REST/HTTP                            │ JDBC + Flyway
┌───────▼───────────┐               ┌─────────▼──────────────┐
│  signal-engine    │               │       MySQL 8           │
│    (Python)       │               │  trades · signals       │
│  Technical Ind.   │               │  ohlcv · bot_config     │
│  + XGBoost ML     │               └────────────────────────┘
└───────┬───────────┘
        │ REST/HTTP
┌───────▼───────────┐
│   mt5-bridge      │  ← Windows only for dev (MetaApi for UAT/prod)
│    (Python)       │
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
| `backend` | Java 21 / Spring Boot 3 | 8080 | REST API + Thymeleaf dashboard |
| `mysql` | MySQL 8 | 3306 | Persistence layer (migrations via Flyway) |

---

## ⚠️ MT5 Windows Constraint

The `MetaTrader5` Python package **only runs on Windows**. Deployment strategy by environment:

| Environment | MT5 Bridge | Everything Else |
|---|---|---|
| **Development** | Local Windows machine | Local / Docker |
| **UAT / Prod** | MetaApi REST API (Linux-friendly) | GCP Cloud Run + Cloud SQL |

For development, a local Windows machine running MT5 Desktop is perfectly sufficient.

---

## Quick Start (Development)

### Prerequisites
- Python 3.11+
- Java 21+
- Docker Desktop
- MetaTrader 5 installed on Windows with a demo account
- IntelliJ IDEA (Java) + PyCharm or Python plugin (for Python modules)

### 1. Configure environment
```bash
cp .env.example .env
# Fill in MT5_BRIDGE_MT5_LOGIN, MT5_BRIDGE_MT5_PASSWORD, MT5_BRIDGE_MT5_SERVER
# and a strong DB_PASSWORD
```

### 2. Start MySQL

**Local dev (native MySQL — recommended for low-RAM machines):**
Ensure MySQL 8 is running locally and create the database:
```sql
CREATE DATABASE IF NOT EXISTS forexbot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'forexbot'@'localhost' IDENTIFIED WITH mysql_native_password BY 'forexbot123';
GRANT ALL PRIVILEGES ON forexbot.* TO 'forexbot'@'localhost';
```
Flyway runs automatically when the Spring Boot backend starts — tables are created from `backend/src/main/resources/db/migration/V1__init_schema.sql`.

**Docker (optional, for machines with sufficient RAM):**
```bash
docker compose up mysql -d
```

### 3. Start mt5-bridge (Windows only — MT5 desktop app must be open and logged in)
```bash
cd mt5-bridge
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python src/main.py
# Verify: GET http://localhost:8001/health → {"status":"ok","connected":true}
```

### 4. Start signal-engine
```bash
cd signal-engine
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python src/main.py
# Verify: GET http://localhost:8002/health
```

### 4b. Train ML models (first run only)
```bash
# From project root — signal-engine must be running
python train_all.py
```

### 5. Start backend (Flyway runs on startup)
```bash
cd backend
./mvnw spring-boot:run
```

### 6. Open dashboard
Navigate to `http://localhost:8080`

---

## Database Migrations (Flyway)

Schema is managed exclusively by Flyway — never by Hibernate DDL.

| File | Description |
|---|---|
| `backend/src/main/resources/db/migration/V1__init_schema.sql` | Initial schema: trades, signals, ohlcv, bot_config |

**To add a schema change:** create `V2__your_description.sql` in the same folder. Flyway applies it automatically on next startup.

> Never edit an existing migration file after it has been applied to any environment. Always create a new versioned file.

---

## Python Interpreter (IntelliJ)

IntelliJ IDEA treats this as a Java project. For Python services, do one of:

**Option A (recommended) — open Python dirs in PyCharm:**
Open `mt5-bridge/` and `signal-engine/` as separate PyCharm projects. Use IntelliJ for the `backend/` module only.

**Option B — Python plugin inside IntelliJ:**
1. Install the **Python** plugin: `Settings → Plugins → Marketplace → Python`
2. `File → Project Structure → Modules → + → Import Module`
3. Select `mt5-bridge/` → choose **Python** module type
4. Set SDK to your local Python 3.11 interpreter
5. Repeat for `signal-engine/`

---

## Tech Stack

| Layer | Tool | Cost |
|---|---|---|
| MT5 connectivity (dev) | `MetaTrader5` Python package | Free |
| MT5 connectivity (prod) | MetaApi | Free tier / ~$8+/mo |
| Technical indicators | `ta` (RSI, MACD, EMA, Bollinger, ATR, OBV) | Free |
| ML model | `xgboost`, `scikit-learn` | Free |
| Data wrangling | `pandas`, `numpy` | Free |
| HTTP server (Python) | `FastAPI` + `uvicorn` | Free |
| Backend framework | `Spring Boot 3` | Free |
| UI templating | `Thymeleaf` | Free |
| Database migrations | `Flyway` | Free |
| Database | `MySQL 8` | Free |
| Containerisation | `Docker` + `Docker Compose` | Free |
| Cloud (UAT/prod) | GCP Cloud Run + Cloud SQL | ~$15–30/mo |

**Total cost: $0 for local development. ~$15–30/month on GCP.**

---

## Project Structure

```
forex-ai-bot/
├── CHECKPOINTS.md                         # Development milestone tracker
├── mt5-bridge/                            # Python: MT5 connectivity layer
│   ├── src/
│   │   ├── main.py                        # FastAPI entry point
│   │   ├── config.py                      # Settings (pydantic-settings)
│   │   ├── mt5_client.py                  # MT5 connection lifecycle
│   │   ├── feed.py                        # OHLCV + tick data
│   │   └── executor.py                    # Order open/close
│   ├── requirements.txt
│   └── Dockerfile
├── signal-engine/                         # Python: strategy + ML
│   ├── src/
│   │   ├── main.py                        # FastAPI entry point
│   │   ├── config.py                      # Settings
│   │   ├── indicators/technical.py        # RSI, MACD, EMA, Bollinger, ATR
│   │   ├── ml/model.py                    # XGBoost train + predict
│   │   └── strategy/hybrid.py            # Two-gate signal combiner
│   ├── requirements.txt
│   └── Dockerfile
├── backend/                               # Java: Spring Boot
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/forexbot/
│       │   ├── ForexBotApplication.java
│       │   ├── controller/                # DashboardController, ApiController
│       │   ├── service/                   # TradeService, SignalPollerService
│       │   ├── repository/                # JPA repositories
│       │   ├── model/                     # Trade, Signal, BotConfig entities
│       │   ├── config/                    # WebClientConfig, BotProperties
│       │   └── dto/                       # SignalDto
│       └── resources/
│           ├── application.yml
│           ├── db/migration/
│           │   └── V1__init_schema.sql    # Flyway migration
│           ├── templates/dashboard/       # Thymeleaf HTML
│           └── static/                    # CSS / JS
├── docker-compose.yml
└── .env.example
```
