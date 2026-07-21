#!/bin/bash
set -e
BRANCH="main"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
echo ""
echo "======================================"
echo "  Blue Ocean Hub — Deploying from $BRANCH"
echo "======================================"
echo ""
echo ">>> Pulling latest code..."
git -C "$APP_DIR" fetch origin
git -C "$APP_DIR" checkout "$BRANCH"
git -C "$APP_DIR" pull origin "$BRANCH"
echo ""
echo ">>> Stopping containers..."
docker compose -f "$APP_DIR/docker-compose.yml" down
echo ""
echo ">>> Building and starting..."
docker compose -f "$APP_DIR/docker-compose.yml" up -d --build
echo ""
echo ">>> Container status:"
docker compose -f "$APP_DIR/docker-compose.yml" ps
echo ""
echo "======================================"
echo "  Done! App running at https://blue-ocean-hub.com"
echo "======================================"
