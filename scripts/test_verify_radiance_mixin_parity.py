import json
import tempfile
import unittest
from pathlib import Path

import verify_radiance_mixin_parity as verifier


class VerifyRadianceMixinParityTests(unittest.TestCase):
    def write_mixin_config(self, root: Path, mixins: list[str] | None = None, client: list[str] | None = None) -> None:
        resources = root / "src/main/resources"
        resources.mkdir(parents=True)
        resources.joinpath("radiance.mixins.json").write_text(
            json.dumps({"mixins": mixins or [], "client": client or []}, indent=2) + "\n",
            encoding="utf-8",
        )

    def write_source(self, root: Path, mixin_name: str) -> Path:
        source = root / verifier.mixin_to_source_path(mixin_name)
        source.parent.mkdir(parents=True)
        source.write_text("class TestMixin {}\n", encoding="utf-8")
        return source

    def test_detects_active_mixin_without_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write_mixin_config(root, client=["vulkan_render_integration.MissingMixins"])
            (root / "build.gradle").write_text("plugins { id 'java' }\n", encoding="utf-8")

            result = verifier.verify(root)

            self.assertFalse(result.ok)
            self.assertEqual(1, result.active_count)
            self.assertEqual(1, result.missing_source_count)
            self.assertEqual(0, result.active_excluded_count)
            self.assertIn("missing source", "\n".join(result.errors))

    def test_detects_active_mixin_that_is_gradle_excluded(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mixin = "vulkan_render_integration.FooMixins"
            self.write_source(root, mixin)
            self.write_mixin_config(root, client=[mixin])
            (root / "build.gradle").write_text(
                "sourceSets { main { java { exclude 'com/radiance/mixins/vulkan_render_integration/FooMixins.java' } } } }\n",
                encoding="utf-8",
            )

            result = verifier.verify(root)

            self.assertFalse(result.ok)
            self.assertEqual(1, result.active_count)
            self.assertEqual(1, result.excluded_count)
            self.assertEqual(0, result.missing_source_count)
            self.assertEqual(1, result.active_excluded_count)
            self.assertIn("active but Gradle-excluded", "\n".join(result.errors))

    def test_accepts_intentional_excluded_inactive_mixin(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write_source(root, "vulkan_render_integration.RetiredMixins")
            self.write_mixin_config(root)
            (root / "build.gradle").write_text(
                'sourceSets { main { java { exclude "com/radiance/mixins/vulkan_render_integration/RetiredMixins.java" } } } }\n',
                encoding="utf-8",
            )

            result = verifier.verify(root)

            self.assertTrue(result.ok, result.errors)
            self.assertEqual(0, result.active_count)
            self.assertEqual(1, result.excluded_count)
            self.assertEqual(0, result.missing_source_count)
            self.assertEqual(0, result.active_excluded_count)


if __name__ == "__main__":
    unittest.main()
