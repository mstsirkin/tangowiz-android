"""Native cache safety tests. No catalog download or Android runtime required.

python3 tests/test_score_cache.py
TANGO_TEST_DISABLE_CACHE=1 python3 tests/test_score_cache.py ScoreCacheTests.test_reuses_prior_frame_scores
TANGO_TEST_UNBOUNDED_CACHE=1 python3 tests/test_score_cache.py ScoreCacheTests.test_cache_bound_and_overflow_preserve_results
The latter two commands MUST fail (negative controls).
"""
import ctypes as C,json,os,pathlib,shutil,struct,subprocess,tempfile,unittest
import numpy as np
ROOT=pathlib.Path(__file__).resolve().parents[1];CPP=ROOT/'app/src/main/cpp'
TIMING={'elapsedMs','cpuMs','lookupMs','groupingMs','scoreCacheEnabled'}
def comparable(r):return {k:v for k,v in r.items() if k not in TIMING}
def fixture(path):
 rng=np.random.default_rng(20261108);n=512;blocks=n//16
 ids=rng.permutation(n).astype('<i8');reverse=np.argsort(ids).astype('<u4')
 codes=rng.integers(0,256,(blocks,64,16),dtype='u1');pq=rng.uniform(-.12,.12,(64,256,2)).astype('<f4');coarse=np.zeros((1,128),'<f4')
 sections=[codes.tobytes(),ids.tobytes(),np.array([0,blocks],'<i8').tobytes(),pq.tobytes(),coarse.tobytes(),np.array([0,256,512],'<i8').tobytes(),reverse.tobytes()]
 strings=bytearray(b'\0')
 def text(s):
  at=len(strings);strings.extend(s.encode()+b'\0');return at
 rows=[]
 for i in range(2):rows.append([text('source-'+str(i)),text('Song '+str(i)),text('Orchestra '+str(i)),0,0,0,i+1])
 sections.extend([np.array(rows,'<u4').tobytes(),bytes(strings)])
 buf=bytearray(4096);buf[:8]=b'TWIDX002';struct.pack_into('<8Q',buf,8,2,128,64,1,blocks,n,2,9)
 for i,data in enumerate(sections):
  buf.extend(b'\0'*((-len(buf))%4096));struct.pack_into('<QQ',buf,72+16*i,len(buf),len(data));buf.extend(data)
 path.write_bytes(buf)
 vectors=[]
 for i in range(n):
  slot=int(reverse[i]);q=np.concatenate([pq[j,int(codes[slot//16,j,slot%16])] for j in range(64)]).astype('f4');q/=np.linalg.norm(q);vectors.append(q)
 return vectors
class Native:
 def __init__(self,path):
  self.lib=C.CDLL(str(path));a=np.ctypeslib.ndpointer(dtype=np.float32,flags='C_CONTIGUOUS')
  self.lib.phone_open.argtypes=[C.c_char_p];self.lib.phone_open.restype=C.c_void_p
  self.lib.phone_close.argtypes=[C.c_void_p];self.lib.phone_reset.argtypes=[C.c_void_p]
  self.lib.phone_search.argtypes=[C.c_void_p,a,C.c_float,C.c_float];self.lib.phone_search.restype=C.c_char_p
  self.lib.phone_recent_result.argtypes=[C.c_void_p,C.c_int];self.lib.phone_recent_result.restype=C.c_char_p
  self.lib.phone_retain_recent.argtypes=[C.c_void_p,C.c_int];self.lib.phone_retain_recent.restype=C.c_int
  self.lib.phone_cache_stats.argtypes=[C.c_void_p,C.c_int];self.lib.phone_cache_stats.restype=C.c_uint64
 def open(self,path):
  h=self.lib.phone_open(str(path).encode());assert h;return h
 def search(self,h,q,t,speed=1.):
  raw=self.lib.phone_search(h,np.ascontiguousarray(q,'f4'),speed,t);assert raw;return comparable(json.loads(raw))
class ScoreCacheTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.temp=tempfile.TemporaryDirectory();cls.dir=pathlib.Path(cls.temp.name);cls.catalog=cls.dir/'catalog.bin';cls.vectors=fixture(cls.catalog);cls.variants={}
  for name,flags in [('baseline',['-DTANGO_SCORE_CACHE=0']),('cached',['-DTANGO_SCORE_CACHE=1']),('limited',['-DTANGO_SCORE_CACHE=1','-DTANGO_SCORE_CACHE_LIMIT=4'])]:
   source=CPP
   if os.environ.get('TANGO_TEST_UNBOUNDED_CACHE') and name=='limited':
    source=cls.dir/'mutant';shutil.copytree(CPP,source);p=source/'recognition.cpp';s=p.read_text();needle='if(frame.scoreCache.size()<TANGO_SCORE_CACHE_LIMIT)';assert needle in s;p.write_text(s.replace(needle,'if(true)'))
   if os.environ.get('TANGO_TEST_DISABLE_CACHE') and name=='cached':flags=['-DTANGO_SCORE_CACHE=0']
   target=cls.dir/(name+'.so')
   subprocess.run(['g++','-std=c++17','-O2','-fno-fast-math','-ffp-contract=off','-fPIC','-shared','-DTANGO_SCORE_CACHE_TESTING','-DTANGO_RECOVERY_API',*flags,'-I',str(source),'-I',str(ROOT/'tests/host'),str(ROOT/'tests/host/score_cache_bridge.cpp'),str(source/'recognition.cpp'),str(source/'scanner.cpp'),'-o',str(target)],check=True)
   cls.variants[name]=Native(target)
 @classmethod
 def tearDownClass(cls):cls.temp.cleanup()
 def setUp(self):self.handles=[]
 def tearDown(self):
  for lib,h in self.handles:lib.lib.phone_close(h)
 def engine(self,variant='cached'):
  lib=self.variants[variant];h=lib.open(self.catalog);self.handles.append((lib,h));return lib,h
 def pair(self,variant='cached'):return self.engine('baseline'),self.engine(variant)
 def compare(self,pair,index,t,speed=1.):
  q=self.vectors[index%512];a,b=pair;ra=a[0].search(a[1],q,t,speed);rb=b[0].search(b[1],q,t,speed);self.assertEqual(ra,rb);return rb
 def feed(self,pair,start,count,offset=0.,speed=1.):
  for i in range(count):self.compare(pair,start+i,offset+i*.5*speed,speed)
 def test_reuses_prior_frame_scores(self):
  pair=self.pair();self.feed(pair,0,35);lib,h=pair[1];self.assertGreater(lib.lib.phone_cache_stats(h,0),0);self.assertGreater(lib.lib.phone_cache_stats(h,1),0)
 def test_new_fingerprints_do_not_reuse_old_query_scores(self):
  pair=self.pair()
  for i in range(45):self.compare(pair,0 if i%2==0 else 300,i*.5)
 def test_two_speed_streams_have_independent_evidence(self):
  pair=self.pair()
  for i in range(45):
   self.compare(pair,i,i*.5,1.)
   self.compare(pair,300+i,i*.5*.94,.94)
 def test_sliding_window_evicts_old_frames(self):
  pair=self.pair();self.feed(pair,0,135);lib,h=pair[1];self.assertEqual(lib.lib.phone_cache_stats(h,4),60)
 def test_reset_discards_cache_and_matches_fresh_engine(self):
  pair=self.pair();self.feed(pair,0,45);lib,h=pair[1];self.assertGreater(lib.lib.phone_cache_stats(h,2),0)
  for l,handle in pair:l.lib.phone_reset(handle)
  self.assertEqual(lib.lib.phone_cache_stats(h,2),0);self.assertEqual(lib.lib.phone_cache_stats(h,4),0)
  fresh=self.engine('baseline')
  for i in range(25):
   r=self.compare(pair,300+i,i*.5);self.assertEqual(r,fresh[0].search(fresh[1],self.vectors[300+i],i*.5))
 def test_offset_jump_discards_stale_frames(self):
  pair=self.pair();self.feed(pair,0,45);result=self.compare(pair,300,100.)
  lib,h=pair[1];self.assertEqual(lib.lib.phone_cache_stats(h,4),1)
  fresh=self.engine('baseline');self.assertEqual(result,fresh[0].search(fresh[1],self.vectors[300],100.))
  for i in range(1,20):self.compare(pair,300+i,100+i*.5)
 def test_stale_speed_stream_is_removed(self):
  pair=self.pair();self.feed(pair,300,20,speed=.94);self.feed(pair,0,100,offset=30.)
  lib,h=pair[1];self.assertEqual(lib.lib.phone_cache_stats(h,4),60)
 def test_recent_window_and_retention_preserve_results(self):
  pair=self.pair();self.feed(pair,0,55)
  a,b=pair;self.assertEqual(comparable(json.loads(a[0].lib.phone_recent_result(a[1],10))),comparable(json.loads(b[0].lib.phone_recent_result(b[1],10))))
  for lib,h in pair:self.assertEqual(lib.lib.phone_retain_recent(h,10),1)
  self.assertEqual(b[0].lib.phone_cache_stats(b[1],4),10)
  for i in range(55,85):self.compare(pair,i,i*.5)
 def test_cache_bound_and_overflow_preserve_results(self):
  pair=self.pair('limited');self.feed(pair,0,90);lib,h=pair[1];self.assertEqual(lib.lib.phone_cache_stats(h,3),4);self.assertLessEqual(lib.lib.phone_cache_stats(h,2),60*4)
 def test_different_catalogs_do_not_share_cached_scores(self):
  pair=self.pair();self.feed(pair,0,30);p=self.dir/'different.bin';data=bytearray(self.catalog.read_bytes());off=struct.unpack_from('<Q',data,72+3*16)[0];np.frombuffer(data,dtype='<f4',count=64*256*2,offset=off)[:]*=.5;p.write_bytes(data)
  base=self.variants['baseline'];cache=self.variants['cached'];a=base.open(p);b=cache.open(p);self.handles.extend([(base,a),(cache,b)])
  for i in range(30):self.assertEqual(base.search(a,self.vectors[i],i*.5),cache.search(b,self.vectors[i],i*.5))
if __name__=='__main__':unittest.main()
