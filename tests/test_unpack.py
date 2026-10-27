"""End-to-end extraction contract with self-authored archives, never game fixtures."""
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest

from tools.build_desktop import build
from tools.verify_unpacked import verify
from tools.index_unpacked import index
from circus_tools.names import parse_names

ROOT=Path(__file__).resolve().parent.parent

def pcm():
    return b'XPCM'+struct.pack('<IIHHIIHH',4,0,1,1,8000,16000,2,16)+b'\0\0\1\0'

def pck(name,payload):
    return struct.pack('<III',1,76,len(payload))+name.ljust(56,b'\0')+struct.pack('<II',76,len(payload))+payload

class UnpackTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which('javac'):raise unittest.SkipTest('JDK not available')
        cls.java,cls.classes=build()

    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(dir=ROOT/'build')
        self.root=Path(self.tmp.name)

    def tearDown(self):self.tmp.cleanup()

    def unpack(self,source,out):
        return subprocess.run([self.java,'-Dfile.encoding=UTF-8','-Djava.awt.headless=true','-cp',str(self.classes),'org.circus.core.DcUnpack',str(source),str(out)],capture_output=True,encoding='utf-8')

    def test_pcm_and_path_safety(self):
        p=self.root/'game.dat';p.write_bytes(pck(b'../../CON.pcm',pcm()));out=self.root/'out'
        r=self.unpack(p,out);self.assertEqual(r.returncode,0,r.stderr)
        row=json.loads((out/'manifest.jsonl').read_text(encoding='utf-8'))
        self.assertEqual((out/row['raw']).read_bytes(),pcm())
        wav=(out/row['decoded']).read_bytes();self.assertEqual(wav[:4],b'RIFF');self.assertEqual(wav[44:],b'\0\0\1\0')
        self.assertEqual(hashlib.sha256(wav).hexdigest(),row['decoded_sha256'])
        self.assertTrue((out/row['raw']).resolve().is_relative_to(out.resolve()))
        self.assertNotEqual(self.unpack(p,out).returncode,0,'Nonempty output must not be overwritten')

    def test_old_voice_split_offsets(self):
        p=self.root/'VOICE.DAT';second=self.root/'VOICE2.DAT';data=pcm()
        p.write_bytes(struct.pack('<I',2)+b'A.PCM'.ljust(60,b'\0')+struct.pack('<I',2048)+b'B.PCM'.ljust(60,b'\0')+struct.pack('<I',1)+bytes(2048-132)+data)
        second.write_bytes(data);out=self.root/'out';r=self.unpack(p,out);self.assertEqual(r.returncode,0,r.stderr)
        rows=[json.loads(l) for l in (out/'manifest.jsonl').read_text(encoding='utf-8').splitlines()]
        self.assertEqual([(r['source'],r['offset']) for r in rows],[('VOICE.DAT',2048),('VOICE2.DAT',0)])
        self.assertTrue(all((out/r['raw']).read_bytes()==data for r in rows))

    def test_corrupt_audio_keeps_raw_and_reports_failure(self):
        p=self.root/'game.dat';p.write_bytes(pck(b'bad.pcm',b'XPCM'+bytes(24)));out=self.root/'out'
        r=self.unpack(p,out);self.assertEqual(r.returncode,1)
        report=json.loads((out/'summary.json').read_text(encoding='utf-8'));self.assertTrue(report['raw_complete']);self.assertFalse(report['complete'])
        self.assertEqual(report['raw_verified'],1)

    def test_output_inside_game_is_rejected(self):
        game=self.root/'game';adv=game/'AdvData';adv.mkdir(parents=True);p=adv/'VOICE.DAT';p.write_bytes(pck(b'a.pcm',pcm()))
        r=self.unpack(p,game/'out');self.assertNotEqual(r.returncode,0);self.assertFalse((game/'out').exists())

    def test_independent_verifier_detects_modified_export(self):
        p=self.root/'game.dat';p.write_bytes(pck(b'a.pcm',pcm()));out=self.root/'out'
        self.assertEqual(self.unpack(p,out).returncode,0)
        self.assertTrue(verify(out))
        row=json.loads((out/'manifest.jsonl').read_text(encoding='utf-8'))
        (out/row['decoded']).write_bytes(b'corrupted')
        self.assertFalse(verify(out))

    def test_legacy_names_width_and_opaque_fields(self):
        data=struct.pack('<I',1)+b'script'.ljust(128,b'\0')
        data+=struct.pack('<I',1)+b'music/01'.ljust(128,b'\0')+bytes(range(64))
        data+=struct.pack('<III',0,0,0)
        parsed=parse_names(data,True)
        self.assertEqual(parsed['tables'][1]['entries'][0]['name'],'music/01')
        self.assertEqual(parsed['tables'][1]['entries'][0]['extra_hex'],bytes(range(64)).hex())

    def test_gallery_prefix_preserved_in_index(self):
        game=self.root/'game';grp=game/'AdvData/GRP';grp.mkdir(parents=True)
        (grp/'NAMES.DAT').write_bytes(struct.pack('<IIII',0,0,0,1)+b'//bg\\s01a'.ljust(128,b'\0')+struct.pack('<I',0))
        out=self.root/'out';(out/'bg').mkdir(parents=True);png=b'synthetic image bytes';(out/'bg/001.png').write_bytes(png)
        (out/'summary.json').write_text(json.dumps({'input':str(game),'profile':'dcpc-2063'}),encoding='utf-8')
        (out/'manifest.jsonl').write_text(json.dumps({'source':'AdvData/GRP/BG/s01a.CRX','entry':'s01a.CRX','category':'bg','decoded':'bg/001.png','decoded_sha256':hashlib.sha256(png).hexdigest()})+'\n',encoding='utf-8')
        index(out);gallery=json.loads((out/'catalog/gallery.json').read_text(encoding='utf-8'))
        self.assertEqual(gallery['matched'],1);self.assertEqual(gallery['unmatched'],[])
        self.assertEqual(gallery['entries'][0]['catalog_prefix'],'//')
        self.assertEqual((out/gallery['entries'][0]['png']).read_bytes(),png)
