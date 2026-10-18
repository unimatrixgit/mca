"""Read the actual saved Anvil chunks, audit lobby content, and render a roofless plan.

Requires: pip install nbtlib pillow. Run only against a stopped server/copy.
"""
import argparse
from collections import Counter
import io
import json
from pathlib import Path
import struct
import zlib
import gzip
import nbtlib
from PIL import Image, ImageDraw, ImageFont

def chunks(directory):
    for path in sorted(directory.glob('*.mca')):
        raw = path.read_bytes()
        for i in range(1024):
            loc = int.from_bytes(raw[i*4:i*4+4], 'big')
            if not loc:
                continue
            offset = (loc >> 8) * 4096
            length = int.from_bytes(raw[offset:offset+4], 'big')
            compression = raw[offset+4]
            payload = raw[offset+5:offset+4+length]
            if compression == 2:
                payload = zlib.decompress(payload)
            elif compression == 1:
                payload = gzip.decompress(payload)
            elif compression != 3:
                raise ValueError(f'Unsupported region compression {compression} in {path}')
            yield nbtlib.File.parse(io.BytesIO(payload))

def blocks(world):
    result = {}
    for chunk in chunks(world/'DIM1'/'region'):
        cx, cz = int(chunk['xPos']), int(chunk['zPos'])
        if not (-8 <= cx <= 6 and -6 <= cz <= 8):
            continue
        for section in chunk['sections']:
            sy = int(section['Y'])
            if sy < 4 or sy > 7:
                continue
            states = section['block_states']
            palette = [str(p['Name']).removeprefix('minecraft:') for p in states['palette']]
            if palette == ['air']:
                continue
            bits = max(4, (len(palette)-1).bit_length())
            per_long = 64 // bits
            mask = (1 << bits) - 1
            data = [int(v) & ((1 << 64)-1) for v in states.get('data', [])]
            for i in range(4096):
                p = (data[i//per_long] >> ((i % per_long)*bits)) & mask if data else 0
                material = palette[p]
                if material in ('air', 'cave_air', 'void_air'):
                    continue
                x = cx*16+(i&15)
                y = sy*16+(i>>8)
                z = cz*16+((i>>4)&15)
                result[x,y,z] = material
    return result

def font(size):
    for name in ('C:/Windows/Fonts/arial.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default(size=size)

def color(name):
    if 'gold' in name: return '#caa45d'
    if name == 'lodestone': return '#fff1a1'
    if 'yellow' in name: return '#d6b638'
    if 'orange' in name or 'shroomlight' in name: return '#d98531'
    if 'sandstone' in name: return '#c8b27e'
    if 'blue' in name or 'water' in name or 'cyan' in name: return '#4b8bba'
    if 'copper' in name: return '#738b7a' if 'weathered' in name else '#a66f4d'
    if 'lantern' in name or name == 'end_rod': return '#ffe5a2'
    if 'purple' in name or 'amethyst' in name: return '#8f70aa'
    if 'barrel' in name or 'oak' in name or 'wool' in name: return '#77583e'
    if 'quartz' in name or 'iron' in name or 'white' in name: return '#c5c6c7'
    if 'black' in name: return '#393746'
    return '#555564'

def render(data, output):
    scale, x0, z0, margin, top = 6, -122, -96, 38, 125
    image = Image.new('RGB', (1380, 1515), '#100f1c')
    draw = ImageDraw.Draw(image)
    draw.text((38, 24), 'SPACING GUILD | SAVED WORLD PLAN', fill='#e5bd72', font=font(36))
    draw.text((40, 77), 'Actual Minecraft blocks at floor level; roofs removed for this plan. North is up.', fill='#b3adc2', font=font(19))
    for z in range(-96, 129):
        for x in range(-122, 94):
            candidates = [(y,data[x,y,z]) for y in range(80,87) if (x,y,z) in data]
            if not candidates:
                continue
            y, material = candidates[-1]
            px, pz = margin+(x-x0)*scale, top+(z-z0)*scale
            draw.rectangle((px,pz,px+scale-1,pz+scale-1), fill=color(material))
    for x,z,text in [(-78,-57,'STARPORT'), (0,8,'SPAWN'), (-66,40,'EXCHANGE'), (62,-42,'NEWS'), (62,37,'RULES'), (-66,100,'SMUGGLERS')]:
        px, pz = margin+(x-x0)*scale, top+(z-z0)*scale
        box = draw.textbbox((0,0),text,font=font(21))
        width = box[2]-box[0]
        draw.rectangle((px-width/2-8,pz-15,px+width/2+8,pz+16),fill='#15131e',outline='#bd995a')
        draw.text((px-width/2,pz-13),text,fill='#ffdf9c',font=font(21))
    draw.text((40, 1482), 'Amber Navigator tank north of spawn | Five launch pads | Separate water and spice NPC stalls', fill='#b3adc2', font=font(17))
    output.parent.mkdir(parents=True,exist_ok=True)
    image.save(output)

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('world',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    level=nbtlib.load(args.world/'level.dat')['Data']
    data=blocks(args.world)
    assert tuple(int(level[f'Spawn{k}']) for k in ('X','Y','Z')) == (0,81,10)
    assert data.get((0,80,10)) == 'lodestone'
    assert (0,81,10) not in data and (0,82,10) not in data
    for x,z in [(-100,-73),(-78,-75),(-56,-73),(-101,-39),(-55,-39)]:
        assert data.get((x,82,z)) == 'smooth_quartz', (x,z)
    for x,z in [(0,0),(-78,-58),(-66,40),(62,-42),(62,37),(-66,101)]:
        assert (x,80,z) in data, (x,z)
    entity_counts=Counter()
    roles=Counter()
    for chunk in chunks(args.world/'DIM1'/'entities'):
        for e in chunk.get('Entities',[]):
            entity_counts[str(e['id'])] += 1
            role=e.get('BukkitValues',{}).get('dunelobby:role')
            if role is not None:
                roles[str(role)] += 1
    assert roles == Counter({'liaison':1,'exchange':1,'water':1,'spice':1}),roles
    assert entity_counts['minecraft:glow_item_frame'] == 276,entity_counts
    assert entity_counts['minecraft:ender_dragon'] == 0,entity_counts
    report={'minecraft':'1.21.4','data_version':int(level['DataVersion']),'spawn':[0,81,10],
            'saved_non_air_blocks_in_build_height':len(data),'entities':dict(entity_counts),'npc_roles':dict(roles),
            'checks':'PASS: saved spawn, five launch pads, six rooms, four unique NPCs, 276 artwork frames, no dragon'}
    render(data,args.output)
    args.output.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))
