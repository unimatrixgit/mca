"""Private authoritative quest configuration/history API. No gameplay or currency fabrication."""
import re
import uuid

class Conflict(Exception):
    pass

def player_uuid(value):
    if not isinstance(value, str) or not re.fullmatch(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}', value):
        raise ValueError('Invalid player UUID')
    return str(uuid.UUID(value))

def definition_input(data):
    fields={'key','title','description','enabled','hidden','intervalSeconds','surviveSeconds','rewardDunemarks','version','actor'}
    if not isinstance(data,dict) or set(data)-fields:
        raise ValueError('Unknown quest configuration field')
    if not isinstance(data.get('key'),str) or not re.fullmatch(r'[a-z0-9][a-z0-9-]{2,63}',data['key']):
        raise ValueError('Use a lowercase quest key of 3–64 characters')
    for field,low,high in [('title',3,120),('description',10,2000)]:
        value=data.get(field)
        if not isinstance(value,str) or not low<=len(value.strip())<=high or re.search(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]',value):
            raise ValueError('Invalid quest '+field)
    for field,low,high in [('intervalSeconds',3600,604800),('surviveSeconds',60,1800),('version',0,2147483646)]:
        if type(data.get(field)) is not int or not low<=data[field]<=high:
            raise ValueError('Quest '+field+' is outside its allowed bounds')
    if type(data.get('enabled')) is not bool or type(data.get('hidden')) is not bool:
        raise ValueError('Explicit enabled and hidden flags are required')
    if type(data.get('rewardDunemarks',0)) is not int or data.get('rewardDunemarks',0)!=0:
        raise ValueError('No additional Dunemark reward is authorized')
    if not isinstance(data.get('actor'),str) or not re.fullmatch(r'[A-Za-z0-9_]{3,32}',data['actor']):
        raise ValueError('Authenticated staff actor is required')
    return {**data,'title':data['title'].strip(),'description':data['description'].strip(),'rewardDunemarks':0}

def clock(cursor):
    cursor.execute('SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED) AS now')
    return int(cursor.fetchone()['now'])

def definitions(connect):
    with connect() as connection:
        with connection.cursor() as c:
            c.execute('SELECT quest_key AS `key`,title,description,engine,enabled,hidden,interval_seconds AS intervalSeconds,survive_seconds AS surviveSeconds,reward_dunemarks AS rewardDunemarks,version,next_run_at AS nextRunAt,updated_at AS updatedAt,updated_by AS updatedBy FROM dune_quest_definitions ORDER BY quest_key LIMIT 100')
            rows=c.fetchall()
            for row in rows:
                row['enabled']=bool(row['enabled']);row['hidden']=bool(row['hidden'])
            return {'definitions':rows,'cap':2,'rewardDunemarks':0}

def events(connect):
    with connect() as connection:
        with connection.cursor() as c:
            c.execute('SELECT id,definition_key AS questKey,definition_version AS definitionVersion,player_uuid AS playerUuid,server_id AS serverId,status,assigned_at AS assignedAt,deadline_at AS deadlineAt,completed_at AS completedAt,worm_uuid AS wormUuid,reason,title_snapshot AS title,description_snapshot AS description FROM dune_quest_events ORDER BY assigned_at DESC,id DESC LIMIT 100')
            return {'events':c.fetchall(),'rewardDunemarks':0}

def player(connect,value):
    value=player_uuid(value)
    with connect() as connection:
        with connection.cursor() as c:
            # No unassigned definition, skipped attempt, another UUID or private worm/location data leaves this query.
            c.execute("SELECT id,definition_key AS questKey,status,assigned_at AS assignedAt,deadline_at AS deadlineAt,completed_at AS completedAt,title_snapshot AS title,description_snapshot AS description FROM dune_quest_events WHERE player_uuid=%s AND deadline_at IS NOT NULL AND status IN ('assigned','completed','failed','discovered') ORDER BY assigned_at DESC,id DESC LIMIT 50",(value,))
            return {'events':c.fetchall(),'rewardDunemarks':0}

def update(connect,body):
    data=definition_input(body)
    with connect() as connection:
        try:
            with connection.cursor() as c:
                c.execute('SELECT version,enabled,interval_seconds,next_run_at FROM dune_quest_definitions WHERE quest_key=%s FOR UPDATE',(data['key'],))
                old=c.fetchone();now=clock(c)
                if old is None:
                    if data['version']!=0:raise Conflict('Definition no longer exists. Refresh first.')
                    c.execute('SELECT COUNT(*) AS total FROM dune_quest_definitions')
                    if int(c.fetchone()['total'])>=100:raise Conflict('Quest definition limit reached')
                    version=1
                    c.execute("INSERT INTO dune_quest_definitions(quest_key,title,description,engine,enabled,hidden,interval_seconds,survive_seconds,reward_dunemarks,version,next_run_at,updated_at,updated_by) VALUES(%s,%s,%s,'worm-survival',%s,%s,%s,%s,0,1,%s,%s,%s)",(data['key'],data['title'],data['description'],data['enabled'],data['hidden'],data['intervalSeconds'],data['surviveSeconds'],now+data['intervalSeconds']*1000,now,data['actor']))
                else:
                    if int(old['version'])!=data['version']:raise Conflict('Quest configuration changed. Refresh before saving.')
                    version=data['version']+1
                    next_run=now+data['intervalSeconds']*1000 if (not old['enabled'] and data['enabled']) or old['interval_seconds']!=data['intervalSeconds'] else old['next_run_at']
                    c.execute('UPDATE dune_quest_definitions SET title=%s,description=%s,enabled=%s,hidden=%s,interval_seconds=%s,survive_seconds=%s,reward_dunemarks=0,version=version+1,next_run_at=%s,updated_at=%s,updated_by=%s WHERE quest_key=%s AND version=%s',(data['title'],data['description'],data['enabled'],data['hidden'],data['intervalSeconds'],data['surviveSeconds'],next_run,now,data['actor'],data['key'],data['version']))
                    if c.rowcount!=1:raise Conflict('Quest configuration changed during save.')
            connection.commit()
            return {'ok':True,'key':data['key'],'version':version,'cap':2,'rewardDunemarks':0}
        except Exception:
            connection.rollback();raise

def dispatch(connect,method,path,args=None,body=None):
    args=args or {}
    try:
        if method=='GET' and path=='/quests/definitions' and not args:return 200,definitions(connect)
        if method=='GET' and path=='/quests/events' and not args:return 200,events(connect)
        if method=='GET' and path=='/quests/player' and set(args)=={'uuid'} and len(args['uuid'])==1:return 200,player(connect,args['uuid'][0])
        if method=='POST' and path=='/quests/definitions' and not args:return 200,update(connect,body)
        return 404,{'message':'Unknown quest operation'}
    except ValueError as error:return 400,{'message':str(error)}
    except Conflict as error:return 409,{'message':str(error)}

