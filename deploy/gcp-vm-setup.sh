#!/bin/bash
# ── Blue Ocean Hub — GCP VM Setup Script ─────────────────────────────────────
# Run this on a fresh Ubuntu 22.04 GCP e2-medium VM after SSH'ing in.
# Usage: bash gcp-vm-setup.sh
# ─────────────────────────────────────────────────────────────────────────────

set -e
echo "=== Blue Ocean Hub — VM Setup ==="

# ── 1. System update ──────────────────────────────────────────────────────────
sudo apt-get update -y && sudo apt-get upgrade -y

# ── 2. Install Docker ────────────────────────────────────────────────────────
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Allow running Docker without sudo
sudo usermod -aG docker $USER

# ── 3. Install Nginx ─────────────────────────────────────────────────────────
sudo apt-get install -y nginx
sudo systemctl enable nginx

# ── 4. Install Git ───────────────────────────────────────────────────────────
sudo apt-get install -y git

echo ""
echo "=== Setup complete ==="
echo ""
echo "NEXT STEPS:"
echo "  1. Log out and back in (so Docker group takes effect)"
echo "  2. Clone your repo:  git clone https://github.com/YOUR_USERNAME/forex-ai-bot.git"
echo "  3. cd forex-ai-bot"
echo "  4. Create .env:      nano .env   (fill in all credentials)"
echo "  5. Start services:   docker compose up -d --build"
echo "  6. Configure Nginx:  sudo cp deploy/nginx.conf /etc/nginx/sites-available/blueocean"
echo "                       sudo ln -s /etc/nginx/sites-available/blueocean /etc/nginx/sites-enabled/"
echo "                       sudo rm -f /etc/nginx/sites-enabled/default"
echo "                       sudo nginx -t && sudo systemctl reload nginx"
echo "  7. Point Cloudflare DNS A record to this VM's external IP"
echo ""
