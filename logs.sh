#!/bin/bash
# Usage: ./logs.sh [service]
# Services: backend, signal-engine, mt5-bridge, mysql
# Default: backend
SERVICE="${1:-backend}"
cd ~/forex-ai-bot
docker compose logs -f "$SERVICE"
