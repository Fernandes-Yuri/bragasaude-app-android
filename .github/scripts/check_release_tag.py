"""Uma tag publicada nunca recebe APK/manifesto com bytes diferentes."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys


def check_release_tag(release, manifest, manifest_digest):
    if release is None:
        return
    assets = {asset['name']: asset for asset in release.get('assets', [])}
    apk = assets.get('app-release.apk')
    if apk is None or apk.get('digest') != 'sha256:' + manifest['sha256']:
        raise ValueError('Tag existente tem bytes diferentes ou hash não comprovado')
    previous = assets.get('release-manifest.json')
    if previous and previous.get('digest') != 'sha256:' + manifest_digest:
        raise ValueError('Manifesto de uma tag publicada é imutável')


def main():
    path = Path(sys.argv[1]); manifest = json.loads(path.read_text())
    tag = f"v{manifest['version_name']}-build{manifest['version_code']}"
    result = subprocess.run(['gh', 'api', f"repos/{sys.argv[2]}/releases/tags/{tag}"], capture_output=True, text=True)
    if result.returncode and 'HTTP 404' not in result.stderr:
        raise RuntimeError('Não foi possível verificar a tag existente')
    release = json.loads(result.stdout) if result.returncode == 0 else None
    check_release_tag(release, manifest, hashlib.sha256(path.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
