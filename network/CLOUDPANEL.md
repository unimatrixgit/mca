# Hetzner DuneRealms: CloudPanel on the Vaerkring lobby host

The **Vaerkring lobby server is also the web hosting server**.
Run CloudPanel and the Vaerkring Paper backend on the same Debian VM.
Velocity and the other five planets may run on this VM or another VM.

## Recommended hostname
- `dune-lobby-01`: CloudPanel, Nginx, website, and Vaerkring Paper (port 25571)
- `dune-mc-01`: Velocity and five planet backends (optional second VM)

## Install order on a fresh Debian 12/13 VM
1. Install CloudPanel **first** on the fresh VM with `network/hetzner-cloudpanel-setup.sh`.
2. Complete CloudPanel initial setup on `https://LOBBY_IP:8443`.
3. Run `network/hetzner-debian-setup.sh` to install Java 21 and Minecraft dependencies.
4. Deploy the GitHub Actions network ZIP with `network/deploy-network.sh` if running the full network locally.
   If using separate VMs, copy only the Vaerkring backend to the lobby VM and
   adjust Velocity's `spacing-guild` address to its private Hetzner network IP.
   The existing deployment script assumes all six backends and proxy are local:
   multi-VM installation is not automated yet.

## Networking and security
- Public TCP 80/443: CloudPanel website.
- TCP 8443: CloudPanel admin, restricted to your administrator IP.
- TCP 22: SSH, restricted to your administrator IP.
- Public TCP 25565: Velocity proxy **only on the VM hosting Velocity**.
- TCP 25571: Vaerkring backend; only accessible from Velocity over private network.
- TCP 25566–25570: Planet backends; private access only.
- Never expose MariaDB or Paper backends to the public internet.
- If Velocity and lobby are on different hosts, change `server-ip=127.0.0.1`
  in lobby `server.properties` to the lobby VM's private network IP,
  configure Paper's Velocity forwarding secret, and firewall port 25571.
- Reserve sufficient RAM for CloudPanel services and Minecraft; do not allocate
  all RAM to Java. Monitor CPU, disk, and memory during testing.

The network packaging and lobby generation are prototypes: custom Vaerkring
terrain, NPC navigation, and economy synchronization are not yet verified.

CloudPanel official documentation:
https://www.cloudpanel.io/docs/v2/getting-started/hetzner-cloud/installation/installer/
