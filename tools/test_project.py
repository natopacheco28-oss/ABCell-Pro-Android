"""Dependency-free checks of the original desktop schema and Android packaging."""
import json
from pathlib import Path
import sqlite3
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
ANDROID = '{http://schemas.android.com/apk/res/android}'

class ProjectTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.executescript((ASSETS / 'schema.sql').read_text())
    def tearDown(self):
        self.db.close()
    def test_six_original_tables(self):
        names = {r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        self.assertEqual(names - {'sqlite_sequence'}, {'ordenes','pagos_orden','servicios_catalogo','ubicaciones','notificaciones_wa','inventario'})
    def test_original_seed_fits_schema(self):
        seed = json.loads((ASSETS / 'catalogo.json').read_text())
        for table, count in [('ubicaciones', 4), ('servicios_catalogo', 5)]:
            self.assertEqual(len(seed[table]), count)
            for row in seed[table]:
                self.db.execute(f"INSERT INTO {table} ({','.join(row)}) VALUES ({','.join('?' for _ in row)})", list(row.values()))
        self.assertEqual(len(seed['estados']), 7)
        self.assertNotIn('Utilidades Android', seed['categorias'])
    def test_app_name_and_private_storage(self):
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        self.assertEqual(len(manifest.findall('uses-permission')), 0)
        app = manifest.find('application')
        self.assertEqual(app.get(ANDROID+'allowBackup'), 'false')
        self.assertEqual(app.get(ANDROID+'icon'), '@mipmap/ic_launcher')
        strings = ET.parse(ROOT / 'app/src/main/res/values/strings.xml').getroot()
        self.assertEqual(strings.find("string[@name='app_name']").text, 'ABCell Móvil')
    def test_no_desktop_or_adb_payload(self):
        files = list((ROOT / 'app/src/main').rglob('*'))
        self.assertFalse(any(p.suffix in {'.exe','.dll','.py'} for p in files))
        self.assertFalse(any('adb' in p.name.lower() for p in files))
    def test_launcher_icons_exist(self):
        for density in ['mdpi','hdpi','xhdpi','xxhdpi','xxxhdpi']:
            self.assertTrue((ROOT / f'app/src/main/res/mipmap-{density}/ic_launcher.png').is_file())

if __name__ == '__main__':
    unittest.main()
