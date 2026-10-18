#!/usr/bin/env bash
set -Eeuo pipefail
# Fresh Debian 12/13 Hetzner VM dedicated to CloudPanel web hosting.
[[ $EUID -eq 0 ]] || { echo "Run with sudo"; exit 1; }
. /etc/os-release
[[ "$ID" == debian && ( "$VERSION_ID" == 12 || "$VERSION_ID" == 13 ) ]] || { echo "Requires Debian 12 or 13"; exit 1; }
if command -v clpctl >/dev/null 2>&1; then echo "CloudPanel already installed"; exit 0; fi
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get -y upgrade
apt-get install -y curl wget sudo ca-certificates
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl -fsSL https://installer.cloudpanel.io/ce/v2/install.sh -o "$tmp/install.sh"
echo "8146dbe0a488e7088b04071b0c34d59aa0ab1fe9dcec382d395fd155c9e6c476  $tmp/install.sh" | sha256sum -c -
CLOUD=hetzner DB_ENGINE=MARIADB_11.8 bash "$tmp/install.sh"
echo "CloudPanel installed. Restrict TCP 8443 to your own IP in Hetzner firewall, then visit https://SERVER_IP:8443 and create admin immediately."
