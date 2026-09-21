#!/usr/bin/env python3
# =============================================================================
# AUD-AN02 — Gate de CI: proíbe emojis em strings de UI do app Android.
#
# Contexto: o item "emojis em strings de UI" quitou e REGREDIU 2 vezes
# (limpeza por commit, depois alguém reinserta). A doc §12.3 exige um gate
# no CI em vez de commit de limpeza. Este script é esse gate.
#
# Roda em Python puro (sem dependências) — local ou no GitHub Actions:
#   py tools/check_emojis_ui.py
#
# Regra: nenhum emoji (símbolo pictográfico Unicode) pode aparecer dentro de
# uma string literal em arquivos de UI (app/src/main). Emojis em COMENTÁRIO
# são permitidos (não chegam ao usuário), mas também são reportados como
# warning para evitar que virem string no futuro.
# =============================================================================
import re
import sys
import unicodedata
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SCAN_ROOT = REPO / "app" / "src" / "main"

# Intervalos Unicode de emojis/pictogramas (cobertura ampla, sem dependências).
# NOTA: setas (U+2190-21FF) NÃO são emoji — são pontuação técnica comum em
# comentários (→) e em algumas telas legítimas. Fora da lista.
EMOJI_RANGES = re.compile(
    "["
    "\U0001F300-\U0001FAFF"   # Símbolos & Pictogramas (emoji principais)
    "\U00002600-\U000027BF"   # Símbolos diversos (☀ ☂ ✂ ✈ ♻ ⚠ etc.)
    "\U0001F1E6-\U0001F1FF"   # Bandeiras (regional indicators)
    "\U0001F900-\U0001F9FF"   # Emojis suplementares
    "\U0001F000-\U0001F0FF"   # Mahjong / cartas de jogo
    "]"
)

# Extensões de UI (Kotlin/Compose) e de valores (strings.xml).
EXTS = {".kt", ".xml"}

# Arquivos/pastas isentos: ícones vetoriais e a seção de assets.
EXCLUDE_SUBSTRINGS = (
    "/drawable/",       # vetores — emojis viram PathData, não string
    "/mipmap-",         # ícones de launcher
    "/values/ic_",      # resources de ícone
    "ic_launcher",
)


def is_emoji(ch: str) -> bool:
    if EMOJI_RANGES.match(ch):
        return True
    try:
        cat = unicodedata.category(ch)
        name = unicodedata.name(ch, "")
    except (TypeError, ValueError):
        return False
    # Símbolos pictográficos avulsos que viraram emoji (ex: ❤ U+2764).
    return cat.startswith("S") and ("HEART" in name or "KEYCAP" in name)


def line_has_string_literal_emoji(line: str) -> bool:
    """True se a linha tem um emoji DENTRO de aspas duplas."""
    for m in re.finditer(r'"([^"]*)"', line):
        if any(is_emoji(c) for c in m.group(1)):
            return True
    return False


def main() -> int:
    in_string = []   # erros: emoji dentro de string literal
    in_comment = []  # warnings: emoji em comentário (permitido, mas alertar)

    for path in sorted(SCAN_ROOT.rglob("*")):
        if not path.is_file() or path.suffix not in EXTS:
            continue
        rel = str(path.relative_to(REPO)).replace("\\", "/")
        if any(ex in rel for ex in EXCLUDE_SUBSTRINGS):
            continue
        try:
            text = path.read_text(encoding="utf-8-sig")
        except (OSError, UnicodeDecodeError):
            continue
        for lineno, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if not stripped:
                continue
            # Pula declarações de PathData/vetores no XML.
            if "android:pathData" in line or "viewportWidth" in line:
                continue
            if line_has_string_literal_emoji(line):
                in_string.append((rel, lineno, stripped[:100]))
            elif "//" in stripped or "<!--" in stripped or stripped.startswith("*"):
                if any(is_emoji(c) for c in stripped):
                    in_comment.append((rel, lineno, stripped[:100]))

    print("=" * 60)
    print("AUD-AN02 — Gate de emojis em strings de UI")
    print("=" * 60)

    if in_comment:
        print(f"\n⚠ {len(in_comment)} emoji(s) em COMENTÁRIO (permitido —")
        print("  não chega ao usuário, mas remova antes que vire string):")
        for rel, lineno, src in in_comment[:15]:
            print(f"    {rel}:{lineno}: {src}")
        if len(in_comment) > 15:
            print(f"    ... e mais {len(in_comment) - 15}")

    if in_string:
        print(f"\n❌ {len(in_string)} emoji(s) em STRING DE UI — GATE FALHOU:")
        for rel, lineno, src in in_string:
            print(f"    {rel}:{lineno}: {src}")
        print("\nCorrija removendo o emoji da string (use ícone vetorial ou")
        print("texto). Strings de UI com emoji violam a AUD-AN02 (2 regressões).")
        return 1

    print(f"\n✅ Nenhum emoji em string de UI em {SCAN_ROOT.relative_to(REPO)}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
