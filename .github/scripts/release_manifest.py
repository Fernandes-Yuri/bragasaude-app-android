#!/usr/bin/env python3
"""Verifica o APK e vincula os bytes ao commit/run. Não lê keystore/senhas."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

POLICY = Path(__file__).resolve().parents[1] / 'release-policy.json'
FIELDS = ('package_name', 'variant', 'debuggable', 'version_name', 'version_code', 'min_android_sdk',
          'file_size_bytes', 'sha256', 'certificate_sha256')


def tool(name):
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '/nonexistent')
    if name == 'apksigner':
        pinned = sdk / 'build-tools' / '35.0.0' / name
        if not pinned.is_file():
            raise RuntimeError('Build Tools 35.0.0 é obrigatório para verificar assinatura')
        return str(pinned)
    found = shutil.which(name)
    if found:
        return found
    matches = sorted(sdk.glob(f'build-tools/*/{name}')) + sorted(sdk.glob(f'cmdline-tools/*/bin/{name}'))
    if not matches:
        raise RuntimeError(f'Ferramenta obrigatória ausente: {name}')
    return str(matches[-1])


def invoke(args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()


def inspect_apk(apk, variant, policy=None):
    policy = policy or json.loads(POLICY.read_text())
    apk = Path(apk)
    # Falha criptográfica nunca é convertida em artifact elegível a release.
    signature = invoke([tool('apksigner'), 'verify', '--print-certs', str(apk)])
    # v3.1 descreve faixas de SDK em vez de "#1" e pode repetir a mesma chave.
    # Source Stamp não é o assinante do APK e fica fora da seleção.
    signers = sorted(set(value.lower() for value in re.findall(
        r'^(?:Signer(?: #\d+| \(.*\))|V[123](?:\.1)? Signer(?::| #\d+:?)) certificate SHA-256 digest:\s*([a-fA-F0-9]{64})\s*$',
        signature, re.MULTILINE)))
    if len(signers) != 1:
        diagnostics = [line for line in signature.splitlines() if 'SHA-256' in line]
        raise ValueError(f'APK precisa de um único certificado aprovado; ferramenta={tool("apksigner")}; digests públicos={diagnostics}')
    analyzer = tool('apkanalyzer')

    def field(name):
        return invoke([analyzer, 'manifest', name, str(apk)])

    debug = field('debuggable')
    if debug not in {'true', 'false'}:
        raise ValueError('Manifesto não informa debuggable corretamente')
    result = {
        'package_name': field('application-id'), 'version_name': field('version-name'),
        'version_code': int(field('version-code')), 'min_android_sdk': int(field('min-sdk')),
        'debuggable': debug == 'true', 'variant': variant,
        'certificate_sha256': signers[0].lower(), 'file_size_bytes': apk.stat().st_size,
    }
    digest = hashlib.sha256()
    with apk.open('rb') as file:
        for block in iter(lambda: file.read(1024 * 1024), b''):
            digest.update(block)
    result['sha256'] = digest.hexdigest()
    if result['package_name'] != policy['package_name']:
        raise ValueError('Pacote diferente do app canônico')
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:[-.][a-zA-Z0-9]+)*', result['version_name']):
        raise ValueError('Nome de versão inválido')
    if result['version_code'] <= 0 or result['min_android_sdk'] < 26:
        raise ValueError('Code/SDK incompatível com a política de release')
    if variant == 'release' and (result['debuggable'] or result['certificate_sha256'] != policy['certificate_sha256']):
        raise ValueError('APK debug ou certificado de assinatura divergente')
    if variant not in {'debug', 'release'}:
        raise ValueError('Variante inválida')
    return result


def make_manifest(apk, variant, source_sha, run_id):
    if not re.fullmatch(r'[a-f0-9]{40}', source_sha) or int(run_id) <= 0:
        raise ValueError('Proveniência inválida')
    return {'schema': 1, **inspect_apk(apk, variant), 'source_sha': source_sha, 'run_id': int(run_id)}


def validate_promotion(apk, manifest, origin, jobs, repository, current_run_id):
    if set(manifest) != {'schema', 'source_sha', 'run_id', *FIELDS} or manifest['schema'] != 1:
        raise ValueError('Manifesto ausente, desconhecido ou incompleto')
    if manifest['variant'] != 'release' or manifest['debuggable'] is not False:
        raise ValueError('Artifact de diagnóstico não é release')
    if (origin.get('head_repository') or {}).get('full_name') != repository or origin.get('path') != '.github/workflows/android-apk.yml':
        raise ValueError('Origem de workflow/repositório não autorizada')
    if origin.get('id') != manifest['run_id'] or origin.get('head_sha') != manifest['source_sha']:
        raise ValueError('Commit/run diverge do manifesto do APK')
    if origin['id'] != int(current_run_id) and (origin.get('status') != 'completed' or origin.get('conclusion') != 'success'):
        raise ValueError('Run de origem não concluída com sucesso')
    for name in ('Compilar APK', 'Testes unitários'):
        matching = [job for job in jobs if job.get('name') == name]
        if not matching or any(job.get('conclusion') != 'success' for job in matching):
            raise ValueError(f'Gate obrigatório sem sucesso: {name}')
    actual = inspect_apk(apk, 'release')
    if any(actual[key] != manifest[key] for key in FIELDS):
        raise ValueError('Bytes/versão/assinatura do APK divergem do manifesto')
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', required=True)
    parser.add_argument('--variant', choices=('debug', 'release'))
    parser.add_argument('--source-sha')
    parser.add_argument('--run-id', type=int)
    parser.add_argument('--output', required=True)
    parser.add_argument('--validate-manifest')
    parser.add_argument('--origin')
    parser.add_argument('--jobs')
    args = parser.parse_args()
    if args.validate_manifest:
        manifest = validate_promotion(args.apk, json.loads(Path(args.validate_manifest).read_text()),
            json.loads(Path(args.origin).read_text()), json.loads(Path(args.jobs).read_text())['jobs'],
            os.environ['GITHUB_REPOSITORY'], os.environ['GITHUB_RUN_ID'])
    else:
        manifest = make_manifest(args.apk, args.variant, args.source_sha, args.run_id)
    Path(args.output).write_text(json.dumps(manifest, indent=2) + '\n')
    if os.getenv('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as file:
            for key in ('version_name', 'version_code', 'sha256', 'source_sha', 'run_id'):
                file.write(f'{key}={manifest[key]}\n')


if __name__ == '__main__':
    main()
