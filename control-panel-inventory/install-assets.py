#!/usr/bin/env python3
"""Install account-page assets into an existing CP source checkout; does not deploy or change auth."""
import argparse, shutil
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('public_directory',type=Path)
a=parser.parse_args();target=a.public_directory.resolve();index=target/'index.html'
if not index.is_file():parser.error('The supplied directory must contain the existing control panel index.html')
html=index.read_text()
if 'id="dashboard"' not in html or 'id="game"' not in html:parser.error('Expected existing account dashboard and game-link sections were not found')
if not index.with_suffix('.html.before-inventory').exists():shutil.copy2(index,index.with_suffix('.html.before-inventory'))
shutil.copytree(Path(__file__).resolve().parents[1]/'website'/'control-panel'/'public'/'inventory',target/'inventory',dirs_exist_ok=True)
css='<link rel="stylesheet" href="/inventory/inventory.css">'
js='<script type="module" src="/inventory/inventory-mount.mjs"></script>'
if css not in html:html=html.replace('</head>',css+'</head>',1)
if js not in html:html=html.replace('</body>',js+'</body>',1)
index.write_text(html)
print('Account assets installed. Wire the authenticated server routes before deploying this checkout.')
