#!/usr/bin/env bash
set -Eeuo pipefail
# Usage: sudo bash deploy-network.sh /path/to/DuneRealms-Velocity-Network.zip
[[ $EUID -eq 0 ]] || { echo "Run as root"; exit 1; }
ZIP="${1:?Provide network ZIP path}"
BASE=/opt/dunerealms
[[ -f "$ZIP" ]] || { echo "Missing ZIP: $ZIP"; exit 1; }
command -v java >/dev/null || { echo "Install Java 21 first"; exit 1; }
install -d -o dune -g dune "$BASE"
unzip -o "$ZIP" -d "$BASE"
ROOT="$BASE/DuneRealms-Velocity-Network"
[[ -d "$ROOT/servers/spacing-guild" ]] || { echo "Unexpected ZIP layout"; exit 1; }
# Download official Paper builds from PaperMC downloads API.
get_paper() {
  local version=1.21.4
  local json build url
  json="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$version/builds")"
  url="$(printf '%s' "$json" | jq -r '[.[] | select(.channel == "STABLE") | .downloads."server:default".url] | first // empty')"
  [[ -n "$url" ]] || { echo "No stable Paper build found for $version"; return 1; }
  curl -fL "$url" -o "$ROOT/paper.jar"
}
get_velocity() {
  local json url
  json="$(curl -fsSL 'https://fill.papermc.io/v3/projects/velocity/versions/3.4.0-SNAPSHOT/builds')"
  url="$(printf '%s' "$json" | jq -r '[.[] | select(.channel == "STABLE") | .downloads."server:default".url] | first // empty')"
  [[ -n "$url" ]] || { echo "No stable Velocity build found; install proxy jar manually"; return 1; }
  curl -fL "$url" -o "$ROOT/proxy/velocity.jar"
}
get_paper
# Velocity download failure does not silently start an incomplete proxy.
get_velocity
for dir in "$ROOT"/servers/*; do
  [[ -d "$dir" ]] || continue
  ln -sfn ../../paper.jar "$dir/paper.jar"
done
chown -R dune:dune "$BASE"
create_unit() {
  local name="$1" dir="$2" jar="$3" memory="$4"
  cat >"/etc/systemd/system/dune-${name}.service" <<EOF
[Unit]
Description=DuneRealms ${name}
After=network-online.target
Wants=network-online.target
[Service]
Type=simple
User=dune
Group=dune
WorkingDirectory=${dir}
ExecStart=/usr/bin/java -Xms512M -Xmx${memory} -jar ${jar} nogui
Restart=on-failure
RestartSec=10
LimitNOFILE=65535
NoNewPrivileges=true
[Install]
WantedBy=multi-user.target
EOF
}
create_unit proxy "$ROOT/proxy" velocity.jar 512M
for name in arrakis giedi-prime kaitain caladan sigma-draconis-iv spacing-guild; do
  create_unit "$name" "$ROOT/servers/$name" paper.jar 2G
done
systemctl daemon-reload
echo "Installed units: dune-proxy and dune-{arrakis,giedi-prime,kaitain,caladan,sigma-draconis-iv,spacing-guild}"
echo "Review eula.txt on every backend; accept only after reading the EULA."
echo "After configuring firewall and accepting EULA, run: systemctl enable --now dune-proxy dune-arrakis dune-giedi-prime dune-kaitain dune-caladan dune-sigma-draconis-iv dune-spacing-guild"
