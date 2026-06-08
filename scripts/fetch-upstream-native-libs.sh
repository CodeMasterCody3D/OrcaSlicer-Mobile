#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK_DIR="$ROOT/vendor/upstream-apk"
APK="$APK_DIR/SliceBeam_0.3.0.apk"
OUT="$ROOT/app/src/prebuiltNative/jniLibs"
URL="https://github.com/utkabobr/SliceBeam/releases/download/0.3.0/SliceBeam_dd7a6ddf1d.apk"

mkdir -p "$APK_DIR" "$OUT"

if [[ ! -f "$APK" ]]; then
  curl -L --fail -o "$APK" "$URL"
fi

rm -rf "$OUT"
mkdir -p "$OUT"
python3 - "$APK" "$OUT" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import sys

apk = Path(sys.argv[1])
out = Path(sys.argv[2])
with ZipFile(apk) as z:
    libs = [n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
    for name in libs:
        parts = Path(name).parts
        # lib/<abi>/<file.so> -> <out>/<abi>/<file.so>
        if len(parts) != 3:
            continue
        _, abi, filename = parts
        existing = out.parents[1] / "main" / "jniLibs" / abi / filename
        if existing.exists():
            continue
        target = out / abi / filename
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(z.read(name))
print(f"Extracted non-duplicate native libraries to {out}")
PY
