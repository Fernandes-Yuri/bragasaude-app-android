import copy
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import release_manifest as gate
import publish_apk
from check_release_tag import check_release_tag

CERT = json.loads(gate.POLICY.read_text())['certificate_sha256']
INFO = {'package_name': 'br.com.bragasaude', 'variant': 'release', 'debuggable': False,
        'version_name': '1.7.1', 'version_code': 32, 'min_android_sdk': 26,
        'file_size_bytes': 12, 'sha256': 'a' * 64, 'certificate_sha256': CERT}
MANIFEST = {'schema': 1, **INFO, 'source_sha': 'b' * 40, 'run_id': 123}
ORIGIN = {'id': 123, 'head_sha': 'b' * 40, 'path': '.github/workflows/android-apk.yml',
          'head_repository': {'full_name': 'Fernandes-Yuri/bragasaude-app-android'},
          'status': 'completed', 'conclusion': 'success'}
JOBS = [{'name': name, 'conclusion': 'success'} for name in ('Compilar APK', 'Testes unitários')]


class GateTests(unittest.TestCase):
    def validate(self, manifest=None, origin=None, jobs=None, actual=None, current=999):
        with patch.object(gate, 'inspect_apk', return_value=actual or INFO):
            return gate.validate_promotion('app-release.apk', manifest or MANIFEST,
                origin or ORIGIN, jobs if jobs is not None else JOBS, ORIGIN['head_repository']['full_name'], current)

    def test_old_apk_uses_own_metadata_not_current_checkout(self):
        result = self.validate()
        self.assertEqual((result['version_name'], result['version_code']), ('1.7.1', 32))

    def test_current_run_still_requires_build_and_unit_tests(self):
        origin = {**ORIGIN, 'status': 'in_progress', 'conclusion': None}
        self.assertEqual(self.validate(origin=origin, current=123), MANIFEST)
        with self.assertRaises(ValueError):
            self.validate(origin=origin, current=123, jobs=[])

    def test_invalid_origin_variant_gates_or_content_never_promotes(self):
        cases = [
            {'manifest': {**MANIFEST, 'variant': 'debug'}},
            {'manifest': {**MANIFEST, 'debuggable': True}},
            {'manifest': {**MANIFEST, 'source_sha': 'c' * 40}},
            {'manifest': {**MANIFEST, 'run_id': 456}},
            {'manifest': {**MANIFEST, 'schema': 2}},
            {'origin': {**ORIGIN, 'conclusion': 'failure'}},
            {'origin': {**ORIGIN, 'status': 'in_progress'}},
            {'origin': {**ORIGIN, 'head_repository': {'full_name': 'attacker/repo'}}},
            {'origin': {**ORIGIN, 'path': '.github/workflows/other.yml'}},
            {'jobs': []},
            {'jobs': [{**job, 'conclusion': 'failure'} for job in JOBS]},
        ]
        for field in INFO:
            changed = copy.deepcopy(INFO)
            changed[field] = 'changed'
            cases.append({'actual': changed})
        for case in cases:
            with self.subTest(case=case), self.assertRaises(ValueError):
                self.validate(**case)

    def test_inspector_rejects_unsigned_debug_wrong_certificate_or_package(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / 'fixture.apk'; apk.write_bytes(b'synthetic')
            def output(args):
                if args[0] == 'apksigner':
                    return f'Signer #1 certificate SHA-256 digest: {CERT}'
                return {'application-id': 'br.com.bragasaude', 'debuggable': 'false',
                        'version-name': '1.7.1', 'version-code': '32', 'min-sdk': '26'}[args[2]]
            with patch.object(gate, 'tool', side_effect=lambda name: name), patch.object(gate, 'invoke', side_effect=output):
                self.assertEqual(gate.inspect_apk(apk, 'release')['version_code'], 32)
                with patch.object(gate, 'invoke', side_effect=subprocess.CalledProcessError(1, ['apksigner'])), self.assertRaises(subprocess.CalledProcessError):
                    gate.inspect_apk(apk, 'release')
                with self.assertRaises(ValueError):
                    gate.inspect_apk(apk, 'release', {'package_name': 'br.com.bragasaude', 'certificate_sha256': '0' * 64})
                with self.assertRaises(ValueError):
                    gate.inspect_apk(apk, 'release', {'package_name': 'other.package', 'certificate_sha256': CERT})

    def test_staging_preserves_old_file_on_wrong_hash_and_is_immutable(self):
        with tempfile.TemporaryDirectory() as temp:
            old = Path(temp) / 'BragaSaude.apk'; old.write_bytes(b'old')
            content = b'new-signed-fixture'
            manifest = {**MANIFEST, 'sha256': hashlib.sha256(content).hexdigest(), 'file_size_bytes': len(content)}
            with patch.object(publish_apk.urllib.request, 'urlopen', return_value=io.BytesIO(b'wrong')), self.assertRaises(ValueError):
                publish_apk.stage(temp, manifest, 'https://fixture')
            self.assertEqual(old.read_bytes(), b'old')
            for _ in range(2):
                with patch.object(publish_apk.urllib.request, 'urlopen', return_value=io.BytesIO(content)):
                    target = publish_apk.stage(temp, manifest, 'https://fixture')
            self.assertEqual(target.read_bytes(), content)
            self.assertEqual(old.read_bytes(), b'old')
            self.assertFalse(list(Path(temp).glob('*.tmp')))

    def test_v31_sdk_ranges_deduplicate_same_signer_but_reject_mixed_keys(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / 'fixture.apk'; apk.write_bytes(b'synthetic')
            def output(args):
                if args[0] == 'apksigner':
                    return (f'Signer (minSdkVersion=24, maxSdkVersion=32) certificate SHA-256 digest: {CERT}\n'
                            f'Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: {CERT}\n'
                            f'V2 Signer: certificate SHA-256 digest: {CERT}\n'
                            f'Source Stamp Signer certificate SHA-256 digest: {"0" * 64}')
                return {'application-id': 'br.com.bragasaude', 'debuggable': 'false',
                        'version-name': '1.7.1', 'version-code': '32', 'min-sdk': '26'}[args[2]]
            with patch.object(gate, 'tool', side_effect=lambda name: name), patch.object(gate, 'invoke', side_effect=output):
                self.assertEqual(gate.inspect_apk(apk, 'release')['certificate_sha256'], CERT)
            mixed = f'Signer #1 certificate SHA-256 digest: {CERT}\nSigner #2 certificate SHA-256 digest: {"0" * 64}'
            with patch.object(gate, 'tool', side_effect=lambda name: name), patch.object(gate, 'invoke', return_value=mixed), self.assertRaises(ValueError):
                gate.inspect_apk(apk, 'release')

    def test_published_tags_cannot_receive_other_apk_or_manifest_bytes(self):
        release = {'assets': [{'name': 'app-release.apk', 'digest': 'sha256:' + INFO['sha256']},
                              {'name': 'release-manifest.json', 'digest': 'sha256:' + 'b' * 64}]}
        check_release_tag(release, MANIFEST, 'b' * 64)
        with self.assertRaises(ValueError):
            check_release_tag(release, MANIFEST, 'c' * 64)
        with self.assertRaises(ValueError):
            check_release_tag(release, {**MANIFEST, 'sha256': 'd' * 64}, 'b' * 64)


if __name__ == '__main__':
    unittest.main()
