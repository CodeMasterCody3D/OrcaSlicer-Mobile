#!/usr/bin/env python3
"""Check Android native prebuilt imports required by app/CMakeLists.txt.

This intentionally focuses on the imported libraries that are not built from
source by this repo: Boost, oneTBB, OCCT, GMP, GMPXX, and MPFR.
"""
from pathlib import Path
import re
import sys

repo = Path(__file__).resolve().parents[1]
app = repo / "app"
cmake = (app / "CMakeLists.txt").read_text()
abi = sys.argv[1] if len(sys.argv) > 1 else "arm64-v8a"

checks = []

for name in ["tbb", "tbbmalloc"]:
    checks.append((name, app / f"src/main/jniImports/oneTBB/lib/{abi}/lib{name}.a"))

for name in ["gmp", "gmpxx", "mpfr"]:
    checks.append((name, app / f"src/main/jniLibs/{abi}/lib{name}.so"))

occt = re.search(r"set\(OCCT_LIBS\s+([^\)]+)\)", cmake, re.S)
if occt:
    for name in occt.group(1).split():
        checks.append((f"occt_{name}", app / f"src/main/occt/jniLibs/{abi}/lib{name}.so"))

boost = re.search(r"set\(BOOST_LIBS\s+([^\)]+)\)", cmake, re.S)
boost_arch = {
    "arm64-v8a": "a64",
    "armeabi-v7a": "a32",
    "x86_64": "x64",
    "x86": "x32",
}.get(abi)
if boost and boost_arch:
    for name in boost.group(1).split():
        checks.append((f"boost_{name}", app / f"src/main/jniImports/boost/lib/{abi}/lib/libboost_{name}-clang-mt-{boost_arch}-1_85.a"))

present = []
missing = []
for label, path in checks:
    (present if path.exists() else missing).append((label, path.relative_to(repo)))

print(f"ABI: {abi}")
print(f"present prebuilt imports: {len(present)}")
print(f"missing prebuilt imports: {len(missing)}")

if missing:
    print("\nMissing:")
    for label, path in missing:
        print(f"- {label}: {path}")
    sys.exit(1)

print("All native prebuilts are present.")
