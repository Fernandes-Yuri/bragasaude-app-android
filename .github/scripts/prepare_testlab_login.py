#!/usr/bin/env python3
"""Prepara credencial efêmera cifrada para login automático no Firebase Test Lab.

Solicita ao servidor (via AWS SSM) um custom token de até 1h para uma das contas
autorizadas de homologação, cifrado de ponta a ponta com X25519 + HKDF + AES-GCM
para uma chave efêmera em memória. O token nunca aparece em argumentos, logs do
SSM ou saída padrão e é gravado localmente com permissão 0600 (gitignored).
"""
import argparse
import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

PROJECT_ID = "braga-saude"
PACKAGE_NAME = "br.com.bragasaude"
DEFAULT_INSTANCE_ID = "i-04728dd8094ed0e78"
DEFAULT_REGION = "us-east-2"
MAX_GRANT_BYTES = 16_384
DEVICE_GRANT_PATH = f"/sdcard/Android/data/{PACKAGE_NAME}/files/braga-test-login.json"

ALLOWED_ACCOUNTS = frozenset({
    "testebraga1@proton.me",
    "testebraga2@proton.me",
    "testeb2b@teste.com",
})

ACCOUNT_ALIASES = {
    "teste1": "testebraga1@proton.me",
    "paciente": "testebraga1@proton.me",
    "teste2": "testebraga2@proton.me",
    "empresa": "testeb2b@teste.com",
    "b2b": "testeb2b@teste.com",
}


def b64u_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def b64u_decode(text: str) -> bytes:
    padding = "=" * (-len(text) % 4)
    return base64.urlsafe_b64decode(text + padding)


def resolve_account(value: str) -> str:
    normalized = value.strip().lower()
    resolved = ACCOUNT_ALIASES.get(normalized, normalized)
    if resolved not in ALLOWED_ACCOUNTS:
        allowed = ", ".join(sorted(ALLOWED_ACCOUNTS))
        raise ValueError(f"Conta não autorizada para Test Lab: {value!r}. Permitidas: {allowed}")
    return resolved


def _require_crypto():
    try:
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import x25519
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        from cryptography.hazmat.primitives.kdf.hkdf import HKDF
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError(
            "Dependência 'cryptography' ausente no Python local para cifrar o grant efêmero."
        ) from exc
    return hashes, serialization, x25519, AESGCM, HKDF


def generate_ephemeral_keypair():
    _, serialization, x25519, _, _ = _require_crypto()
    private_key = x25519.X25519PrivateKey.generate()
    public_bytes = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    )
    return private_key, b64u_encode(public_bytes)


def hkdf_info(account: str) -> bytes:
    return f"braga-testlab-login-v1:{account}".encode("utf-8")


def decrypt_envelope(private_key, client_pub_b64: str, envelope: dict, expected_account: str) -> dict:
    hashes, serialization, x25519, AESGCM, HKDF = _require_crypto()
    if envelope.get("version") != 1:
        raise ValueError("Versão de envelope criptográfico incompatível.")
    server_pub_bytes = b64u_decode(envelope["server_public_key"])
    client_pub_bytes = b64u_decode(client_pub_b64)
    server_pub = x25519.X25519PublicKey.from_public_bytes(server_pub_bytes)
    shared_secret = private_key.exchange(server_pub)
    aes_key = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=server_pub_bytes + client_pub_bytes,
        info=hkdf_info(expected_account),
    ).derive(shared_secret)
    nonce = b64u_decode(envelope["nonce"])
    ciphertext = b64u_decode(envelope["ciphertext"])
    aad = f"{PROJECT_ID}:{expected_account}".encode("utf-8")
    plaintext = AESGCM(aes_key).decrypt(nonce, ciphertext, aad)
    return json.loads(plaintext.decode("utf-8"))


def validate_grant(grant: dict, expected_account: str, now_seconds: int | None = None) -> dict:
    now = int(time.time()) if now_seconds is None else int(now_seconds)
    encoded = json.dumps(grant, separators=(",", ":"))
    if len(encoded.encode("utf-8")) > MAX_GRANT_BYTES:
        raise ValueError("Credencial efêmera excedeu o tamanho máximo permitido.")
    if grant.get("version") != 1 or grant.get("project_id") != PROJECT_ID:
        raise ValueError("Metadados de projeto/versão inválidos no grant.")
    if grant.get("account_email") != expected_account:
        raise ValueError("Conta do grant diverge da solicitada.")
    uid = grant.get("uid")
    if not isinstance(uid, str) or not uid.strip() or len(uid) > 128:
        raise ValueError("UID ausente ou inválido no grant.")
    expires = grant.get("expires_at")
    if type(expires) is not int or not (now < expires <= now + 3_600):
        raise ValueError("Validade do token fora da janela de 1 hora.")
    token = grant.get("custom_token")
    if not isinstance(token, str):
        raise ValueError("Token customizado ausente.")
    segments = token.split(".")
    if len(segments) != 3 or any(not s for s in segments):
        raise ValueError("Formato JWT inválido no custom_token.")
    claims = json.loads(b64u_decode(segments[1]).decode("utf-8"))
    if claims.get("uid") != uid or claims.get("exp") != expires:
        raise ValueError("Claims uid/exp do token divergem do envelope.")
    iat = claims.get("iat")
    if type(iat) is not int or not (now - 3_600 <= iat <= now + 60):
        raise ValueError("Claim iat fora da janela permitida.")
    purpose = claims.get("claims")
    if not isinstance(purpose, dict) or purpose.get("braga_test_login") is not True or purpose.get("braga_test_account") != expected_account:
        raise ValueError("Claims de propósito/conta ausentes ou divergentes.")
    return grant


REMOTE_MINT_PYTHON = r'''
import base64
import json
import os
import sys

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import x25519
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
import firebase_admin
from firebase_admin import auth, credentials

PROJECT_ID = "braga-saude"
ALLOWED = {
    "testebraga1@proton.me",
    "testebraga2@proton.me",
    "testeb2b@teste.com",
}


def b64u_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def b64u_decode(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def main() -> int:
    account = sys.argv[1].strip().lower()
    client_pub_b64 = sys.argv[2].strip()
    if account not in ALLOWED:
        print("Conta não autorizada.", file=sys.stderr)
        return 2
    if os.environ.get("FIREBASE_AUTH_EMULATOR_HOST"):
        print("Emulador Firebase ativo.", file=sys.stderr)
        return 2
    sa_path = os.environ.get("FIREBASE_SERVICE_ACCOUNT_PATH", "").strip()
    if not sa_path:
        print("FIREBASE_SERVICE_ACCOUNT_PATH ausente no runtime.", file=sys.stderr)
        return 2
    try:
        app = firebase_admin.get_app()
    except ValueError:
        app = firebase_admin.initialize_app(credentials.Certificate(sa_path))
    if app.project_id != PROJECT_ID:
        print("Projeto Firebase divergente.", file=sys.stderr)
        return 2

    user = auth.get_user_by_email(account, app=app)
    if user.disabled or (user.email or "").lower() != account or not user.uid:
        print("Conta indisponível ou divergente.", file=sys.stderr)
        return 2

    token = auth.create_custom_token(
        user.uid,
        developer_claims={"braga_test_login": True, "braga_test_account": account},
        app=app,
    )
    if isinstance(token, bytes):
        token = token.decode("utf-8")
    segments = token.split(".")
    if len(segments) != 3:
        print("Token inválido.", file=sys.stderr)
        return 2
    claims = json.loads(b64u_decode(segments[1]).decode("utf-8"))
    exp = int(claims["exp"])

    grant = json.dumps({
        "version": 1,
        "project_id": PROJECT_ID,
        "account_email": account,
        "uid": user.uid,
        "expires_at": exp,
        "custom_token": token,
    }, separators=(",", ":")).encode("utf-8")

    client_pub_bytes = b64u_decode(client_pub_b64)
    client_pub = x25519.X25519PublicKey.from_public_bytes(client_pub_bytes)
    server_priv = x25519.X25519PrivateKey.generate()
    server_pub_bytes = server_priv.public_key().public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    )
    shared = server_priv.exchange(client_pub)
    aes_key = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=server_pub_bytes + client_pub_bytes,
        info=f"braga-testlab-login-v1:{account}".encode("utf-8"),
    ).derive(shared)
    nonce = os.urandom(12)
    aad = f"{PROJECT_ID}:{account}".encode("utf-8")
    ciphertext = AESGCM(aes_key).encrypt(nonce, grant, aad)

    print(json.dumps({
        "version": 1,
        "server_public_key": b64u_encode(server_pub_bytes),
        "nonce": b64u_encode(nonce),
        "ciphertext": b64u_encode(ciphertext),
    }, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
'''


def build_ssm_command(account: str, client_pub_b64: str) -> str:
    script_b64 = base64.b64encode(REMOTE_MINT_PYTHON.encode("utf-8")).decode("ascii")
    return f"""set -eu
umask 077
task_dir=$(mktemp -d /tmp/braga-testlab.XXXXXX)
trap 'rm -rf "$task_dir"' EXIT
for env_file in /etc/braga-saude/gateway.env /run/braga-saude/gateway.env /home/ec2-user/braga-gateway/.env /home/ec2-user/braga-gateway/app/.env; do
  if [ -r "$env_file" ]; then
    set -a
    # shellcheck disable=SC1090
    . "$env_file"
    set +a
  fi
done
printf '%s' '{script_b64}' | base64 -d > "$task_dir/mint.py"
chown -R ec2-user:ec2-user "$task_dir"
chmod 0700 "$task_dir"
chmod 0600 "$task_dir/mint.py"
if [ -n "${{FIREBASE_SERVICE_ACCOUNT_PATH:-}}" ]; then
  runuser -u ec2-user -- env FIREBASE_SERVICE_ACCOUNT_PATH="$FIREBASE_SERVICE_ACCOUNT_PATH" /home/ec2-user/braga-gateway/venv/bin/python "$task_dir/mint.py" '{account}' '{client_pub_b64}'
else
  runuser -u ec2-user -- /home/ec2-user/braga-gateway/venv/bin/python "$task_dir/mint.py" '{account}' '{client_pub_b64}'
fi
"""


def aws_ssm(region: str, *args: str) -> str:
    return subprocess.run(
        ["aws", "--region", region, "ssm", *args],
        check=True,
        capture_output=True,
        text=True,
    ).stdout


def mint_encrypted_grant_via_ssm(
    account: str,
    client_pub_b64: str,
    instance_id: str = DEFAULT_INSTANCE_ID,
    region: str = DEFAULT_REGION,
) -> dict:
    request = {
        "InstanceIds": [instance_id],
        "DocumentName": "AWS-RunShellScript",
        "TimeoutSeconds": 120,
        "Comment": f"Mint ephemeral encrypted Test Lab grant for {account}",
        "Parameters": {"commands": [build_ssm_command(account, client_pub_b64)]},
    }
    with tempfile.TemporaryDirectory() as temp:
        req_path = Path(temp) / "request.json"
        req_path.write_text(json.dumps(request), encoding="utf-8")
        sent = json.loads(aws_ssm(region, "send-command", "--cli-input-json", f"file://{req_path}"))
    command_id = sent["Command"]["CommandId"]

    for _ in range(60):
        time.sleep(2)
        try:
            invocation = json.loads(
                aws_ssm(
                    region,
                    "get-command-invocation",
                    "--command-id",
                    command_id,
                    "--instance-id",
                    instance_id,
                )
            )
        except subprocess.CalledProcessError as error:
            if "InvocationDoesNotExist" in error.stderr:
                continue
            raise
        status = invocation.get("Status")
        if status in {"Pending", "InProgress", "Delayed"}:
            continue
        if status != "Success":
            err = (invocation.get("StandardErrorContent") or "").strip()
            raise RuntimeError(f"SSM falhou ({status}): {err or 'sem detalhes'}")
        return json.loads((invocation.get("StandardOutputContent") or "").strip())
    raise TimeoutError("Tempo esgotado aguardando geração do grant via SSM.")


def write_grant_securely(output_path: Path, grant: dict) -> Path:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(grant, separators=(",", ":")) + "\n"
    fd, tmp_name = tempfile.mkstemp(dir=output_path.parent, prefix=".braga-test-login.", suffix=".tmp")
    tmp_path = Path(tmp_name)
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_path, output_path)
    except Exception:
        tmp_path.unlink(missing_ok=True)
        raise
    return output_path


def prepare_login(
    account_or_alias: str,
    output_path: Path,
    instance_id: str = DEFAULT_INSTANCE_ID,
    region: str = DEFAULT_REGION,
) -> dict:
    account = resolve_account(account_or_alias)
    private_key, client_pub_b64 = generate_ephemeral_keypair()
    envelope = mint_encrypted_grant_via_ssm(
        account=account,
        client_pub_b64=client_pub_b64,
        instance_id=instance_id,
        region=region,
    )
    grant = decrypt_envelope(private_key, client_pub_b64, envelope, account)
    validate_grant(grant, expected_account=account)
    write_grant_securely(output_path, grant)
    return {
        "account_email": account,
        "project_id": PROJECT_ID,
        "expires_at": grant["expires_at"],
        "output": str(output_path),
        "device_path": DEVICE_GRANT_PATH,
        "other_files_arg": f"{DEVICE_GRANT_PATH}={output_path}",
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--account",
        required=True,
        help="Conta autorizada (testebraga1@proton.me, testebraga2@proton.me, testeb2b@teste.com) ou alias (teste1, teste2, empresa).",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Caminho local do arquivo JSON gerado (padrão: braga-test-login-<alias>.json).",
    )
    parser.add_argument("--instance-id", default=DEFAULT_INSTANCE_ID)
    parser.add_argument("--region", default=DEFAULT_REGION)
    args = parser.parse_args(argv)

    try:
        account = resolve_account(args.account)
        if args.output is not None:
            output_path = args.output
        else:
            suffix = "empresa" if account == "testeb2b@teste.com" else account.split("@")[0]
            output_path = Path(f"braga-test-login-{suffix}.json")
        summary = prepare_login(
            account_or_alias=account,
            output_path=output_path,
            instance_id=args.instance_id,
            region=args.region,
        )
        print(json.dumps(summary, ensure_ascii=False))
        return 0
    except Exception as exc:
        print(f"Erro ao preparar login efêmero: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
