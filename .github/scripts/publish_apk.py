#!/usr/bin/env python3
"""Cliente local do gateway: só manifesto público; sem token ou senha do banco."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import re
import tempfile
import urllib.request


def stage(root, manifest, url):
    root = Path(root)
    root.mkdir(parents=True, exist_ok=True)
    name = f"BragaSaude-v{manifest['version_name']}-build{manifest['version_code']}-{manifest['sha256'][:16]}.apk"
    target = root / name
    with tempfile.NamedTemporaryFile(dir=root, suffix='.apk.tmp', delete=False) as temp:
        tmp = Path(temp.name)
        try:
            digest = hashlib.sha256()
            size = 0
            with urllib.request.urlopen(url, timeout=60) as response:
                for block in iter(lambda: response.read(1024 * 1024), b''):
                    temp.write(block); digest.update(block); size += len(block)
            temp.flush(); os.fsync(temp.fileno())
            if digest.hexdigest() != manifest['sha256'] or size != manifest['file_size_bytes']:
                raise ValueError('Download diverge do artifact testado')
            try:
                os.link(tmp, target)  # falha se já existe; nunca sobrescreve uma versão
            except FileExistsError:
                if target.is_symlink() or hashlib.sha256(target.read_bytes()).hexdigest() != manifest['sha256']:
                    raise ValueError('Arquivo imutável existente diverge do artifact')
            target.chmod(0o444)
        finally:
            tmp.unlink(missing_ok=True)
    return target


def send(root, payload):
    with socket.socket(socket.AF_UNIX) as client:
        client.settimeout(120)
        client.connect(str(Path(root) / 'publish.sock'))
        client.sendall(json.dumps(payload).encode() + b'\n')
        with client.makefile('rb') as reader:
            result = json.loads(reader.readline(8192))
    if result.get('status') not in {'ready', 'published', 'already_active'}:
        raise RuntimeError(f"Publicação recusada: {result.get('error', 'sem resultado')}")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', default='/home/ec2-user/braga-gateway/releases')
    parser.add_argument('--manifest')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if args.check:
        print(json.dumps(send(args.root, {'action': 'probe'})))
        return
    manifest = json.loads(Path(args.manifest).read_text())
    if (manifest.get('schema') != 1 or manifest.get('package_name') != 'br.com.bragasaude' or
        manifest.get('variant') != 'release' or manifest.get('debuggable') is not False or
        not re.fullmatch(r'\d+\.\d+\.\d+(?:[-.][a-zA-Z0-9]+)*', manifest.get('version_name', '')) or
        not re.fullmatch(r'[a-f0-9]{64}', manifest.get('sha256', '')) or
        type(manifest.get('version_code')) is not int or manifest['version_code'] <= 0):
        raise ValueError('Manifesto não elegível para publicação')
    ready = send(args.root, {'action': 'probe'})
    if ready.get('contract') != 2 or ready.get('certificate_sha256') != manifest.get('certificate_sha256'):
        raise ValueError('Gateway não confirma a política de publicação')
    tag = f"v{manifest['version_name']}-build{manifest['version_code']}"
    url = f'https://github.com/Fernandes-Yuri/bragasaude-app-android/releases/download/{tag}/app-release.apk'
    stage(args.root, manifest, url)
    manifest.pop('schema')
    print(json.dumps(send(args.root, manifest)))


if __name__ == '__main__':
    main()
