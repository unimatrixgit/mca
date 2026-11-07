"""Independent audit of stopped/snapshotted saved Anvil files against the grand Atreides plan.
QA dependencies: nbtlib 2.0.4 and numpy. Never writes world files.
"""
import argparse,gzip,io,json,struct,zlib
from pathlib import Path
import nbtlib
import numpy as np

PALETTE=['air','sandstone','smooth_sandstone','cut_sandstone','chiseled_sandstone',
         'yellow_terracotta','dark_prismarine','green_terracotta','tinted_glass',
         'sea_lantern','sand','polished_deepslate','red_sandstone','brown_terracotta','calcite','waxed_cut_copper','stripped_dark_oak_log','dark_oak_planks','orange_terracotta','red_concrete']
def chunk(world,cx,cz):
    p=world/'region'/f'r.{cx//32}.{cz//32}.mca'
    with p.open('rb') as f:
        f.seek(4*((cx%32)+(cz%32)*32));location=int.from_bytes(f.read(4),'big')
        if not location:raise AssertionError(f'Missing chunk {cx},{cz}')
        f.seek((location>>8)*4096);n=int.from_bytes(f.read(4),'big');compression=f.read(1)[0];raw=f.read(n-1)
    raw={1:gzip.decompress,2:zlib.decompress,3:lambda b:b}[compression](raw)
    return nbtlib.File.parse(io.BytesIO(raw))
def section(section):
    states=section['block_states'];palette=[str(p['Name']).removeprefix('minecraft:') for p in states['palette']]
    if len(palette)==1:return np.full((16,16,16),PALETTE.index(palette[0]) if palette[0] in PALETTE else 255,dtype=np.uint8)
    bits=max(4,(len(palette)-1).bit_length());per=64//bits
    raw=np.asarray([int(n)&((1<<64)-1) for n in states['data']],dtype=np.uint64)
    ids=np.arange(4096,dtype=np.uint64)
    values=((raw[ids//per]>>((ids%per)*bits))&((1<<bits)-1)).astype(np.int32)
    mapped=np.asarray([PALETTE.index(m) if m in PALETTE else 255 for m in palette],dtype=np.uint8)
    return mapped[values].reshape((16,16,16))
def audit(world,plan):
    raw=gzip.decompress(plan.read_bytes());magic,w,h,l=struct.unpack('>iiii',raw[:16]);assert magic==0x44524131
    expected=np.frombuffer(raw,dtype=np.uint8,count=w*h*l,offset=16).reshape((h,l,w))
    actual=np.full((h,l,w),255,dtype=np.uint8)
    ox,oy,oz=-486,68,-184
    for cx in range(ox//16,(ox+w-1)//16+1):
        for cz in range(oz//16,(oz+l-1)//16+1):
            c=chunk(world,cx,cz)
            for sec in c['sections']:
                sy=int(sec['Y'])*16
                if sy+16<=oy or sy>=oy+h:continue
                data=section(sec)
                xa,xb=max(ox,cx*16),min(ox+w,cx*16+16)
                za,zb=max(oz,cz*16),min(oz+l,cz*16+16)
                ya,yb=max(oy,sy),min(oy+h,sy+16)
                actual[ya-oy:yb-oy,za-oz:zb-oz,xa-ox:xb-ox]=data[ya-sy:yb-sy,za-cz*16:zb-cz*16,xa-cx*16:xb-cx*16]
    mismatch=int(np.count_nonzero(actual!=expected));assert mismatch==0,f'{mismatch} saved blocks differ'
    level=nbtlib.load(world/'level.dat')['Data'];spawn=[int(level['Spawn'+a]) for a in 'XYZ'];assert spawn==[0,72,0],spawn
    offset=16+w*h*l;n=struct.unpack('>i',raw[offset:offset+4])[0]
    for i in range(n):
        x,y,z=struct.unpack('>iii',raw[offset+4+i*12:offset+16+i*12])
        assert actual[y,z,x]==actual[y+1,z,x]==0 and actual[y-1,z,x]!=0,(x,y,z)
    return {'savedBlocksCompared':w*h*l,'mismatches':mismatch,'nonAir':int(np.count_nonzero(actual)),
            'savedSafeDestinations':n,'spawn':spawn,'worldUUIDBytes':(world/'uid.dat').read_bytes().hex()}
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('world',type=Path);p.add_argument('plan',type=Path);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    result=audit(a.world,a.plan);a.report.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
