"""Package a stopped, validated Paper lobby; excludes locks, identities and player data."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('server',type=Path)
    parser.add_argument('output',type=Path)
    args=parser.parse_args()
    server=args.server.resolve()
    world=server/'guild_lobby'
    result=(server/'plugins'/'DuneLobby'/'verification.txt').read_text()
    if not result.startswith('GUILD_VERIFY PASS'):
        raise SystemExit('Refusing package: runtime verification has not passed.')
    if not (world/'DIM1'/'region').is_dir():
        raise SystemExit('Saved End lobby is missing.')
    files={}
    for file in world.rglob('*'):
        if not file.is_file():
            continue
        relative=file.relative_to(world)
        if relative.as_posix() not in ('level.dat','DUNE-LOBBY.txt') and not relative.as_posix().startswith('DIM1/'):
            continue
        if file.name in ('session.lock','uid.dat') or 'playerdata' in relative.parts or 'advancements' in relative.parts or 'stats' in relative.parts:
            continue
        files['guild_lobby/'+relative.as_posix()]=file.read_bytes()
    files['plugins/DuneLobby-1.1.0.jar']=(ROOT/'build/libs/DuneLobby-1.1.0.jar').read_bytes()
    files['tools/install_imageframe.py']=(ROOT/'tools/install_imageframe.py').read_bytes()
    files['README-Guild-Lobby.md']=(ROOT/'README.md').read_bytes()
    files['README.txt']=('Dune Guild Lobby 1.1.0 | Paper 1.21.4 | Java 21 | Interiors only\n\n'
        'Extract into a STOPPED dedicated lobby server directory. Do not overwrite an existing guild_lobby.\n'
        'Install ImageFrame 2026.1.5.0: python tools/install_imageframe.py <server-directory>\n'
        'Keep level-name=lobby_bootstrap (or another primary world); do not set level-name=guild_lobby.\n'
        'Start Paper; DuneLobby imports guild_lobby as THE_END, restores artwork, and routes joins.\n'
        'Run dunelobby verify from console. Players use /lobby.\n'
        'Read README-Guild-Lobby.md for proxy destinations, optional DuneCore/DuneEss integration and barter rules.\n'
        'Source and current instructions: https://github.com/unimatrixgit/mcdune/tree/beta/DuneLobby\n').encode()
    manifest={path:{'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()} for path,data in sorted(files.items())}
    files['manifest.json']=(json.dumps({'minecraft':'1.21.4','world':'guild_lobby','environment':'THE_END','runtime_verification':result.strip(),'files':manifest},indent=2)+'\n').encode()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,date_time=(2026,10,18,12,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info,data)
    with zipfile.ZipFile(args.output) as archive:
        assert archive.testzip() is None
        assert len(archive.namelist()) == len(files)
    print(json.dumps({'archive':str(args.output),'bytes':args.output.stat().st_size,'entries':len(files),
                      'sha256':hashlib.sha256(args.output.read_bytes()).hexdigest()},indent=2))
