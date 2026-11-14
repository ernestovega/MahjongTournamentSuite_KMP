"""Tests for versions.py. Run with: python3 -m unittest discover -s scripts -p "*_test.py"

The "Build and test" job trusts this script to say which products a pull request must bump, so a wrong answer
lets an app ship without a new version, or blocks a pull request that only touches tests.
"""

from __future__ import annotations

import contextlib
import io
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import versions

CATALOG_TEXT = """[versions]
web-versionName = "1.0.4"
admin-versionName = "1.0.3"
admin-versionCode = "3"
player-versionName = "1.0.3"
player-versionCode = "3"
kotlin = "2.0.0"
"""


class ProductsOfTest(unittest.TestCase):
    def test_shared_modules_go_into_every_product(self):
        for module in ("domain", "data", "ui"):
            self.assertEqual(versions.products_of(f"{module}/src/commonMain/kotlin/A.kt"), {"web", "admin", "player"})

    def test_each_app_module_goes_into_its_own_products(self):
        self.assertEqual(versions.products_of("adminApp/src/commonMain/kotlin/A.kt"), {"web", "admin"})
        self.assertEqual(versions.products_of("playerApp/src/commonMain/kotlin/A.kt"), {"web", "admin", "player"})
        self.assertEqual(versions.products_of("androidApp/src/main/AndroidManifest.xml"), {"admin"})
        self.assertEqual(versions.products_of("playerAndroidApp/build.gradle.kts"), {"player"})

    def test_platform_source_sets_narrow_the_products(self):
        self.assertEqual(versions.products_of("data/src/wasmJsMain/kotlin/A.kt"), {"web"})
        self.assertEqual(versions.products_of("adminApp/src/webMain/resources/telemetry.js"), {"web"})
        self.assertEqual(versions.products_of("data/src/androidMain/kotlin/A.kt"), {"admin", "player"})
        # The desktop app is the admin app.
        self.assertEqual(versions.products_of("data/src/jvmMain/kotlin/A.kt"), {"admin"})
        # An Android source set of the player module goes into both Android apps: the app of the suite holds it too.
        self.assertEqual(versions.products_of("playerApp/src/androidMain/kotlin/A.kt"), {"admin", "player"})

    def test_tests_docs_ci_and_scripts_go_into_no_product(self):
        for path in (
            "data/src/commonTest/kotlin/ATest.kt",
            "adminApp/src/jvmTest/kotlin/AUiTest.kt",
            "androidApp/src/androidTest/kotlin/e2e/ATest.kt",
            "playerApp/src/test/kotlin/ATest.kt",
            "playerApp/diagnostics-test/start.test.mjs",
            "firebase/functions/src/services/a.test.ts",
            "firebase/functions/src/api/a.integration.test.ts",
            "firebase/functions/test/fixture.json",
            "docs/versioning.md",
            "adminApp/README.md",
            ".github/workflows/firebase.yml",
            "scripts/versions.py",
            "AGENTS.md",
        ):
            with self.subTest(path=path):
                self.assertEqual(versions.products_of(path), set())

    def test_the_backend_and_the_hosting_go_with_the_web(self):
        self.assertEqual(versions.products_of("firebase/functions/src/services/a.ts"), {"web"})
        self.assertEqual(versions.products_of("firebase/firestore.rules"), {"web"})
        self.assertEqual(versions.products_of("firebase.json"), {"web"})
        self.assertEqual(versions.products_of(".firebaserc"), {"web"})

    def test_root_build_files_and_gradle_files_touch_every_product(self):
        self.assertEqual(versions.products_of("build.gradle.kts"), {"web", "admin", "player"})
        self.assertEqual(versions.products_of("gradle/wrapper/gradle-wrapper.properties"), {"web", "admin", "player"})

    def test_the_catalog_is_left_to_check(self):
        # check() compares the catalog without its version lines. products_of() must not decide it alone.
        self.assertEqual(versions.products_of(versions.CATALOG), set())


class CatalogTest(unittest.TestCase):
    def test_reads_every_version(self):
        self.assertEqual(
            versions.read_versions(CATALOG_TEXT),
            {
                "web": {"name": "1.0.4"},
                "admin": {"name": "1.0.3", "code": "3"},
                "player": {"name": "1.0.3", "code": "3"},
            },
        )

    def test_without_versions_keeps_the_other_lines(self):
        stripped = versions.without_versions(CATALOG_TEXT)
        self.assertNotIn("versionName", stripped)
        self.assertIn('kotlin = "2.0.0"', stripped)

    def test_parse_name_compares_as_numbers(self):
        self.assertGreater(versions.parse_name("1.0.10"), versions.parse_name("1.0.9"))
        for bad in ("1.0", "1.0.0-beta", "v1.0.0", ""):
            with self.subTest(name=bad), self.assertRaises(ValueError):
                versions.parse_name(bad)


class GitRepoTest(unittest.TestCase):
    """bump() and check() on a small git repository, so the real catalog and history are never touched."""

    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        self.original_root = versions.ROOT
        versions.ROOT = self.root
        self.addCleanup(setattr, versions, "ROOT", self.original_root)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "user.name", "Test")
        self.write(versions.CATALOG, CATALOG_TEXT)
        self.write("docs/a.md", "docs")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "base")
        self.git("checkout", "-q", "-b", "feature")

    def git(self, *args: str) -> str:
        return subprocess.run(["git", *args], cwd=self.root, check=True, capture_output=True, text=True).stdout

    def write(self, path: str, text: str):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)

    def commit(self):
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "change")

    def quiet(self, function, *args):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            result = function(*args)
        return result, out.getvalue()

    def catalog(self):
        return versions.read_versions((self.root / versions.CATALOG).read_text())

    def test_bump_raises_only_the_patch_and_the_code(self):
        self.quiet(versions.bump, "admin")
        self.quiet(versions.bump, "web")
        self.assertEqual(self.catalog()["admin"], {"name": "1.0.4", "code": "4"})
        self.assertEqual(self.catalog()["web"], {"name": "1.0.5"})
        self.assertEqual(self.catalog()["player"], {"name": "1.0.3", "code": "3"})

    def test_a_branch_that_only_changes_tests_and_docs_needs_no_bump(self):
        self.write("data/src/commonTest/kotlin/ATest.kt", "test")
        self.write("docs/a.md", "more docs")
        self.commit()
        self.assertEqual(self.quiet(versions.check, "main")[0], 0)

    def test_a_product_change_without_a_bump_fails(self):
        self.write("adminApp/src/commonMain/kotlin/A.kt", "code")
        self.commit()
        result, out = self.quiet(versions.check, "main")
        self.assertEqual(result, 1)
        self.assertIn("bump admin", out)
        self.assertIn("bump web", out)
        self.assertNotIn("bump player", out)

    def test_bumping_the_products_that_changed_passes(self):
        self.write("playerApp/src/commonMain/kotlin/A.kt", "code")
        self.quiet(versions.bump, "player")
        self.quiet(versions.bump, "admin")
        self.quiet(versions.bump, "web")
        self.commit()
        self.assertEqual(self.quiet(versions.check, "main")[0], 0)

    def test_a_version_that_goes_down_fails(self):
        catalog = (self.root / versions.CATALOG).read_text().replace('web-versionName = "1.0.4"', 'web-versionName = "1.0.3"')
        self.write(versions.CATALOG, catalog)
        self.commit()
        result, out = self.quiet(versions.check, "main")
        self.assertEqual(result, 1)
        self.assertIn("went down", out)

    def test_a_name_without_a_new_code_fails(self):
        catalog = (self.root / versions.CATALOG).read_text().replace('admin-versionName = "1.0.3"', 'admin-versionName = "1.0.4"')
        self.write(versions.CATALOG, catalog)
        self.commit()
        result, out = self.quiet(versions.check, "main")
        self.assertEqual(result, 1)
        self.assertIn("must go up together", out)

    def test_a_library_change_in_the_catalog_needs_every_product_bumped(self):
        catalog = (self.root / versions.CATALOG).read_text().replace('kotlin = "2.0.0"', 'kotlin = "2.1.0"')
        self.write(versions.CATALOG, catalog)
        self.commit()
        result, out = self.quiet(versions.check, "main")
        self.assertEqual(result, 1)
        for product in ("web", "admin", "player"):
            self.assertIn(f"bump {product}", out)

    def test_a_catalog_change_of_only_the_version_lines_needs_nothing_else(self):
        self.quiet(versions.bump, "admin")
        self.commit()
        self.assertEqual(self.quiet(versions.check, "main")[0], 0)


if __name__ == "__main__":
    unittest.main()
