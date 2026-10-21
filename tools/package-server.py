#!/usr/bin/env python3
"""Build the single plugin-JAR ZIP after all five Gradle builds have passed."""
from pathlib import Path
import hashlib
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'build/server-package'
JARS={'DuneRealms-minecraft-only':'Core','DuneEss':'Essentials','DuneTerrain':'Terrain','DuneNPC':'NPC','DuneLobby':'Lobby'}
for module,name in JARS.items():
    jar=ROOT/module/'build/libs'/f'DuneRealms-{name}.jar'
    if not jar.is_file(): raise RuntimeError(f'Missing compiled JAR: {jar}')
    with zipfile.ZipFile(jar) as archive:
        if archive.testzip() is not None: raise ValueError(f'Invalid compiled JAR: {jar}')
DEST.mkdir(parents=True,exist_ok=True)
for module,name in JARS.items():
    shutil.copy2(ROOT/module/'build/libs'/f'DuneRealms-{name}.jar',DEST)
subprocess.run([sys.executable,str(ROOT/'tools/download-crossplay.py'),str(DEST/'crossplay')],check=True)
vault=DEST/'Vault-2.5.0.jar'
with urllib.request.urlopen('https://github.com/Foulest/Vault/releases/download/2.5.0/Vault-2.5.0.jar',timeout=120) as response:
    data=response.read()
if hashlib.sha256(data).hexdigest()!='3efb1c893d35807c0f3945068858a96af0f131020eee37545bdddb330044b2d9':
    raise ValueError('Vault SHA-256 mismatch')
vault.write_bytes(data)
with urllib.request.urlopen('https://raw.githubusercontent.com/Foulest/Vault/2.5.0/LICENSE',timeout=120) as response:
    (DEST/'Vault-LICENSE.txt').write_bytes(response.read())
datapack=ROOT/'DuneTerrain/build/distributions/DuneRealms-Dune-Highlands-Datapack.zip'
if not datapack.is_file(): raise RuntimeError('Build DuneTerrain including its biome/loot datapack first')
with zipfile.ZipFile(datapack) as archive:
    if archive.testzip() is not None: raise ValueError('Invalid Arrakis datapack')
shutil.copy2(datapack,DEST/datapack.name)
shutil.copy2(ROOT/'DuneTerrain/README.md',DEST/'Arrakis-Update.md')
shutil.copy2(ROOT/'docs/Arrakis-Update-Validation.md',DEST/'Arrakis-Validation.md')
shutil.copy2(ROOT/'docs/Native-GUI.md',DEST/'Native-GUI.md')
shutil.copy2(ROOT/'docs/Native-GUI-Validation.md',DEST/'Validation.md')
with (DEST/'DuneRealms-Source-Changes.patch').open('wb') as patch:
    subprocess.run(['git','format-patch','--stdout','origin/main..HEAD'],cwd=ROOT,stdout=patch,check=True)
shutil.copy2(ROOT/'README.md',DEST/'Project-README.md')
(DEST/'README.txt').write_text('DuneRealms Essentials / native GUI staging build. Paper 1.21.8, Java 21.\n'
    'Read Native-GUI.md before installing. Replace old project JARs while stopped; keep all existing data folders.\n'
    'Crossplay JARs are separated by platform. Do not install both Geyser variants on the same network.\n'
    'No worlds, player data, private keys, or generated configurations are included.\n'
    'No resource pack or client mod is required. Live cross-client staging verification is still required.\n')
lines=[]
for path in sorted(DEST.rglob('*')):
    if path.is_file() and path.name!='SHA256SUMS.txt':
        lines.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(DEST)))
(DEST/'SHA256SUMS.txt').write_text('\n'.join(lines)+'\n')
output=ROOT/'build/DuneRealms-All-Server-Jars.zip'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(DEST.rglob('*')):
        if path.is_file(): archive.write(path,path.relative_to(DEST))
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert len([name for name in archive.namelist() if name.endswith('.jar')])==11
print(output)
