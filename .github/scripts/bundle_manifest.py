"""Record the signed AAB and its build origin; does not publish to Google Play."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile


def main():
    bundle = Path('app/build/outputs/bundle/release/app-release.aab')
    with zipfile.ZipFile(bundle) as archive:
        names = archive.namelist()
        if 'BundleConfig.pb' not in names or 'base/manifest/AndroidManifest.xml' not in names:
            raise ValueError('Estrutura AAB incompleta')
        if not any(name.upper().startswith('META-INF/') and name.upper().endswith(('.RSA', '.EC', '.DSA')) for name in names):
            raise ValueError('AAB sem assinatura')
        if archive.testzip() is not None:
            raise ValueError('AAB corrompido')
    result = subprocess.run(['jarsigner', '-verify', str(bundle)], check=True,
                            capture_output=True, text=True, env={**os.environ, 'JAVA_TOOL_OPTIONS': '-Duser.language=en -Duser.country=US'})
    if 'jar verified.' not in result.stdout:
        raise ValueError('Assinatura AAB não verificada')
    apk = json.loads(Path('release-manifest.json').read_text())
    if apk['variant'] != 'release' or apk['source_sha'] != os.environ['GITHUB_SHA'] or apk['run_id'] != int(os.environ['GITHUB_RUN_ID']):
        raise ValueError('Origem do APK/AAB divergente')
    manifest = {
        'schema': 1, 'artifact': bundle.name,
        'sha256': hashlib.file_digest(bundle.open('rb'), 'sha256').hexdigest(),
        'size_bytes': bundle.stat().st_size,
        'source_sha': apk['source_sha'], 'run_id': apk['run_id'],
        'repository': os.environ['GITHUB_REPOSITORY'],
        'apk_sha256': apk['sha256'],
        'play_validation': 'pending',
    }
    Path('bundle-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    main()
