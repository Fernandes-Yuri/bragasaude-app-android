"""Preflight obrigatório antes de preparar um Robo autenticado; não envia testes."""
import argparse
import json
import re
from datetime import datetime, timezone, timedelta
from pathlib import Path


def validate(manifest, now=None):
    now = now or datetime.now(timezone.utc)
    run = manifest.get("run_id", "")
    if not re.fullmatch(r"[a-zA-Z0-9-]{8,64}", run):
        raise ValueError("run_id inválido")
    expiry = datetime.fromisoformat(manifest["expires_at"])
    if expiry.tzinfo is None or not now < expiry <= now + timedelta(hours=24):
        raise ValueError("Fixtures precisam expirar em até 24 horas")
    accounts = manifest.get("accounts", [])
    if {a.get("role") for a in accounts} != {"PATIENT", "CAREGIVER", "HYBRID"}:
        raise ValueError("Prepare paciente, cuidador e híbrido separados")
    ids = [a.get("uid", "") for a in accounts]
    if len(ids) != len(set(ids)) or not all(i.startswith(f"robo-{run}-") for i in ids):
        raise ValueError("Contas compartilhadas ou de outra execução não são permitidas")
    if not all(a.get("disposable") is True for a in accounts):
        raise ValueError("Apenas identidades descartáveis")
    if manifest.get("allow_external_intents") is not False:
        raise ValueError("O harness deve bloquear telefonia e outros efeitos externos")
    if not manifest.get("cleanup_inventory"):
        raise ValueError("Registre a limpeza das fixtures e seus efeitos")
    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    validate(json.loads(args.manifest.read_text()))
    print("Fixtures isoladas; a submissão do Firebase ainda exige autorização própria.")
