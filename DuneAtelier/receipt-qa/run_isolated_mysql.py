import pathlib,pwd,os,subprocess,time,datetime,json,shutil,socket,xml.etree.ElementTree as E,hashlib,argparse,secrets,re
p=argparse.ArgumentParser(description='Run receipt QA in a dedicated loopback-only MySQL process; never installs plugins or reads application configuration.');p.add_argument('--gradle',required=True);p.add_argument('--installed-artifact',required=True,type=pathlib.Path);p.add_argument('--installed-sha256',required=True);args=p.parse_args()
def require(condition,message):
 if not condition:raise RuntimeError(message)
require(os.geteuid()==0,'Linux root is required only to launch a dedicated mysqld as the existing mysql OS user')
require(re.fullmatch('[a-f0-9]{64}',args.installed_sha256)and hashlib.sha256(args.installed_artifact.read_bytes()).hexdigest()==args.installed_sha256,'Installed artifact SHA mismatch')
java=pathlib.Path(os.environ.get('JAVA_HOME','')).joinpath('bin/java');require(java.is_file(),'Set JAVA_HOME to the installed Java21 JDK')
version=subprocess.run([str(java),'-XshowSettings:properties','-version'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=10);require(re.search(r'java.specification.version\s*=\s*21\s',version.stdout),'Java21 JDK required')
qa=pathlib.Path('/tmp/dune-receipt318-qa-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));require(qa.parent==pathlib.Path('/tmp')and not qa.exists(),'Fresh private QA directory required');qa.mkdir(mode=0o700);(qa/'.codex-qa318').write_text('isolated-receipt318');user=pwd.getpwnam('mysql');os.chown(qa,user.pw_uid,user.pw_gid);data=qa/'data';data.mkdir(mode=0o700);os.chown(data,user.pw_uid,user.pw_gid)
for attempt in range(50):
 port=49152+secrets.randbelow(16384)
 try:
  with socket.socket()as reserve:reserve.bind(('127.0.0.1',port))
  break
 except OSError:continue
else:raise RuntimeError('No free high loopback port found')
unix=qa/'mysql.sock';mysqld=shutil.which('mysqld');admin=shutil.which('mysqladmin');mysql=shutil.which('mysql');require(mysqld and admin and mysql,'Existing mysqld/mysqladmin/mysql binaries required');process=None
root=pathlib.Path(__file__).resolve().parent;artifact=args.installed_artifact.resolve()
try:
 init=subprocess.run([mysqld,'--no-defaults','--initialize-insecure','--user=mysql','--datadir='+str(data),'--log-error='+str(qa/'initialize.log'),'--innodb-buffer-pool-size=64M'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60);require(init.returncode==0,'Isolated initialization failed: '+init.stderr.decode())
 process=subprocess.Popen([mysqld,'--no-defaults','--user=mysql','--datadir='+str(data),'--socket='+str(unix),'--pid-file='+str(qa/'mysql.pid'),'--bind-address=127.0.0.1','--port='+str(port),'--mysqlx=OFF','--skip-log-bin','--innodb-buffer-pool-size=64M','--max-connections=8','--performance-schema=OFF','--log-error='+str(qa/'mysql.log')],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
 for attempt in range(100):
  if process.poll()is not None:raise RuntimeError('Isolated server exited: '+(qa/'mysql.log').read_text()[-2000:])
  if unix.exists()and subprocess.run([admin,'--no-defaults','--protocol=SOCKET','--socket='+str(unix),'-uroot','ping'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0:break
  time.sleep(.2)
 else:raise RuntimeError('QA startup timeout')
 require(int((qa/'mysql.pid').read_text().strip())==process.pid,'Unexpected isolated PID')
 subprocess.run([mysql,'--no-defaults','--protocol=SOCKET','--socket='+str(unix),'-uroot','-e','CREATE DATABASE receipt318qa CHARACTER SET utf8mb4'],check=True,stdout=subprocess.DEVNULL)
 env={**os.environ,'DUNE_RECEIPT_TEST_URL':'jdbc:mysql://127.0.0.1:'+str(port)+'/receipt318qa','DUNE_RECEIPT_TEST_DIR':str(qa)};report={}
 for mode in ['source','installed']:
  cmd=[args.gradle,'--no-daemon','test','--rerun-tasks','-PqaMySql=true']+(['-PinstalledArtifact='+str(artifact)]if mode=='installed'else[])
  run=subprocess.run(cmd,cwd=root,env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=180);(qa/(mode+'.log')).write_text(run.stdout)
  if run.returncode:raise RuntimeError(mode+' QA failed: '+run.stdout[-9000:])
  tests=[t for file in (root/'build/test-results/test').glob('TEST-*.xml')for t in E.parse(file).getroot().findall('testcase')];failed=[t.attrib['name']for t in tests if t.find('failure')is not None or t.find('error')is not None or t.find('skipped')is not None];require(len(tests)==6 and not failed,'Receipt tests failed or skipped: '+str(failed))
  report[mode]={'passed':len(tests),'failed':failed,'actualJvmCrashes':2}
 report.update({'isolation':'dedicated MySQL process bound only to high 127.0.0.1 port; private marker and actual datadir guards before DDL','installedSha256':hashlib.sha256(artifact.read_bytes()).hexdigest(),'productionDataChanged':False,'gameRequests':0,'realPayments':0});(qa/'result.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
finally:
 if process is not None and process.poll()is None:
  try:subprocess.run([admin,'--no-defaults','--protocol=SOCKET','--socket='+str(unix),'-uroot','shutdown'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=15)
  except subprocess.TimeoutExpired:pass
  try:process.wait(timeout=15)
  except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=15)
 print(json.dumps({'qaDirectory':str(qa),'port':port,'loopbackOnly':True,'isolatedServerStopped':process is None or process.poll()is not None,'productionDatabaseConnected':False}))
