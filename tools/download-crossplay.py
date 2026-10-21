#!/usr/bin/env python3
"""Download the pinned official crossplay plugins and verify every SHA-256."""
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile
import argparse


def download(destination):
    lock = json.loads(Path(__file__).with_name('crossplay-lock.json').read_text())
    destination = Path(destination)
    for entry in lock:
        folder = destination / entry['platform']
        folder.mkdir(parents=True, exist_ok=True)
        target = folder / entry['name']
        if not target.exists():
            request = urllib.request.Request(entry['url'], headers={'User-Agent': 'DuneRealms-build/1.0'})
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read()
        else:
            data = target.read_bytes()
        if hashlib.sha256(data).hexdigest() != entry['sha256']:
            raise ValueError('Checksum mismatch: ' + entry['name'])
        target.write_bytes(data)
        if not zipfile.is_zipfile(target):
            raise ValueError('Invalid JAR: ' + entry['name'])
        print('Verified', entry['project'], entry['version'], entry.get('build', ''), entry['platform'])
    (destination / 'crossplay-lock.json').write_text(json.dumps(lock, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('destination')
    download(parser.parse_args().destination)
