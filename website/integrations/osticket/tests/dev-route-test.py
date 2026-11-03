"""Run with PHP CLI + a private MySQL config path; never uses native ticket/email services.
Every SQL table is rewritten to a random fixture namespace; real GET_LOCK serializes concurrency.
"""
import concurrent.futures, json, os, pathlib, secrets, shutil, subprocess, sys, tempfile

source = pathlib.Path(__file__).resolve().parents[1]
stage = pathlib.Path(tempfile.mkdtemp(prefix='dune-dev344-'))
env = {**os.environ, 'DUNE_TEST_PREFIX': 'dune_test344_'+secrets.token_hex(6)+'_', 'DUNE_TEST_DB_CONFIG': sys.argv[1]}
shutil.copy2(source/'tests/dev-route-fixture.php', stage/'boot.php')
shutil.copy2(source/'dev-policy.php', stage/'dev-policy.php')
route = (source/'dev-tasks.php').read_text()
for old, new in [("require 'main.inc.php';", "require __DIR__.'/boot.php';"), ("new PDO(", "new FixturePDO("), ("file_get_contents('/home/dunerealms/.config/integrations.json')", "file_get_contents(__DIR__.'/fixture-config.json')")]:
    assert route.count(old)==1, 'Route dependency boundary changed: '+old
    route=route.replace(old,new)
(stage/'dev-tasks.php').write_text(route)
(stage/'fixture-config.json').write_text(json.dumps(dict(host='fixture', database='fixture', username='fixture', password='unused')))
(stage/'dev-view.php').write_text('<?php // Rendering is checked separately against the actual served UI.\n')
identities={
    'owner':dict(id='owner',username='unimatrix999',ranks=['owners'],permissions=[]),
    'dev':dict(id='dev',username='dev',ranks=['web_dev'],permissions=['web.tickets.dev']),
    'vip':dict(id='vip',username='vip',ranks=['vip'],permissions=[]),
    'member':dict(id='member',username='member',ranks=['user'],permissions=[]),
}
session={'dune_dev_csrf':'a'*64,'dune_dev_submission':'b'*64}
valid=dict(title='Fixture request', type='Feature',system='Fixture only',objective='Verify actual route',current='Fixture state',expected='One native fixture ticket',acceptance='No duplicate fixture records',priority='High',privacy='1')
count=0
def check(ok, label):
    global count
    assert ok, label
    count+=1
def invoke(program='dev-tasks.php', **request):
    f=stage/(secrets.token_hex(8)+'.json'); f.write_text(json.dumps(request))
    try:
        p=subprocess.run(['php',str(stage/program),str(f)],env=env,capture_output=True,text=True,timeout=20)
        assert p.returncode==0, p.stderr
        return json.loads(p.stdout)
    finally: f.unlink()
def post(role='dev', sess=None, **fields):
    return invoke(identity=identities[role],session=sess or session,post={'csrf':(sess or session)['dune_dev_csrf'],**fields})
schemas={
 'dune_dev_tasks':'task_key VARCHAR(255) UNIQUE,ticket_id INT PRIMARY KEY,submitter_id VARCHAR(36),title VARCHAR(200),prompt_json LONGTEXT,approval VARCHAR(16),priority VARCHAR(16),task_type VARCHAR(32),approved_by VARCHAR(36) NULL,decision_note TEXT,execution_spec LONGTEXT NULL,queue_order INT DEFAULT 0,created_at DATETIME,updated_at DATETIME',
 'dune_dev_task_events':'id INT PRIMARY KEY AUTO_INCREMENT,ticket_id INT,actor_id VARCHAR(36),action VARCHAR(40),note LONGTEXT,created_at DATETIME',
 'drsupport_ticket':'ticket_id INT PRIMARY KEY AUTO_INCREMENT,user_id INT,dept_id INT,status_id INT,number VARCHAR(32)',
 'drsupport_ticket__cdata':'ticket_id INT PRIMARY KEY,subject VARCHAR(255)',
 'drsupport_help_topic':'topic_id INT,dept_id INT,team_id INT,ispublic INT,topic VARCHAR(100)',
 'drsupport_ticket_status':'id INT,state VARCHAR(10)',
}
init="<?php require __DIR__.'/boot.php';\n"
for name,columns in schemas.items():
    init+=f"$fixtureConnection->exec('CREATE TABLE '.$prefix.'{name} ({columns}) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');\n"
init+="$db=new FixturePDO;$db->query(\"INSERT INTO drsupport_help_topic VALUES(20,9,9,0,'Development task')\");$db->query(\"INSERT INTO drsupport_ticket_status VALUES(1,'open'),(3,'closed')\");"
(stage/'init.php').write_text(init)
(stage/'peek.php').write_text("<?php require __DIR__.'/boot.php';$db=new FixturePDO;echo json_encode(['tasks'=>$db->query('SELECT * FROM dune_dev_tasks ORDER BY ticket_id')->fetchAll(),'tickets'=>$db->query('SELECT * FROM drsupport_ticket ORDER BY ticket_id')->fetchAll(),'events'=>$db->query('SELECT * FROM dune_dev_task_events ORDER BY id')->fetchAll()]);")
def snapshot(): return json.loads(invoke('peek.php',identity=identities['dev'])['body'])
try:
    invoke('init.php',identity=identities['dev'])
    check(post('member',action='create')['status']==403,'ordinary member denied')
    first=post('vip',action='create',submission=session['dune_dev_submission'],**valid)
    check(not first['errors'],'valid creation')
    state=snapshot(); tid=state['tasks'][0]['ticket_id']
    check(len(state['tickets'])==len(state['tasks'])==1,'one native and registry row')
    check(state['tasks'][0]['approval']=='pending' and state['tasks'][0]['priority']=='Medium','VIP cannot autoapprove or set High priority')
    post('vip',action='create',submission=session['dune_dev_submission'],**valid)
    check(len(snapshot()['tickets'])==1,'replayed original request deduplicated')
    stale=post('vip',sess=first['session'],action='create',submission=session['dune_dev_submission'],**valid)
    check(stale['status']==400 and len(snapshot()['tickets'])==1,'rotated token rejects browser replay')
    check(post(action='approve',id=str(tid),note='A valid approval note')['status']==400,'Dev cannot approve')
    check(post(action='complete',id=str(tid),note='A valid completion note')['status']==400,'pending cannot complete')
    check(not post('owner',action='approve',id=str(tid),note='Approved fixture work')['errors'],'owner approve')
    check(snapshot()['tasks'][0]['approval']=='approved','approved state persisted')
    check(post('owner',action='deny',id=str(tid),note='Second decision forbidden')['status']==400,'decision cannot replay')
    check(not post(action='brief',id=str(tid),brief='English execution brief with acceptance and tests')['errors'],'Dev brief edit')
    check(not post(action='priority',id=str(tid),priority='Low')['errors'],'Dev priority edit')
    fail=invoke(identity=identities['dev'],session=session,nativeCloseFail=True,post=dict(csrf=session['dune_dev_csrf'],action='complete',id=str(tid),note='Native closure must fail safely'))
    check(fail['status']==400 and snapshot()['tasks'][0]['approval']=='approved' and snapshot()['tickets'][0]['status_id']==1,'native close failure keeps approved/open')
    check(not post(action='complete',id=str(tid),note='Fixture tests and evidence complete')['errors'],'completion succeeds')
    state=snapshot();check(state['tasks'][0]['approval']=='completed' and state['tickets'][0]['status_id']==3,'completed registry and closed native')
    before=len(state['events']);check(post(action='complete',id=str(tid),note='Completion replay forbidden')['status']==400 and len(snapshot()['events'])==before,'completion replay creates no event')
    concurrent_session={**session,'dune_dev_submission':'c'*64}
    def create_concurrent(_): return post('vip',sess=concurrent_session,action='create',submission='c'*64,**valid)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool: list(pool.map(create_concurrent,range(2)))
    state=snapshot();check(len(state['tasks'])==len(state['tickets'])==2,'real MySQL advisory lock deduplicates concurrent creation')
    recovery_session={**session,'dune_dev_submission':'d'*64}
    interrupted=invoke(identity=identities['vip'],session=recovery_session,crashAfterNative=True,post=dict(csrf='a'*64,action='create',submission='d'*64,**valid))
    check('interrupted' in interrupted['body'] and len(snapshot()['tickets'])==3 and len(snapshot()['tasks'])==2,'interruption leaves fixture native ticket only')
    post('vip',sess=recovery_session,action='create',submission='d'*64,**valid)
    state=snapshot();check(len(state['tasks'])==len(state['tickets'])==3,'retry recovers native ticket without duplication')
    failed_session={**session,'dune_dev_submission':'e'*64}
    failed=invoke(identity=identities['vip'],session=failed_session,nativeCreateFail=True,post=dict(csrf='a'*64,action='create',submission='e'*64,**valid))
    check(failed['status']==400 and len(snapshot()['tickets'])==3 and len(snapshot()['tasks'])==3,'native create failure makes no registry row')
    owner_session={**session,'dune_dev_submission':'f'*64}
    post('owner',sess=owner_session,action='create',submission='f'*64,**valid)
    owner_task=snapshot()['tasks'][-1]
    check(owner_task['approval']=='approved' and owner_task['priority']=='High','verified owner creation autoapproves and retains priority')
    private=invoke(method='GET',identity=identities['vip'],get={'id':str(owner_task['ticket_id'])})
    check(private['status']==404,'VIP cannot view another submitter task')
    pending=state['tasks'][1]['ticket_id']
    denied=post('owner',action='deny',id=str(pending),note='Denied fixture request as out of scope')
    state=snapshot();denied_task=next(t for t in state['tasks'] if t['ticket_id']==pending);denied_native=next(t for t in state['tickets'] if t['ticket_id']==pending)
    check(not denied['errors'] and denied_task['approval']=='denied' and denied_native['status_id']==3,'denial closes native and persists decision')
    check(post('owner',action='approve',id=str(pending),note='Cannot approve an already denied task')['status']==400,'denied task cannot be reapproved')
    check(post('vip',action='priority',id=str(owner_task['ticket_id']),priority='High')['status']==400,'VIP cannot edit priority')
    check(post('vip',action='brief',id=str(owner_task['ticket_id']),brief='A sufficiently long forbidden execution brief')['status']==400,'VIP cannot edit execution brief')
    check(post(action='priority',id=str(owner_task['ticket_id']),priority=['High'])['status']==400,'array priority rejected by route')
    check(post(action='brief',id=str(owner_task['ticket_id']),brief='é'*30001)['status']==400,'overlong Unicode brief rejected by route')
    check(not post(action='brief',id=str(owner_task['ticket_id']),brief='é'*30000)['errors'],'maximum Unicode brief accepted by route')
    print(json.dumps(dict(routeAssertions=count,realMysqlLocks=True,nativeTicketBoundary='isolated doubles; no production tickets or email')))
finally:
    cleanup="<?php require __DIR__.'/boot.php';foreach(array_reverse($fixtureTables)as$table)$fixtureConnection->exec('DROP TABLE IF EXISTS '.$prefix.$table);"
    (stage/'cleanup.php').write_text(cleanup)
    try: invoke('cleanup.php',identity=identities['dev'])
    finally: shutil.rmtree(stage)
