#!/usr/bin/env bash
set -Eeuo pipefail
# Deploy one node of a DuneRealms Velocity network on Debian.
# Usage: sudo bash network/deploy-node.sh /path/to/DuneRealms-Velocity-Network.zip <proxy|spacing-guild|arrakis|giedi-prime|kaitain|caladan|sigma-draconis-iv> [private-bind-ip]
# On a single VM use deploy-network.sh instead.
[[ $EUID -eq 0 ]] || { echo "Run with sudo"; exit 1; }
ZIP="${1:?ZIP required}"
NODE="${2:?node name required}"
IP="${3:-127.0.0.1}"
case "$NODE" in proxy|spacing-guild|arrakis|giedi-prime|kaitain|caladan|sigma-draconis-iv) ;; *) echo "Unknown node"; exit 1;; esac
[[ -f "$ZIP" ]] || { echo "ZIP not found"; exit 1; }
id dune >/dev/null 2>&1 || useradd --system --create-home --home-dir /opt/dunerealms --shell /usr/sbin/nologin dune
BASE=/opt/dunerealms
mkdir -p "$BASE"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
unzip -q "$ZIP" -d "$TEMP"
SRC="$TEMP/DuneRealms-Velocity-Network"
[[ -d "$SRC/servers/spacing-guild" ]] || { echo "Wrong ZIP layout"; exit 1; }
if [[ "$NODE" == proxy ]]; then
  mkdir -p "$BASE/proxy"
  cp -a "$SRC/proxy/." "$BASE/proxy/"
  echo "IMPORTANT: Edit $BASE/proxy/velocity.toml to point each backend to its private VM IP."
  echo "IMPORTANT: Distribute $BASE/proxy/forwarding.secret to each backend's Paper proxy config."
  DIR="$BASE/proxy"; JAR=velocity.jar; MEM=512M
else
  mkdir -p "$BASE/servers/$NODE"
  cp -a "$SRC/servers/$NODE/." "$BASE/servers/$NODE/"
  sed -i "s/^server-ip=.*/server-ip=$IP/" "$BASE/servers/$NODE/server.properties"
  echo "IMPORTANT: Set Paper Velocity forwarding secret to match proxy's forwarding.secret."
  DIR="$BASE/servers/$NODE"; JAR=paper.jar; MEM=2G
fi
cat >"/etc/systemd/system/dune-$NODE.service" <<EOF
[Unit]
Description=DuneRealms $NODE
After=network-online.target
Wants=network-online.target
[Service]
Type=simple
User=dune
Group=dune
WorkingDirectory=$DIR
ExecStart=/usr/bin/java -Xms512M -Xmx$MEM -jar $JAR nogui
Restart=on-failure
RestartSec=10
LimitNOFILE=65535
NoNewPrivileges=true
[Install]
WantedBy=multi-user.target
EOF
chown -R dune:dune "$BASE"
systemctl daemon-reload
echo "Prepared dune-$NODE service; server jar must be installed in $DIR/$JAR."
echo "Review Minecraft EULA and set eula=true on Paper servers after accepting it."
echo "Do not start until jar, forwarding secret and private IP settings are correct."
