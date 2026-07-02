#!/usr/bin/env python3
"""Verify Radiance validation logs contain expected proof markers."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping

CRASH_MARKERS = (
    "Mixin apply failed",
    "Mixin prepare failed",
    "ClassNotFoundException",
    "NoClassDefFoundError",
    "hs_err_pid",
    "EXCEPTION_ACCESS_VIOLATION",
    "java.lang.UnsatisfiedLinkError",
)

AUTH_NOISE = (
    "Realms authentication error",
    "Authentication servers are down",
    "401",
)


@dataclass(frozen=True)
class Marker:
    text: str
    source: str = "log"
    regex: bool = False
    warning_only: bool = False


@dataclass(frozen=True)
class VerificationResult:
    profiles: tuple[str, ...]
    errors: tuple[str, ...]
    warnings: tuple[str, ...]
    matched: Mapping[str, tuple[str, ...]]

    @property
    def ok(self) -> bool:
        return not self.errors


PROFILES: dict[str, tuple[Marker, ...]] = {
    "default-title": (
        Marker("Radiance renderer availability: required=false"),
        Marker("Radiance native renderer lifecycle is not required"),
        Marker("Radiance native renderer is disabled"),
        Marker("Sound engine started"),
    ),
    "default-world": (
        Marker("Radiance renderer availability: required=false"),
        Marker("Sound engine started"),
        Marker("Starting integrated minecraft server"),
        Marker("joined the game"),
    ),
    "required-title": (
        Marker("Radiance renderer availability: required=true"),
        Marker("Radiance lifecycle marker: native renderer loaded from"),
        Marker("Radiance native renderer folder path set to"),
        Marker("Using graphics backend Vulkan"),
        Marker("Radiance native renderer initialization deferred until a loaded world frame"),
        Marker("Sound engine started"),
    ),
    "required-world": (
        Marker("Radiance renderer availability: required=true"),
        Marker("Radiance lifecycle marker: native renderer loaded from"),
        Marker("Radiance lifecycle marker: native renderer initialized"),
        Marker("joined the game"),
        Marker("Radiance pipeline load completed"),
        Marker(r"Radiance texture upload replay: replayed [1-9][0-9]* vanilla texture writes", regex=True),
        Marker("Radiance RenderSystem bridge: captured 26.2 default uniform bindings"),
        Marker("Radiance chunk bridge: uploaded native section"),
    ),
    "f2-readback": (
        Marker("Radiance screenshot capture using native overlay color target"),
        Marker("Saved screenshot as"),
    ),
    "cloud-replay": (
        Marker(r"accepted encoded cloud packet through native overlay draw path .*faces=[1-9][0-9]*", source="stderr", regex=True),
    ),
    "line-replay": (
        Marker("Radiance line replay proof: synthesized Globals ScreenSize fallback"),
        Marker("Radiance render-pass replay accepted by native overlay path: pipeline=minecraft:pipeline/lines"),
        Marker("Radiance line replay proof: accepted native replay pipeline=minecraft:pipeline/lines"),
    ),
    "entity-item-replay": (
        Marker("Radiance render-pass replay accepted by native overlay path: pipeline=minecraft:pipeline/item_cutout"),
        Marker("Radiance render-pass replay accepted by native overlay path: pipeline=minecraft:pipeline/entity_cutout"),
        Marker("Radiance render-pass replay accepted by native overlay path: pipeline=minecraft:pipeline/entity_cutout_cull"),
        Marker("Radiance render-pass replay accepted by native overlay path: pipeline=minecraft:pipeline/eyes"),
        Marker("Radiance render-pass replay accepted by native overlay path: pipeline=minecraft:pipeline/entity_translucent"),
    ),
    "glint-surface": (
        Marker("RADIANCE_ITEM_GLINT_BLOCKED_26_2_SUBMIT_NODE_STATE"),
        Marker("RADIANCE_ITEM_GLINT_SURFACE_26_2_FEATURE_RENDERER"),
    ),
    "glint-native-replay": (
        Marker("RADIANCE_ITEM_GLINT_RENDERPASS_NATIVE_ACCEPTED_26_2"),
        Marker("pipeline=minecraft:pipeline/glint"),
    ),
}


def read_text(path: Path | None) -> str:
    if path is None:
        return ""
    return path.read_text(encoding="utf-8", errors="replace")


def marker_present(marker: Marker, text: str) -> bool:
    if marker.regex:
        return re.search(marker.text, text, re.MULTILINE) is not None
    return marker.text in text


def verify_profiles(
    root: Path | str,
    profiles: Iterable[str],
    *,
    log: Path | str,
    stderr: Path | str | None = None,
    screenshot: Path | str | None = None,
    forbid_crash_markers: bool = True,
) -> VerificationResult:
    root = Path(root)
    profile_tuple = tuple(profiles)
    log_path = Path(log)
    stderr_path = Path(stderr) if stderr is not None else None
    screenshot_path = Path(screenshot) if screenshot is not None else None

    log_text = read_text(log_path)
    stderr_text = read_text(stderr_path)
    sources = {"log": log_text, "stderr": stderr_text, "combined": log_text + "\n" + stderr_text}

    errors: list[str] = []
    warnings: list[str] = []
    matched: dict[str, list[str]] = {profile: [] for profile in profile_tuple}

    for profile in profile_tuple:
        markers = PROFILES.get(profile)
        if markers is None:
            errors.append(f"unknown profile: {profile}; known profiles: {', '.join(sorted(PROFILES))}")
            continue
        for marker in markers:
            source_text = sources.get(marker.source, "")
            if marker_present(marker, source_text):
                matched[profile].append(marker.text)
            else:
                message = f"{profile}: missing marker in {marker.source}: {marker.text}"
                if marker.warning_only:
                    warnings.append(message)
                else:
                    errors.append(message)

    if "f2-readback" in profile_tuple:
        if screenshot_path is None:
            errors.append("f2-readback: --screenshot is required")
        else:
            candidate = screenshot_path if screenshot_path.is_absolute() else root / screenshot_path
            if not candidate.is_file() or candidate.stat().st_size <= 0:
                errors.append(f"f2-readback: screenshot missing or empty: {candidate}")

    if forbid_crash_markers:
        combined = sources["combined"]
        for crash_marker in CRASH_MARKERS:
            if crash_marker in combined:
                errors.append(f"forbidden crash marker present: {crash_marker}")

    combined = sources["combined"]
    for noise in AUTH_NOISE:
        if noise in combined:
            warnings.append(f"auth/online-service noise present: {noise}")

    return VerificationResult(
        profiles=profile_tuple,
        errors=tuple(errors),
        warnings=tuple(warnings),
        matched={profile: tuple(values) for profile, values in matched.items()},
    )


def print_text(result: VerificationResult) -> None:
    matched_count = sum(len(v) for v in result.matched.values())
    print(
        "validation log proof: "
        f"profiles={','.join(result.profiles)}, "
        f"matched={matched_count}, errors={len(result.errors)}, warnings={len(result.warnings)}"
    )
    for warning in result.warnings:
        print(f"WARNING: {warning}", file=sys.stderr)
    for error in result.errors:
        print(f"ERROR: {error}", file=sys.stderr)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repository root; used for relative screenshot paths")
    parser.add_argument("--profile", action="append", required=True, choices=sorted(PROFILES), help="validation profile to check; repeatable")
    parser.add_argument("--log", type=Path, required=True, help="Minecraft latest.log/proof log to inspect")
    parser.add_argument("--stderr", type=Path, help="Gradle/native stderr log to inspect for native-side markers")
    parser.add_argument("--screenshot", type=Path, help="screenshot path required by f2-readback")
    parser.add_argument("--json", action="store_true", help="emit JSON result")
    parser.add_argument("--no-forbid-crash-markers", action="store_true", help="do not fail on known crash markers")
    args = parser.parse_args(argv)

    try:
        result = verify_profiles(
            args.root,
            args.profile,
            log=args.log,
            stderr=args.stderr,
            screenshot=args.screenshot,
            forbid_crash_markers=not args.no_forbid_crash_markers,
        )
    except Exception as exc:  # noqa: BLE001 - command-line checker should report concise failures.
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    if args.json:
        print(json.dumps({
            "profiles": result.profiles,
            "ok": result.ok,
            "errors": result.errors,
            "warnings": result.warnings,
            "matched": result.matched,
        }, indent=2))
    else:
        print_text(result)
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
