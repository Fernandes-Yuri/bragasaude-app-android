"""Impede janelas avulsas fora dos componentes visuais compartilhados."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
source = root / "app/src/main/java"
shared = source / "br/com/bragasaude/ui/components/BragaModals.kt"
violations = []
for path in source.rglob("*.kt"):
    if path == shared:
        continue
    text = path.read_text(encoding="utf-8-sig")
    # Ignorar comentários e literais, mantendo as linhas para o diagnóstico.
    code = re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/',
                  lambda match: re.sub(r"[^\n]", " ", match.group()), text)
    for match in re.finditer(r"\b(?:AlertDialog|BasicAlertDialog|Dialog|ModalBottomSheet|DatePickerDialog)\s*\(", code):
        line = code.count("\n", 0, match.start()) + 1
        violations.append(f"{path.relative_to(root)}:{line}: use os componentes BragaModals")
if violations:
    print("\n".join(violations))
    sys.exit(1)
print("Janelas de UI usam o padrão compartilhado Braga.")
