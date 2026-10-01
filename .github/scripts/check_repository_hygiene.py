"""Bloqueia documentos internos e artefatos gerados no índice do Git."""
from pathlib import Path, PurePosixPath
import subprocess
import sys


PUBLIC_DOCS = {
    "README.md", "CONTRIBUTING.md", "SECURITY.md", "CODE_OF_CONDUCT.md",
    ".github/pull_request_template.md",
}
GENERATED_DIRS = {"node_modules", "__pycache__", ".gradle", ".idea", "build"}
GENERATED_SUFFIXES = {".apk", ".aab", ".log", ".pyc", ".tmp", ".bak", ".iml", ".map", ".jks", ".keystore"}


def violation(path: str) -> str | None:
    file = PurePosixPath(path)
    if file.suffix.lower() == ".md" and not (
        path in PUBLIC_DOCS or file.name == "AGENTS.md"
        or path.startswith(".github/ISSUE_TEMPLATE/")
    ):
        return "documentação interna deve ficar na Contexto"
    if file.suffix.lower() in GENERATED_SUFFIXES or GENERATED_DIRS.intersection(file.parts):
        return "artefato gerado, temporário ou privado não deve ser versionado"
    if path.startswith(("tools/", "releases/", "functions/lib/", "app/src/res/")):
        return "diretório descontinuado; confira AGENTS.md"
    if file.name in {"local.properties", "keystore.properties", ".DS_Store"}:
        return "configuração local não deve ser versionada"
    return None


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    files = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode("utf-8").split("\0")
    violations = [(path, reason) for path in files if path and (reason := violation(path))]
    for path, reason in violations:
        print(f"{path}: {reason}")
    if violations:
        return 1
    print("Higiene do repositório validada.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
