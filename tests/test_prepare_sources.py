import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "prepare_sources.py"


class RomPreparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not SCRIPT.exists():
            raise AssertionError("ROM preparation tool has not been implemented")
        spec = importlib.util.spec_from_file_location("prepare_sources", SCRIPT)
        cls.tool = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.tool)

    def test_all_three_dump_orders_normalize_to_same_bytes(self):
        big = bytes.fromhex("803712400102030405060708")
        swapped = bytes.fromhex("378040120201040306050807")
        little = bytes.fromhex("401237800403020108070605")
        for dump in (big, swapped, little):
            self.assertEqual(self.tool.normalize_rom(dump), big)

    def test_unknown_header_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "N64 ROM"):
            self.tool.normalize_rom(b"not a ROM!!!")

    def test_truncated_word_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "multiple of four"):
            self.tool.normalize_rom(bytes.fromhex("8037124001"))

    def test_wrong_game_is_rejected_even_with_valid_n64_header(self):
        with self.assertRaisesRegex(ValueError, "US 1.0"):
            self.tool.validate_rom(bytes.fromhex("8037124001020304"))


if __name__ == "__main__":
    unittest.main()
