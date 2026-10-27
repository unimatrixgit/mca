# DuneRealms Pterodactyl deployment

## Topology
- dune-lobby-01: CloudPanel, public website, Pterodactyl **panel**, Wings node for Vaerkring.
- dune-proxy-01: Wings node for Velocity.
- dune-arrakis-01, dune-giedi-01, dune-kaitain-01, dune-caladan-01, dune-sigma-01:
  one Wings node each for its Paper backend.
- All VMs on Hetzner private network. Panel domain e.g. panel.example.com.
- CloudPanel and Pterodactyl panel must use separate site configurations; avoid
  conflicting Nginx server blocks, PHP versions, and database users.

## Installation checklist
1. Provision CloudPanel on fresh lobby VM first and create your website.
2. Create a **separate** PHP site for the Pterodactyl panel in CloudPanel.
   Follow the official Pterodactyl panel installation guide for its supported
   PHP version, Composer, database, Redis, scheduler and queue worker.
   CloudPanel is not itself a Pterodactyl installer.
3. Configure HTTPS for panel.example.com, admin login and two-factor auth.
   Link to it from the website's Admin tools page.
4. Install Docker and Pterodactyl Wings on each of the seven VMs using the
   official Wings installation instructions. On lobby VM, ensure Docker and
   CloudPanel firewall/network rules do not collide.
5. Register each node in the panel, download each node's unique Wings
   configuration and place it in /etc/pterodactyl/config.yml on its VM.
6. Import compatible Paper and Velocity eggs. Create seven server instances,
   assign their network ports and per-server resource limits. Upload the
   network configs/plugins and copy the same Velocity forwarding secret.
7. Use Pterodactyl to manage Minecraft processes. **Disable any existing
   dune-*.service units** on VMs before starting the equivalent Pterodactyl
   server to avoid port conflicts and duplicate instances.
8. Firewall Wings API to the panel, SFTP to trusted users, and Paper ports to
   the private network only. Expose public TCP 25565 solely on proxy VM.
9. Verify start/stop/restart, live console, logs, file manager and backups.

## IMPORTANT
This repository contains the topology and website integration, not a fully
installed Pterodactyl panel. Installation requires server access, node
registration, panel-generated Wings tokens and DNS records. Do not expose
Wings API credentials, panel API tokens or forwarding secrets in Git.
Use Pterodactyl's built-in authenticated live console, rather than exposing
Minecraft RCON or an unauthenticated custom command API.

Official documentation:
- https://pterodactyl.io/panel/1.0/getting_started.html
- https://pterodactyl.io/wings/1.0/installing.html
