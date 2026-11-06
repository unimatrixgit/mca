"""Read-only installed artifact inventory for native task326. Never reads database/plugin configuration."""
import argparse,pathlib,json,hashlib,zipfile,re,subprocess,urllib.parse
p=argparse.ArgumentParser();p.add_argument('--host-role',choices=['planets','gateway'],required=True);p.add_argument('--out',required=True,type=pathlib.Path);args=p.parse_args()
nodes=({name:pathlib.Path('/opt/dunerealms/servers')/name for name in ['arrakis','caladan','giedi_prime','kaitain','sigma_draconis_iv']}if args.host_role=='planets'else{'lobby':pathlib.Path('/opt/dunerealms/lobby'),'proxy':pathlib.Path('/opt/dunerealms/proxy')})
def sha(file):return hashlib.sha256(file.read_bytes()).hexdigest()
def field(text,key):
 match=re.search(r'^'+re.escape(key)+r':\s*([^\r\n]+)',text,re.M);return match[1].strip().strip('"\'')if match else None
units={}
for file in pathlib.Path('/etc/systemd/system').glob('dune-*.service'):
 for line in file.read_text().splitlines():
  if line.startswith('WorkingDirectory='):units[str(pathlib.Path(line.split('=',1)[1]).resolve())]=file.name
report={'hostRole':args.host_role,'nodes':{}}
for name,root in nodes.items():
 if not(root/'plugins').is_dir():raise RuntimeError('Expected current node missing: '+name)
 node={'plugins':[],'engineJars':[]};unit=units.get(str(root.resolve()))
 if unit:node['service']={'unit':unit,'state':subprocess.run(['systemctl','is-active',unit],text=True,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL).stdout.strip()}
 for file in sorted((root/'plugins').glob('*.jar')):
  with zipfile.ZipFile(file)as archive:
   names=archive.namelist();native='plugin.yml'if'plugin.yml'in names else'paper-plugin.yml'if'paper-plugin.yml'in names else None;text=archive.read(native).decode('utf-8-sig')if native else''
   descriptor={'name':field(text,'name'),'version':field(text,'version'),'apiVersion':field(text,'api-version')}
   if not text and'velocity-plugin.json'in names:
    native=json.loads(archive.read('velocity-plugin.json'));descriptor={'name':native.get('name')or native.get('id'),'version':native.get('version'),'apiVersion':None}
   classes={entry:hashlib.sha256(archive.read(entry)).hexdigest()for entry in names if entry.endswith(('/DuneCore.class','/NativeMenus.class','/DuneMenus.class','/ConfigurableGui.class','/JobsScreens.class','/StaffMenuBridge.class','/SunWearPolicy.class','/DeliveryStore.class','/GraphicalMenu.class'))}
   node['plugins'].append({'file':file.name,'sha256':sha(file),'descriptor':descriptor,'classes':classes})
 for file in sorted(root.glob('*.jar')):node['engineJars'].append({'file':file.name,'sha256':sha(file)})
 properties=root/'server.properties'
 if properties.is_file():
  # Whitelist only public pack settings; never emit rcon, proxy or database credentials.
  values={line.split('=',1)[0]:line.split('=',1)[1]for line in properties.read_text().splitlines()if '='in line and line.split('=',1)[0]in ['resource-pack','resource-pack-sha1','require-resource-pack']}
  url=values.get('resource-pack','').replace(r'\:',':').replace(r'\=','=').replace(r'\/','/');parsed=urllib.parse.urlsplit(url)
  if url and not(parsed.scheme=='https'and parsed.hostname=='www.dunerealms.com'and parsed.path.endswith('.zip')and not parsed.query and not parsed.username and not parsed.password):values['resource-pack']='[unrecognised URL withheld]'
  elif url:values['resource-pack']=url
  node['publicResourcePack']=values
 packs=[]
 for plugin in(root/'plugins').iterdir():
  if plugin.is_dir()and'geyser'in plugin.name.lower()and(plugin/'packs').is_dir():
   for file in sorted((plugin/'packs').iterdir()):
    if file.is_file()and file.suffix.lower()in['.zip','.mcpack']:packs.append({'file':file.name,'sha256':sha(file)})
 if packs:node['bedrockPacks']=packs
 report['nodes'][name]=node
args.out.write_text(json.dumps(report,indent=2));print(json.dumps({'hostRole':args.host_role,'nodes':{name:{'plugins':len(row['plugins']),'service':row.get('service'),'packSha1':row.get('publicResourcePack',{}).get('resource-pack-sha1'),'bedrockPacks':row.get('bedrockPacks',[])}for name,row in report['nodes'].items()},'configurationRead':'only whitelisted public pack properties','productionMutation':False}))
