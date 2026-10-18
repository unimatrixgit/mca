#!/usr/bin/env bash
set -Eeuo pipefail
# Prepares a Debian 12/13 VM for Pterodactyl Wings.
# Does NOT install Wings or register the node; follow official instructions.
[[ $EUID -eq 0 ]] || { echo "Run with sudo"; exit 1; }
. /etc/os-release
[[ "$ID" == "debian" ]] || { echo "Debian required"; exit 1; }
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get -y full-upgrade
apt-get install -y ca-certificates curl gnupg lsb-release
install -d -m 0755 /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" >/etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
echo "Docker installed. Continue with official Wings installation and per-node panel configuration."
echo "If migrating from systemd Minecraft services, disable dune-*.service before running servers in Pterodactyl."
