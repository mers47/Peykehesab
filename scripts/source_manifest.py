#!/usr/bin/env python3
"""Create a deterministic SHA-256 manifest of production/release input files.

The manifest intentionally excludes build outputs, VCS internals and generated proof files.
It is suitable for binding a release evidence bundle to the exact source tree used by CI.
"""
from __future__ import annotations
from pathlib import Path
import hashlib
import sys

ROOT = Path(__file__).resolve().parents[1]

INCLUDE_FILES = {
    Path("build.gradle.kts"),
    Path("settings.gradle.kts"),
    Path("gradle.properties"),
    Path("app/build.gradle.kts"),
    Path("app/proguard-rules.pro"),
}
INCLUDE_DIRS = (
    Path("app/src"),
    Path("scripts"),
    Path(".github/workflows"),
)
OPTIONAL_DIRS = (Path("app/schemas"),)
OPTIONAL_FILES = (Path(".github/dependabot.yml"),)
EXCLUDED_PARTS = {"build", ".gradle", ".git", "__pycache__", "dist"}
EXCLUDED_SUFFIXES = {".pyc", ".tmp", ".bak", ".old", ".orig", ".rej", ".swp"}


def eligible(path: Path) -> bool:
    rel = path.relative_to(ROOT)
    if any(part in EXCLUDED_PARTS for part in rel.parts):
        return False
    if path.suffix.lower() in EXCLUDED_SUFFIXES:
        return False
    return path.is_file()


def collect() -> list[Path]:
    paths: set[Path] = set()
    for rel in INCLUDE_FILES:
        path = ROOT / rel
        if not path.is_file():
            raise SystemExit(f"required release input missing: {rel.as_posix()}")
        paths.add(path)
    for rel in OPTIONAL_FILES:
        path = ROOT / rel
        if path.is_file():
            paths.add(path)
    for rel in INCLUDE_DIRS + OPTIONAL_DIRS:
        directory = ROOT / rel
        if directory.exists():
            for path in directory.rglob("*"):
                if eligible(path):
                    paths.add(path)
    return sorted(paths, key=lambda p: p.relative_to(ROOT).as_posix())


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    files = collect()
    if not files:
        raise SystemExit("no source files selected")
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        print(f"{sha256_file(path)}  {rel}")
    print(f"# SOURCE_FILE_COUNT={len(files)}", file=sys.stderr)


if __name__ == "__main__":
    main()
