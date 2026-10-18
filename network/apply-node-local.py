#!/usr/bin/env python3
"""Update node config while preserving unrelated Paper configuration."""
import pathlib,sys,re
base=pathlib.Path(sys.argv[1])
props=base/"server.properties"
updates=dict(line.strip().split("=",1) for line in pathlib.Path(sys.argv[2]).read_text().splitlines() if "=" in line)
text=props.read_text()
for k,v in updates.items():
    if re.search(r"^"+re.escape(k)+r"=.*$",text,re.M): text=re.sub(r"^"+re.escape(k)+r"=.*$",lambda _:k+"="+v,text,flags=re.M)
    else: text+="\n"+k+"="+v+"\n"
props.write_text(text)
paper=base/"config/paper-global.yml"
secret=pathlib.Path(sys.argv[3]).read_text().split("secret: ",1)[1].strip().strip("'")
s=paper.read_text()
# Replace only proxies.velocity block when it exists; preserve rest of global config.
block="proxies:\n  velocity:\n    enabled: true\n    online-mode: true\n    secret: '"+secret+"'\n"
if re.search(r"(?m)^proxies:\s*$",s):
    s=re.sub(r"(?ms)^proxies:\n(?:(?!^[^ #\s]).*\n)*",block,s,count=1)
else: s+="\n"+block
paper.write_text(s)
