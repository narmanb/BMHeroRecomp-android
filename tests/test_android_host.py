"""Host regressions for Android import and native surface ownership."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class AndroidHostTests(unittest.TestCase):
    def test_diagnostics_survive_process_death_and_bound_report_size(self):
        javac = os.environ.get('BMHERO_JAVAC') or shutil.which('javac')
        java = shutil.which('java')
        if not javac or not java:
            self.skipTest('requires JDK')
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([javac, '-d', directory,
                str(ROOT/'tests/DiagnosticFilesHarness.java'),
                str(ROOT/'android/app/src/main/java/com/narmanb/bmhero/DiagnosticFiles.java')],
                capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            subprocess.run([java, '-cp', directory, 'com.narmanb.bmhero.DiagnosticFilesHarness'], check=True)

    def test_window_reference_is_transferred_or_released(self):
        compiler = shutil.which('g++')
        if not compiler:
            self.skipTest('requires a host C++ compiler')
        with tempfile.TemporaryDirectory() as directory:
            binary = str(Path(directory) / 'window-test')
            subprocess.run([compiler, '-std=c++17', '-I'+str(ROOT/'include'),
                            str(ROOT/'tests/test_window_handoff.cpp'), '-o', binary], check=True)
            subprocess.run([binary], check=True)

    def test_android_rom_imports_and_rejects_without_losing_previous_import(self):
        javac = os.environ.get('BMHERO_JAVAC') or shutil.which('javac')
        java = shutil.which('java')
        rom = os.environ.get('BMHERO_TEST_ROM')
        if not javac or not java or not rom:
            self.skipTest('requires JDK and user-provided BMHERO_TEST_ROM')
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run([javac, '-d', directory,
                str(ROOT/'tests/RomImporterHarness.java'),
                str(ROOT/'android/app/src/main/java/com/narmanb/bmhero/RomImporter.java')], check=True)
            subprocess.run([java, '-cp', directory, 'com.narmanb.bmhero.RomImporterHarness',
                            str(Path(rom).resolve())], check=True)
