"""Prova criptográfica com APK público conhecido; nunca usa chave privada."""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

from release_manifest import inspect_apk, POLICY


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk')
    args = parser.parse_args()
    apk = Path(args.apk)
    policy = json.loads(POLICY.read_text())
    if hashlib.sha256(apk.read_bytes()).hexdigest() != policy['baseline_apk_sha256']:
        raise ValueError('Fixture diverge do release público aprovado')
    result = inspect_apk(apk, 'release')
    assert result['version_code'] == 32
    try:
        inspect_apk(apk, 'release', {**policy, 'certificate_sha256': '0' * 64})
    except ValueError:
        pass
    else:
        raise AssertionError('Certificado divergente foi aceito')
    with tempfile.TemporaryDirectory() as temp:
        unsigned = Path(temp) / 'unsigned.apk'
        with zipfile.ZipFile(apk) as source, zipfile.ZipFile(unsigned, 'w') as target:
            for info in source.infolist():
                if not info.filename.upper().startswith('META-INF/'):
                    target.writestr(info, source.read(info.filename))
        try:
            inspect_apk(unsigned, 'release')
        except Exception:
            pass
        else:
            raise AssertionError('APK unsigned foi aceito')
        tampered = Path(temp) / 'tampered.apk'
        data = bytearray(apk.read_bytes()); data[100] ^= 1; tampered.write_bytes(data)
        try:
            inspect_apk(tampered, 'release')
        except Exception:
            pass
        else:
            raise AssertionError('APK adulterado foi aceito')
    print('Assinatura real aprovada; unsigned, adulteração e certificado divergente rejeitados.')


if __name__ == '__main__':
    main()
