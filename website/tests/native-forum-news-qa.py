import pathlib,subprocess,json,os,hashlib
root=pathlib.Path('/home/dunerealms/apps/integrations/mybb');qa=pathlib.Path('/tmp/forum372-native-qa');qa.mkdir(mode=0o700,exist_ok=True)
assert not (qa/'state.json').exists(),'Use a fresh isolated fixture directory'
init=(root/'inc/init.php').read_text();needle="$db->type = $config['database']['type'];";assert init.count(needle)==1
(qa/'init.php').write_text(init.replace(needle,needle+"\nrequire '/tmp/forum372-shadow.php';"))
glob=(root/'global.php').read_text();needle="require_once $working_dir.'/inc/init.php';";assert glob.count(needle)==1
(qa/'global.php').write_text(glob.replace(needle,"define('MYBB_ROOT','"+str(root)+"/');require_once '/tmp/forum372-native-qa/init.php';"))
source=pathlib.Path('/tmp/forum-news372-candidate.php').read_text();assert source.count("require 'global.php';")==1
# Only bootstrap/DDL are redirected. Adapter logic and native PostDataHandler are unchanged.
(qa/'adapter.php').write_text(source.replace("require 'global.php';","require '/tmp/forum372-native-qa/global.php';").replace('CREATE TABLE IF NOT EXISTS','CREATE TEMPORARY TABLE IF NOT EXISTS'))
id='8b10b073-96d8-4827-a372-111111111111';post={'id':id,'status':'published','title':'Isolated native QA announcement','body':'Original isolated article body','category':'news','published_at':1793900000000,'author_name':'QA Author'}
def run(payload,reply=False):
 payload=dict(payload);payload['revision']=hashlib.sha256(json.dumps(payload,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()
 result=subprocess.run(['php',str(qa/'adapter.php')],input=json.dumps(payload),text=True,capture_output=True,env={**os.environ,'ADD_REPLY':'1' if reply else '0'})
 if result.returncode:raise RuntimeError('Native fixture failed: '+str(result.returncode)+' '+result.stdout[:300]+' '+result.stderr[:600])
 output=json.loads(result.stdout);assert output['revision']==payload['revision'];state=json.loads((qa/'state.json').read_text());return output,state
initial,state=run(post);tid=initial['thread_id'];pid=state['threads'][0]['firstpost'];date=state['posts'][0]['dateline'];assert len(state['threads'])==len(state['posts'])==1
post.update(title='Corrected isolated announcement',body='Corrected article body',category='maintenance')
edited,state=run(post,True);assert edited['thread_id']==tid and len(state['posts'])==2 and len(state['threads'])==1
assert state['posts'][0]['pid']==pid and state['posts'][0]['dateline']==date and 'Corrected article body' in state['posts'][0]['message']
reply=next(p for p in state['posts'] if str(p['pid'])=='100');reply_bytes=json.dumps(reply,sort_keys=True)
archived,state=run({'id':id,'status':'archived'});assert archived['state']=='archived' and archived['thread_id']==tid and len(state['posts'])==2
assert state['threads'][0]['subject'].startswith('[Archived] ') and 'Archived announcement' in state['posts'][0]['message']
assert json.dumps(next(p for p in state['posts'] if str(p['pid'])=='100'),sort_keys=True)==reply_bytes
again,state=run({'id':id,'status':'archived'});assert len(state['threads'])==1 and len(state['posts'])==2 and again==archived
restored,state=run(post);assert restored['state']=='sent' and restored['thread_id']==tid and not state['threads'][0]['subject'].startswith('[Archived] ')
assert json.dumps(next(p for p in state['posts'] if str(p['pid'])=='100'),sort_keys=True)==reply_bytes
assert state['posts'][0]['dateline']==date and state['posts'][0]['pid']==pid
baseline=json.loads(json.dumps(state));denials=[]
for field,value in [('uid','7'),('fid','99'),('visible','0'),('firstpost','100')]:
 changed=json.loads(json.dumps(baseline));changed['threads'][0][field]=value;(qa/'state.json').write_text(json.dumps(changed))
 payload={**post,'body':'Must not be applied'};payload['revision']=hashlib.sha256(json.dumps(payload,separators=(',',':')).encode()).hexdigest()
 result=subprocess.run(['php',str(qa/'adapter.php')],input=json.dumps(payload),text=True,capture_output=True,env={**os.environ,'ADD_REPLY':'0'})
 assert result.returncode==5,(field,result.returncode)
 final=json.loads((qa/'state.json').read_text());assert final['posts']==changed['posts'],field
 denials.append(field)
(qa/'state.json').write_text(json.dumps(baseline))
print(json.dumps({'nativePostDataHandler':True,'temporaryTablesOnly':True,'createEditArchiveReplayRepublish':True,'topicIdentityPreserved':True,'replyByteForBytePreserved':True,'originalPostDatePreserved':True,'ownershipForumVisibilityFirstPostDenials':denials}))
