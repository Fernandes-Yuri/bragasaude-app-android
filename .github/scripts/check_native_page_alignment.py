"""Verifica LOAD de 16 KB e alinhamento ZIP nas bibliotecas de 64 bits do APK."""
import struct
import sys
import zipfile
from pathlib import Path


def validate(apk: Path) -> int:
    errors = []
    checked = 0
    with zipfile.ZipFile(apk) as archive, apk.open("rb") as raw:
        for info in archive.infolist():
            if not info.filename.startswith(("lib/arm64-v8a/", "lib/x86_64/")) or not info.filename.endswith(".so"):
                continue
            checked += 1
            data = archive.read(info)
            if data[:6] != b"\x7fELF\x02\x01":
                errors.append(f"{info.filename}: ELF64 little-endian esperado")
                continue
            phoff = struct.unpack_from("<Q", data, 32)[0]
            stride, count = struct.unpack_from("<HH", data, 54)
            loads = 0
            for i in range(count):
                kind, _, offset, address, _, _, memory_size, alignment = struct.unpack_from(
                    "<IIQQQQQQ", data, phoff + i * stride)
                if kind == 1:
                    loads += 1
                    if alignment < 16384 or alignment & (alignment - 1) or (address - offset) % 16384:
                        errors.append(f"{info.filename}: segmento LOAD não alinhado a 16 KB")
                if kind == 0x6474E552 and info.filename.endswith("/libbraga_slm.so"):
                    if (address + memory_size) % 16384:
                        errors.append(f"{info.filename}: fim de RELRO não alinhado a 16 KB")
            if not loads:
                errors.append(f"{info.filename}: nenhum segmento LOAD")
            if info.compress_type == zipfile.ZIP_STORED:
                raw.seek(info.header_offset)
                header = raw.read(30)
                name_size, extra_size = struct.unpack_from("<HH", header, 26)
                if (info.header_offset + 30 + name_size + extra_size) % 16384:
                    errors.append(f"{info.filename}: entrada ZIP não alinhada a 16 KB")
    if not checked:
        errors.append("APK sem bibliotecas nativas de 64 bits")
    for error in errors:
        print(error)
    if not errors:
        print(f"LOAD e ZIP de 16 KB aprovados em {checked} bibliotecas; RELRO do Braga aprovado.")
    return bool(errors)


if __name__ == "__main__":
    sys.exit(validate(Path(sys.argv[1])))
