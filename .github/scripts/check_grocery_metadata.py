"""SPEC-001: prevent display names from becoming grocery business rules again."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
engine = root / "app/src/main/java/br/com/bragasaude/domain/WeeklyGroceryEngine.kt"
source = engine.read_text(encoding="utf-8")
assert "groceryNameKey(" not in source, "Engine must use stable IDs and semantic metadata"
assert not re.search(r"(?:name|slug|category)\??\.contains\(", source), "Display text cannot classify foods"
for name in ("arroz", "feijao", "frango", "alface", "salmao", "azeite", "mandioca"):
    assert not re.search(r'"[^"\n]*\b' + name + r'\b[^"\n]*"', source, re.IGNORECASE), name
print("SPEC-001: engine uses semantic metadata, without hardcoded food names.")
