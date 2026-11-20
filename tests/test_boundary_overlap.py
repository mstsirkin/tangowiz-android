"""A song starting inside a stale query must remain a legal bounded candidate."""
import ctypes as C,json,pathlib,subprocess,tempfile,unittest
import numpy as np
from test_score_cache import fixture
ROOT=pathlib.Path(__file__).resolve().parents[1];CPP=ROOT/'app/src/main/cpp'
class BoundaryOverlapTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.temp=tempfile.TemporaryDirectory();cls.p=pathlib.Path(cls.temp.name)
  cls.catalog=cls.p/'catalog.bin';cls.vectors=fixture(cls.catalog)
  subprocess.run(['g++','-std=c++17','-O2','-mssse3','-fPIC','-shared','-DTANGO_RECOVERY_API','-DTANGO_BOUNDARY_OVERLAP=1','-I',str(CPP),str(ROOT/'tests/host/recovery_bridge.cpp'),str(CPP/'recognition.cpp'),str(CPP/'scanner.cpp'),'-o',str(cls.p/'matcher.so')],check=True)
  cls.lib=C.CDLL(str(cls.p/'matcher.so'));l=cls.lib
  l.phone_open.argtypes=[C.c_char_p];l.phone_open.restype=C.c_void_p;l.phone_close.argtypes=[C.c_void_p]
  l.phone_search.argtypes=[C.c_void_p,np.ctypeslib.ndpointer(dtype=np.float32,flags='C_CONTIGUOUS'),C.c_float,C.c_float];l.phone_search.restype=C.c_char_p
 @classmethod
 def tearDownClass(cls):cls.temp.cleanup()
 def test_song_beginning_after_unrelated_context_matches_with_honest_frame_count(self):
  h=self.lib.phone_open(str(self.catalog).encode())
  try:
   indices=list(range(100,115))+list(range(256,276))
   for i,ref in enumerate(indices):
    r=json.loads(self.lib.phone_search(h,self.vectors[ref],1.,i*.5))
    if i==24:
     self.assertEqual(r['candidates'][0]['id'],'source-1')
     self.assertGreater(r['candidates'][0]['score'],.65)
     self.assertEqual(r['frames'],10)
     self.assertEqual(r['candidates'][0]['referenceSeconds'],0)
  finally:self.lib.phone_close(h)
if __name__=='__main__':unittest.main()
