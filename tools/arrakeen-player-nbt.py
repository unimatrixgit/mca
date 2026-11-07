"""Position-only migration for a reset world. Preserve raw strings and all unrelated NBT payloads.
No server or network access. Caller must stop the server and keep an original backup.
"""
import gzip, io, struct

SIZES={1:1,2:2,3:4,4:8,5:4,6:8}
def exact(stream,n):
    data=stream.read(n)
    if len(data)!=n:raise ValueError('Truncated NBT')
    return data
def count(stream):
    n=struct.unpack('>i',exact(stream,4))[0]
    if not 0<=n<=20_000_000:raise ValueError('Invalid NBT count')
    return n
def string(stream):
    n=struct.unpack('>H',exact(stream,2))[0]
    return exact(stream,n)
def payload(stream,t):
    if t in SIZES:return exact(stream,SIZES[t])
    if t==8:return string(stream)
    if t in (7,11,12):
        n=count(stream);return n,exact(stream,n*{7:1,11:4,12:8}[t])
    if t==9:
        element=exact(stream,1)[0];n=count(stream)
        return element,[payload(stream,element) for _ in range(n)]
    if t==10:
        result=[]
        while True:
            kind=exact(stream,1)[0]
            if kind==0:return result
            name=string(stream);result.append((name,kind,payload(stream,kind)))
    raise ValueError('Unknown NBT type '+str(t))
def text(b):return struct.pack('>H',len(b))+b
def emit(t,v):
    if t in SIZES:return v
    if t==8:return text(v)
    if t in (7,11,12):return struct.pack('>i',v[0])+v[1]
    if t==9:return bytes([v[0]])+struct.pack('>i',len(v[1]))+b''.join(emit(v[0],p) for p in v[1])
    if t==10:return b''.join(bytes([kind])+text(name)+emit(kind,p) for name,kind,p in v)+b'\0'
    raise ValueError(t)
def decode(raw):
    stream=io.BytesIO(raw)
    if exact(stream,1)!=b'\x0a':raise ValueError('Expected root compound')
    name=string(stream);tree=payload(stream,10)
    if stream.read():raise ValueError('Trailing NBT')
    if b'\x0a'+text(name)+emit(10,tree)!=raw:raise ValueError('NBT round-trip mismatch')
    return name,tree
def relocate(compressed,world_uid):
    name,tree=decode(gzip.decompress(compressed))
    removed={b'SpawnX',b'SpawnY',b'SpawnZ',b'SpawnDimension',b'SpawnForced',b'SpawnAngle'}
    updates={
        b'Pos':(9,(6,[struct.pack('>d',n) for n in (.5,72.,.5)])),
        b'Motion':(9,(6,[struct.pack('>d',0.)]*3)),
        b'Rotation':(9,(5,[struct.pack('>f',n) for n in (90.,0.)])),
        b'FallDistance':(5,struct.pack('>f',0.)),b'Dimension':(8,b'minecraft:overworld'),
        b'WorldUUIDMost':(4,world_uid[:8]),b'WorldUUIDLeast':(4,world_uid[8:])}
    assert len(world_uid)==16
    untouched=[e for e in tree if e[0] not in updates and e[0] not in removed]
    result=untouched+[(k,t,v) for k,(t,v) in updates.items()]
    raw=b'\x0a'+text(name)+emit(10,result)
    _,check=decode(raw)
    assert [e for e in check if e[0] not in updates and e[0] not in removed]==untouched
    return gzip.compress(raw,mtime=0)
