#!/usr/bin/env python3
"""Verify radiance.mixins.json entries have compilable, non-excluded sources."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

MIXIN_CONFIG = Path("src/main/resources/radiance.mixins.json")
BUILD_GRADLE = Path("build.gradle")
MIXIN_SOURCE_ROOT = Path("src/main/java/com/radiance/mixins")
EXCLUDE_RE = re.compile(r"\bexclude\s+(['\"])([^'\"]+\.java)\1")


@dataclass(frozen=True)
class MixinEntry:
    name: str
    source_relative_path: Path


@dataclass(frozen=True)
class VerificationResult:
    active_count: int
    excluded_count: int
    missing_source_count: int
    active_excluded_count: int
    missing_sources: tuple[MixinEntry, ...]
    active_excluded: tuple[MixinEntry, ...]
    errors: tuple[str, ...]

    @property
    def ok(self) -> bool:
        return not self.errors


def load_active_mixins(root: Path) -> list[str]:
    config_path = root / MIXIN_CONFIG
    with config_path.open("r", encoding="utf-8") as fh:
        config = json.load(fh)

    active: list[str] = []
    for key in ("mixins", "client"):
        entries = config.get(key, [])
        if not isinstance(entries, list):
            raise ValueError(f"{MIXIN_CONFIG}: {key!r} must be an array")
        for entry in entries:
            if not isinstance(entry, str):
                raise ValueError(f"{MIXIN_CONFIG}: {key!r} contains non-string entry {entry!r}")
            active.append(entry)
    return active


def mixin_to_source_path(mixin_name: str) -> Path:
    return MIXIN_SOURCE_ROOT / Path(*mixin_name.split(".")).with_suffix(".java")


def parse_gradle_java_excludes(root: Path) -> set[str]:
    build_gradle = root / BUILD_GRADLE
    if not build_gradle.exists():
        return set()
    excludes: set[str] = set()
    for line in build_gradle.read_text(encoding="utf-8").splitlines():
        if line.lstrip().startswith("//"):
            continue
        match = EXCLUDE_RE.search(line)
        if match:
            excludes.add(match.group(2).replace("\\", "/"))
    return excludes


def verify(root: Path | str = ".") -> VerificationResult:
    root = Path(root)
    active_names = load_active_mixins(root)
    active_entries = [MixinEntry(name, mixin_to_source_path(name)) for name in active_names]
    excluded = parse_gradle_java_excludes(root)

    missing_sources = tuple(
        entry for entry in active_entries if not (root / entry.source_relative_path).is_file()
    )
    active_excluded = tuple(
        entry
        for entry in active_entries
        if entry.source_relative_path.relative_to("src/main/java").as_posix() in excluded
    )

    errors: list[str] = []
    if missing_sources:
        errors.append(
            "missing source: "
            + ", ".join(f"{entry.name} -> {entry.source_relative_path.as_posix()}" for entry in missing_sources)
        )
    if active_excluded:
        errors.append(
            "active but Gradle-excluded: "
            + ", ".join(f"{entry.name} -> {entry.source_relative_path.as_posix()}" for entry in active_excluded)
        )

    return VerificationResult(
        active_count=len(active_entries),
        excluded_count=len(excluded),
        missing_source_count=len(missing_sources),
        active_excluded_count=len(active_excluded),
        missing_sources=missing_sources,
        active_excluded=active_excluded,
        errors=tuple(errors),
    )


def print_summary(result: VerificationResult) -> None:
    print(
        "mixin parity: "
        f"active={result.active_count}, "
        f"excluded={result.excluded_count}, "
        f"missing_source={result.missing_source_count}, "
        f"active_excluded={result.active_excluded_count}"
    )
    for error in result.errors:
        print(f"ERROR: {error}", file=sys.stderr)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "root",
        nargs="?",
        default=Path.cwd(),
        type=Path,
        help="repository root to verify (default: current directory)",
    )
    args = parser.parse_args(argv)

    try:
        result = verify(args.root)
    except Exception as exc:  # noqa: BLE001 - command-line verifier should report concise failures.
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    print_summary(result)
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
