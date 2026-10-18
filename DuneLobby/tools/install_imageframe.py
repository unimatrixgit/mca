"""Install the pinned, verified ImageFrame dependency into a chosen server directory."""
import argparse
import hashlib
from pathlib import Path
import urllib.request

URL = 'https://cdn.modrinth.com/data/lJFOpcEj/versions/dKkWOwRr/ImageFrame-2026.1.5.0.jar'
SHA256 = '65b5c14eb6bd847f1cc0f4985efa95ab43d2f3e2f67dbaec2ecf3ea327fde9b1'

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('server', type=Path)
    args = parser.parse_args()
    target = args.server.resolve() / 'plugins' / 'ImageFrame-2026.1.5.0.jar'
    if target.exists():
        if hashlib.sha256(target.read_bytes()).hexdigest() != SHA256:
            raise SystemExit('Existing ImageFrame differs. Nothing overwritten; inspect it first.')
        print('Pinned ImageFrame already installed.')
    else:
        request = urllib.request.Request(URL, headers={'User-Agent': 'DuneLobby/1.0 https://github.com/thomasv6545-glitch/MC'})
        data = urllib.request.urlopen(request, timeout=90).read()
        if hashlib.sha256(data).hexdigest() != SHA256:
            raise SystemExit('ImageFrame checksum mismatch. Nothing installed.')
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as output:
            output.write(data)
        print('Installed ImageFrame 2026.1.5.0:', target)
