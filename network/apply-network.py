#!/usr/bin/env python3
"""Render or distribute network settings from one JSON file.

Usage:
  python3 network/apply-network.py network/network.json --output /tmp/dune-network
  python3 network/apply-network.py network/network.json --deploy
Requires SSH keys for each node; deploy uses scp and sudo via SSH.
"""
import argparse, ipaddress, json, pathlib, re, secrets, shutil, subprocess, sys
NAMES=("proxy","spacing-guild","arrakis","giedi-prime","kaitain","caladan","sigma-draconis-iv")
BACKENDS=NAMES[1:]
ROOT="/opt/dunerealms/DuneRealms-Velocity-Network"
def valid_host(value):
    if not isinstance(value,str) or not re.fullmatch(r"[A-Za-z0-9_.:-]+",value):
        raise ValueError("Invalid host/IP: "+str(value))
    return value
def run(args): subprocess.run(args,check=True)
def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("config")
    ap.add_argument("--output",default="network/generated")
    ap.add_argument("--deploy",action="store_true",help="Copy configs to VMs and restart services")
    ap.add_argument("--secret-file",default="network/.forwarding.secret",help="Local untracked Velocity secret")
    args=ap.parse_args()
    cfg=json.loads(pathlib.Path(args.config).read_text())
    nodes=cfg["nodes"]
    if set(nodes)!=set(NAMES): raise ValueError("nodes must contain exactly: "+", ".join(NAMES))
    for n in NAMES:
        valid_host(nodes[n]["host"]);valid_host(nodes[n].get("ssh_host",nodes[n]["host"]))
        p=nodes[n]["port"]
        if not isinstance(p,int) or not 1<=p<=65535: raise ValueError("Invalid port: "+n)
    ssh_user=cfg.get("ssh_user","root")
    if not re.fullmatch(r"[a-z_][a-z0-9_-]*",ssh_user): raise ValueError("Invalid ssh_user")
    ssh_port=cfg.get("ssh_port",22)
    if not isinstance(ssh_port,int) or not 1<=ssh_port<=65535: raise ValueError("Invalid ssh_port")
    secret_path=pathlib.Path(args.secret_file)
    if not secret_path.exists():
        secret_path.parent.mkdir(parents=True,exist_ok=True)
        secret_path.write_text(secrets.token_hex(32)+"\n")
        secret_path.chmod(0o600)
    secret=secret_path.read_text().strip()
    if not re.fullmatch(r"[a-fA-F0-9]{64}",secret): raise ValueError("Invalid forwarding secret")
    out=pathlib.Path(args.output)
    out.mkdir(parents=True,exist_ok=True)
    (out/"proxy").mkdir(exist_ok=True)
    lines=['config-version = "2.7"','bind = "0.0.0.0:'+str(nodes["proxy"]["port"])+'"',
           'motd = "<gold>DuneRealms <gray>| Vaerkring"','show-max-players = 100',
           'online-mode = true','player-info-forwarding-mode = "modern"',
           'forwarding-secret-file = "forwarding.secret"','[servers]']
    for name in BACKENDS:
        lines.append('  "'+name+'" = "'+nodes[name]["host"]+':'+str(nodes[name]["port"])+'"')
    lines.extend(['try = ["spacing-guild"]','[advanced]','bungee-plugin-message-channel = true'])
    (out/"proxy"/"velocity.toml").write_text("\n".join(lines)+"\n")
    (out/"proxy"/"forwarding.secret").write_text(secret+"\n")
    (out/"proxy"/"forwarding.secret").chmod(0o600)
    for name in BACKENDS:
        dest=out/name
        dest.mkdir(exist_ok=True)
        (dest/"server.properties.patch").write_text("server-ip="+nodes[name]["host"]+"\nserver-port="+str(nodes[name]["port"])+"\n")
        (dest/"paper-global.yml.patch").write_text("proxies:\n  velocity:\n    enabled: true\n    online-mode: true\n    secret: '"+secret+"'\n")
    print("Rendered configs in",out)
    if not args.deploy: return
    for name in NAMES:
        node=nodes[name]
        target=ssh_user+"@"+node.get("ssh_host",node["host"])
        ssh=["ssh","-p",str(ssh_port),"-o","BatchMode=yes",target]
        scp=["scp","-P",str(ssh_port),"-o","BatchMode=yes"]
        # Stage files in /tmp and install as root; avoid requiring direct root SCP.
        if name=="proxy":
            for filename in ("velocity.toml","forwarding.secret"):
                run(scp+[str(out/"proxy"/filename),target+":/tmp/dune-"+filename])
            remote=("sudo install -m 600 /tmp/dune-forwarding.secret "+ROOT+"/proxy/forwarding.secret && "
                    "sudo install -m 644 /tmp/dune-velocity.toml "+ROOT+"/proxy/velocity.toml && "
                    "rm -f /tmp/dune-forwarding.secret /tmp/dune-velocity.toml && sudo systemctl restart dune-proxy")
        else:
            for filename in ("server.properties.patch","paper-global.yml.patch"):
                run(scp+[str(out/name/filename),target+":/tmp/dune-"+filename])
            # Edit existing server.properties and Paper YAML; do not overwrite full Paper config.
            # A Python helper is transferred to make YAML block replacement deterministic.
            helper=pathlib.Path(__file__).with_name("apply-node-local.py")
            run(scp+[str(helper),target+":/tmp/dune-apply-node-local.py"])
            remote=("sudo python3 /tmp/dune-apply-node-local.py "+ROOT+"/servers/"+name+
                    " /tmp/dune-server.properties.patch /tmp/dune-paper-global.yml.patch && "
                    "rm -f /tmp/dune-server.properties.patch /tmp/dune-paper-global.yml.patch /tmp/dune-apply-node-local.py && "
                    "sudo systemctl restart dune-"+name)
        run(ssh+[remote])
        print("Updated",name)
if __name__=="__main__":
    try: main()
    except (ValueError,KeyError,subprocess.CalledProcessError) as e:
        print("ERROR:",e,file=sys.stderr);sys.exit(1)
