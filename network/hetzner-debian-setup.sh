#!/usr/bin/env bash
set -Eeuo pipefail
# Run as root on a fresh Debian 12/13 Hetzner Cloud VM.
if [[ $EUID -ne 0 ]]; then echo "Run with sudo"; exit 1; fi
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get -y full-upgrade
apt-get install -y ca-certificates curl wget unzip python3 tar jq ufw openssl
if ! apt-cache show openjdk-21-jre-headless >/dev/null 2>&1; then
  apt-get install -y gnupg
  install -d -m 0755 /etc/apt/keyrings
  curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
  . /etc/os-release
  echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb ${VERSION_CODENAME} main" >/etc/apt/sources.list.d/adoptium.list
  apt-get update
  apt-get install -y temurin-21-jre
else
  apt-get install -y openjdk-21-jre-headless
fi
java -version
id dune >/dev/null 2>&1 || useradd --system --home-dir /opt/dunerealms --create-home --shell /usr/sbin/nologin dune
install -d -o dune -g dune /opt/dunerealms
cat >/etc/sysctl.d/90-dunerealms.conf <<'EOF'
vm.swappiness=10
EOF
sysctl --system >/dev/null
echo "Debian updated and Java 21 installed."
echo "Next: copy the GitHub Actions DuneRealms-Velocity-Network.zip to /opt/dunerealms and run deploy-network.sh."
