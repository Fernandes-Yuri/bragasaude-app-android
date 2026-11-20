import base64
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import prepare_testlab_login as prep

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import x25519
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.primitives.kdf.hkdf import HKDF
    HAS_CRYPTO = True
except ImportError:  # pragma: no cover
    HAS_CRYPTO = False


def make_grant(account="testeb2b@teste.com", uid="uid-b2b-1", now=1_800_000_000, exp=1_800_003_600):
    payload = {
        "uid": uid,
        "iat": now,
        "exp": exp,
        "claims": {"braga_test_login": True, "braga_test_account": account},
    }
    seg = prep.b64u_encode(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    return {
        "version": 1,
        "project_id": "braga-saude",
        "account_email": account,
        "uid": uid,
        "expires_at": exp,
        "custom_token": f"header.{seg}.sig",
    }


class PrepareTestLabLoginTest(unittest.TestCase):
    def test_rejects_unexpected_accounts_and_resolves_aliases(self):
        self.assertEqual(prep.resolve_account("empresa"), "testeb2b@teste.com")
        self.assertEqual(prep.resolve_account("teste1"), "testebraga1@proton.me")
        self.assertEqual(prep.resolve_account("TESTEB2B@TESTE.COM"), "testeb2b@teste.com")
        with self.assertRaises(ValueError):
            prep.resolve_account("usuario.real@gmail.com")
        with self.assertRaises(ValueError):
            prep.resolve_account("")

    def test_validates_grant_contract_and_rejects_tampered_values(self):
        now = 1_800_000_000
        grant = make_grant(now=now, exp=now + 3_600)
        self.assertEqual(prep.validate_grant(grant, "testeb2b@teste.com", now_seconds=now)["uid"], "uid-b2b-1")

        for bad in (
            {**grant, "project_id": "other-project"},
            {**grant, "account_email": "testebraga1@proton.me"},
            {**grant, "expires_at": now},
            {**grant, "expires_at": now + 3_601},
            {**grant, "uid": "other-uid"},
            {**grant, "custom_token": "invalid"},
        ):
            with self.assertRaises(ValueError):
                prep.validate_grant(bad, "testeb2b@teste.com", now_seconds=now)

    @unittest.skipUnless(HAS_CRYPTO, "cryptography não instalada no ambiente")
    def test_x25519_aesgcm_roundtrip_writes_0600_file_without_leaking_token_to_stdout(self):
        now = 1_800_000_000
        grant = make_grant(now=now, exp=now + 3_600)

        def fake_ssm(account, client_pub_b64, instance_id=prep.DEFAULT_INSTANCE_ID, region=prep.DEFAULT_REGION):
            client_pub_bytes = prep.b64u_decode(client_pub_b64)
            client_pub = x25519.X25519PublicKey.from_public_bytes(client_pub_bytes)
            srv_priv = x25519.X25519PrivateKey.generate()
            srv_pub_bytes = srv_priv.public_key().public_bytes(
                encoding=serialization.Encoding.Raw,
                format=serialization.PublicFormat.Raw,
            )
            shared = srv_priv.exchange(client_pub)
            aes_key = HKDF(
                algorithm=hashes.SHA256(),
                length=32,
                salt=srv_pub_bytes + client_pub_bytes,
                info=prep.hkdf_info(account),
            ).derive(shared)
            nonce = os.urandom(12)
            aad = f"{prep.PROJECT_ID}:{account}".encode("utf-8")
            ct = AESGCM(aes_key).encrypt(nonce, json.dumps(grant).encode("utf-8"), aad)
            return {
                "version": 1,
                "server_public_key": prep.b64u_encode(srv_pub_bytes),
                "nonce": prep.b64u_encode(nonce),
                "ciphertext": prep.b64u_encode(ct),
            }

        with tempfile.TemporaryDirectory() as tmp:
            out_file = Path(tmp) / "braga-test-login-empresa.json"
            with patch.object(prep, "mint_encrypted_grant_via_ssm", side_effect=fake_ssm), \
                 patch.object(prep.time, "time", return_value=now):
                summary = prep.prepare_login("empresa", out_file)

            self.assertEqual(summary["account_email"], "testeb2b@teste.com")
            self.assertNotIn(grant["custom_token"], json.dumps(summary))
            self.assertNotIn(grant["uid"], json.dumps(summary))
            self.assertTrue(out_file.is_file())
            self.assertEqual(out_file.stat().st_mode & 0o777, 0o600)
            written = json.loads(out_file.read_text(encoding="utf-8"))
            self.assertEqual(written["custom_token"], grant["custom_token"])

    @unittest.skipUnless(HAS_CRYPTO, "cryptography não instalada no ambiente")
    def test_decrypt_rejects_wrong_account_aad(self):
        grant = make_grant(account="testeb2b@teste.com")
        priv, client_pub_b64 = prep.generate_ephemeral_keypair()
        client_pub_bytes = prep.b64u_decode(client_pub_b64)
        client_pub = x25519.X25519PublicKey.from_public_bytes(client_pub_bytes)
        srv_priv = x25519.X25519PrivateKey.generate()
        srv_pub_bytes = srv_priv.public_key().public_bytes(
            encoding=serialization.Encoding.Raw,
            format=serialization.PublicFormat.Raw,
        )
        aes_key = HKDF(
            algorithm=hashes.SHA256(),
            length=32,
            salt=srv_pub_bytes + client_pub_bytes,
            info=prep.hkdf_info("testeb2b@teste.com"),
        ).derive(srv_priv.exchange(client_pub))
        nonce = os.urandom(12)
        ct = AESGCM(aes_key).encrypt(nonce, json.dumps(grant).encode("utf-8"), b"braga-saude:testeb2b@teste.com")
        envelope = {
            "version": 1,
            "server_public_key": prep.b64u_encode(srv_pub_bytes),
            "nonce": prep.b64u_encode(nonce),
            "ciphertext": prep.b64u_encode(ct),
        }
        with self.assertRaises(Exception):
            prep.decrypt_envelope(priv, client_pub_b64, envelope, "testebraga1@proton.me")


if __name__ == "__main__":
    unittest.main()
