#!/usr/bin/env python3
"""Offline preflight for the seven-VM network inventory and generated configs."""
import argparse, json, ipaddress, pathlib, subprocess, sys, tempfile, zipfile
NAMES=("proxy","spacing-guild","arrakis","giedi-prime","kaitain","caladan","sigma-draconis-iv")
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--config",default="network/network.example.json")
    p.add_argument("--check-worlds",action="store_true")
    args=p.parse_args()
    c=json.loads(pathlib.Path(args.config).read_text())
    nodes=c["nodes"]
    assert set(nodes)==set(NAMES), "Expected exactly seven nodes"
    assert len({nodes[n]["host"] for n in NAMES})==7, "Duplicate node private IPs"
    for n in NAMES:
        ip=ipaddress.ip_address(nodes[n]["host"])
        assert ip.is_private, f"{n} backend address should be private"
        assert isinstance(nodes[n]["port"],int) and 1<=nodes[n]["port"]<=65535
    assert nodes["proxy"]["port"]==25565, "Velocity port must be 25565"
    print("PASS: seven unique private node addresses and valid ports")
    if args.check_worlds:
        with tempfile.TemporaryDirectory() as d:
            subprocess.run([sys.executable,"network/generate-world-files.py","--output",d],check=True)
            for n in NAMES[1:]:
                path=pathlib.Path(d)/(n+"-world-config.zip")
                with zipfile.ZipFile(path) as z:
                    assert z.testzip() is None
                    if n!="spacing-guild":
                        data=z.read("plugins/DuneTerrain/config.yml").decode()
                        assert "  planet: "+n.replace("-","_") in data
                    else:
                        assert "plugins/DuneTerrain/config.yml" not in z.namelist()
            print("PASS: six valid world-config archives (five planets + lobby placeholder)")
if __name__=="__main__":
    try:main()
    except (AssertionError,KeyError,ValueError) as e:
        print("FAIL:",e,file=sys.stderr);sys.exit(1)
