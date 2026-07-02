import tempfile
import unittest
from pathlib import Path

import verify_validation_log as verifier


class VerifyValidationLogTests(unittest.TestCase):
    def write_log(self, root: Path, name: str, content: str) -> Path:
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def test_default_title_accepts_required_markers(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = self.write_log(
                root,
                "latest.log",
                "\n".join(
                    [
                        "Radiance renderer availability: required=false, packagedResources=true",
                        "Radiance native renderer lifecycle is not required; continuing with renderer mixins disabled",
                        "Radiance native renderer is disabled; skipping native renderer initialization",
                        "Sound engine started",
                    ]
                ),
            )

            result = verifier.verify_profiles(root, ["default-title"], log=log)

            self.assertTrue(result.ok, result.errors)
            self.assertFalse(result.warnings)

    def test_required_world_reports_missing_marker(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = self.write_log(root, "latest.log", "Radiance renderer availability: required=true\n")

            result = verifier.verify_profiles(root, ["required-world"], log=log)

            self.assertFalse(result.ok)
            self.assertIn("Radiance lifecycle marker: native renderer loaded from", "\n".join(result.errors))

    def test_cloud_replay_uses_stderr(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = self.write_log(root, "latest.log", "")
            err = self.write_log(root, "gradle.err.log", "accepted encoded cloud packet through native overlay draw path (faces=1)\n")

            result = verifier.verify_profiles(root, ["cloud-replay"], log=log, stderr=err)

            self.assertTrue(result.ok, result.errors)

    def test_screenshot_profile_requires_existing_nonzero_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            shot = self.write_log(root, "run/screenshots/shot.png", "not really an image but nonzero")
            log = self.write_log(
                root,
                "latest.log",
                "Radiance screenshot capture using native overlay color target\nSaved screenshot as shot.png\n",
            )

            result = verifier.verify_profiles(root, ["f2-readback"], log=log, screenshot=shot)

            self.assertTrue(result.ok, result.errors)

    def test_forbidden_crash_marker_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = self.write_log(
                root,
                "latest.log",
                "Radiance renderer availability: required=false\nSound engine started\nMixin apply failed\n",
            )

            result = verifier.verify_profiles(root, ["default-title"], log=log)

            self.assertFalse(result.ok)
            self.assertIn("forbidden crash marker", "\n".join(result.errors))

    def test_glint_native_replay_profile_accepts_new_marker(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = self.write_log(
                root,
                "latest.log",
                "RADIANCE_ITEM_GLINT_RENDERPASS_NATIVE_ACCEPTED_26_2 pipeline=minecraft:pipeline/glint renderType=glint\n",
            )

            result = verifier.verify_profiles(root, ["glint-native-replay"], log=log)

            self.assertTrue(result.ok, result.errors)


if __name__ == "__main__":
    unittest.main()
