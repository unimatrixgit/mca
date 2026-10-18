# DuneRealms web hosting on Hetzner Cloud
Use a **separate fresh Debian 12/13 VM** for CloudPanel. Do not run the Minecraft
bootstrap or Velocity/Paper deployment on this VM. CloudPanel installs Nginx,
database and system packages and is intended for a clean host.

## VM names
- `dune-web-01`: CloudPanel / Nginx / website
- `dune-mc-01`: Velocity and six Paper servers (size RAM to workload)

## Web installation
SSH to `dune-web-01`, clone the repo, then:
```bash
sudo bash network/hetzner-cloudpanel-setup.sh
```
Script downloads the official CloudPanel Hetzner installer and verifies its
published SHA-256 before execution. If checksum changes, review official
documentation and update the script instead of bypassing verification.

Open Hetzner firewall: TCP 80 and 443 to public; TCP 8443 and 22 only from
your administrator IP. Do not expose SQL ports. Visit
`https://WEB_PUBLIC_IP:8443` promptly to create the first admin user.
Create the website as a Node.js site or reverse-proxy site as appropriate for
the website's runtime, configure DNS, and enable TLS in CloudPanel.
Never put API secrets in a public web root.

On `dune-mc-01`, allow public TCP 25565 for Velocity and SSH only from your IP.
Paper ports 25566-25571 must remain private. The existing network ZIP is a
prototype; Spacing Guild generator, economy synchronization and lobby travel
behavior still require testing and implementation.

CloudPanel official Hetzner instructions:
https://www.cloudpanel.io/docs/v2/getting-started/hetzner-cloud/installation/installer/
