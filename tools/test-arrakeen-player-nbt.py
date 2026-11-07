"""Independent nbtlib fixture checks for position-only world-reset migration."""
import gzip,importlib.util,io
from pathlib import Path
import nbtlib as n
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('arrakeen-player-nbt.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
original=n.File({'Inventory':n.List[n.Compound]([n.Compound({'id':n.String('minecraft:diamond_sword'),
    'count':n.Int(1),'custom':n.Compound({'name':n.String('Test — Ω'),'bytes':n.ByteArray([1,-2,3]),'longs':n.LongArray([-1,2]),'ints':n.IntArray([-1,2])})})]),
    'EnderItems':n.List[n.Compound]([]),'XpLevel':n.Int(27),'Health':n.Float(18.5),
    'Pos':n.List[n.Double]([100,2,-400]),'SpawnX':n.Int(500),'SpawnY':n.Int(72),'SpawnZ':n.Int(200),
    'Dimension':n.String('minecraft:overworld'),'WorldUUIDMost':n.Long(0),'WorldUUIDLeast':n.Long(1)})
b=io.BytesIO();original.write(b);raw=b.getvalue();uid=bytes.fromhex('c4a0d76dad204e0ebb9d79a0753dbb09')
new=n.File.parse(io.BytesIO(gzip.decompress(m.relocate(gzip.compress(raw),uid))))
for key in ('Inventory','EnderItems','XpLevel','Health'):assert new[key]==original[key],key
assert [float(x) for x in new['Pos']]==[.5,72,.5]
assert new['WorldUUIDMost']==n.Long(int.from_bytes(uid[:8],'big',signed=True))
assert new['WorldUUIDLeast']==n.Long(int.from_bytes(uid[8:],'big',signed=True))
assert all(k not in new for k in ('SpawnX','SpawnY','SpawnZ'))
assert [float(x) for x in new['Motion']]==[0,0,0]
assert [float(x) for x in new['Rotation']]==[90,0]
try:m.relocate(gzip.compress(raw[:-1]),uid)
except ValueError:pass
else:raise AssertionError('Truncated input accepted')
print('Independent player migration checks PASS: inventory, ender chest, XP, health, arrays, UTF8, UUID, arrival and malformed input')
