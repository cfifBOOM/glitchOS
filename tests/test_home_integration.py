import importlib.util
import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('configure_home',ROOT/'tools/Configure-Home.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class IntegrationTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.tree=Path(self.tmp.name)/'lineage'
        for sub in ['build','device/oneplus/fajita','vendor/lineage-priv/keys','vendor/contour']:
            (self.tree/sub).mkdir(parents=True,exist_ok=True)
        (self.tree/'build/envsetup.sh').write_text('# fixture')
        (self.tree/m.PRODUCT).write_text('# existing product\n')
        (self.tree/'vendor/contour/owner.txt').write_text('keep')
        (self.tree/'vendor/lineage-priv/keys/marker').write_text('private fixture')
    def test_install_idempotent_restore(self):
        before=(self.tree/m.PRODUCT).read_bytes()
        backup=m.install(self.tree)
        self.assertIsNone(m.install(self.tree))
        self.assertEqual((self.tree/'vendor/contour/owner.txt').read_text(),'keep')
        self.assertEqual((self.tree/'vendor/lineage-priv/keys/marker').read_text(),'private fixture')
        m.restore(backup)
        self.assertEqual((self.tree/m.PRODUCT).read_bytes(),before)
        self.assertEqual(m.files(self.tree/m.DEST),{'owner.txt':m.sha(self.tree/m.DEST/'owner.txt')})
    def test_later_work_is_not_overwritten(self):
        backup=m.install(self.tree)
        (self.tree/m.DEST/'later.txt').write_text('later')
        with self.assertRaises(RuntimeError):m.restore(backup)
        self.assertTrue((self.tree/m.DEST/'later.txt').exists())
    def test_module_update_keeps_product_timestamp(self):
        m.install(self.tree)
        product=self.tree/m.PRODUCT
        timestamp=product.stat().st_mtime_ns
        (self.tree/m.DEST/'home/src/app/contouros/home/HomeActivity.java').write_text('// previous version')
        m.install(self.tree)
        self.assertEqual(product.stat().st_mtime_ns,timestamp)
    def test_old_source_mtime_does_not_hide_changed_content_from_ninja(self):
        source=Path(self.tmp.name)/'imported'
        source.mkdir()
        manifest=source/'AndroidManifest.xml'
        stable=source/'stable.txt'
        manifest.write_text('version 1');stable.write_text('unchanged')
        with patch.object(m,'SOURCE',source):
            m.install(self.tree)
            stable_timestamp=(self.tree/m.DEST/'stable.txt').stat().st_mtime_ns
            manifest.write_text('version 2')
            os.utime(manifest,(946684800,946684800))  # Imported archive keeps its old date.
            started=time.time_ns()
            m.install(self.tree)
            target=self.tree/m.DEST/'AndroidManifest.xml'
            self.assertEqual(target.read_text(),'version 2')
            self.assertGreaterEqual(target.stat().st_mtime_ns,started)
            self.assertEqual((self.tree/m.DEST/'stable.txt').stat().st_mtime_ns,stable_timestamp)
    def test_symlink_and_unknown_tree_refused(self):
        (self.tree/m.DEST/'owner.txt').unlink()
        (self.tree/m.DEST/'link').symlink_to(self.tree/'vendor/lineage-priv/keys/marker')
        with self.assertRaises(RuntimeError):m.install(self.tree)
        self.assertEqual((self.tree/m.PRODUCT).read_text(),'# existing product\n')
        with self.assertRaises(RuntimeError):m.install(Path(self.tmp.name)/'other')

if __name__=='__main__':unittest.main()
