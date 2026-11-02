"""Native release checks using private, independently recorded phone embeddings."""
import ctypes as C,hashlib,json,pathlib,unicodedata

def digest(path):
 with pathlib.Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def folded(s):return ''.join(c.lower() for c in unicodedata.normalize('NFKD',s) if c.isalnum())

def validate(config,native_library,catalog_override=None):
 import numpy as np
 def require(value,message):
  if not value:raise RuntimeError(message)
 extra=config.get('additional_field_fixtures');require(isinstance(extra,dict),'Additional saved-phone release fixtures are not configured; see README release validation')
 manifest_path=pathlib.Path(extra['manifest_path']);require(digest(manifest_path)==extra['manifest_sha256'],'Saved-phone field fixture manifest changed');manifest=json.loads(manifest_path.read_text());require(manifest['version']==1 and manifest['sample_rate']==8000 and manifest['hop_seconds']==.5,'Unsupported field fixture format')
 require(manifest['model_sha256']=='368f30c94d53e5300f0a17b7b96929b297d56e05903010603a355d798302c851','Field fixture model identity differs')
 catalog=pathlib.Path(catalog_override or extra['catalog_path']);catalog_sha=digest(catalog)
 if catalog_override is None:require(catalog_sha==extra['catalog_sha256'],'Pinned field-fixture catalog changed')
 require({folded(c['title']) for c in manifest['cases']}=={folded(t) for t in ['Vals De Verano','Mujeres Vals','Vals De Invierno']},'Required Solo Tango field coverage is incomplete')
 lib=C.CDLL(str(native_library));p=np.ctypeslib.ndpointer(dtype=np.float32,flags='C_CONTIGUOUS');lib.phone_open.argtypes=[C.c_char_p];lib.phone_open.restype=C.c_void_p;lib.phone_close.argtypes=[C.c_void_p];lib.phone_reset.argtypes=[C.c_void_p];lib.phone_search.argtypes=[C.c_void_p,p,C.c_float,C.c_float];lib.phone_search.restype=C.c_char_p
 h=lib.phone_open(str(catalog).encode());require(h,'Field fixture catalog load failed');results=[];dtype=np.dtype([('t','>f8'),('s','>f4'),('v','>f4',(128,))])
 try:
  for case in manifest['cases']:
   path=manifest_path.parent/case['file'];require(path.is_file() and digest(path)==case['sha256'],'Saved phone query vectors changed');frames=np.fromfile(path,dtype=dtype);require(10<=len(frames)<=60 and np.all(frames['s']==1) and np.isfinite(frames['v']).all(),'Invalid saved phone query frames');require(np.allclose(np.diff(frames['t']),.5,atol=.001),'Saved query is not a contiguous normal phone window');lib.phone_reset(h)
   for frame in frames:
    raw=lib.phone_search(h,np.ascontiguousarray(frame['v'],dtype='f4'),1.,float(frame['t']));require(raw,'Native field query failed');r=json.loads(raw)
   top=next(iter(r.get('candidates',[])),{});require(folded(top.get('title',''))==folded(case['title']) and folded(top.get('orchestra',''))==folded(case['orchestra']) and top.get('score',0)>=case['minimum_score'],'Saved physical-phone Solo Tango recognition regressed')
   results.append(dict(name=case['name'],title=case['title'],orchestra=top['orchestra'],score=top['score'],frames=len(frames),source_phone_session=case['source_phone_session'],source_audio_end_seconds=case['source_audio_end_seconds']))
 finally:lib.phone_close(h)
 return dict(passed=True,cases=results,fixture_manifest_sha256=extra['manifest_sha256'],catalog_sha256=catalog_sha,method='Saved physical-phone normal embeddings, current production native matcher, declared normal-context lengths and unchanged raw score floors. Independent of source-derived reference self-lookups; no asynchronous scheduling or phone hardware timing claim.')

if __name__=='__main__':
 import argparse,os
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--config',type=pathlib.Path,default=pathlib.Path(os.environ.get('TANGOWIZ_RELEASE_FIXTURES',pathlib.Path.home()/'.config/tangowiz/recognition-release-fixtures.json')));p.add_argument('--catalog',type=pathlib.Path,required=True);p.add_argument('--native-library',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args();config=json.loads(a.config.read_text());report=validate(config,a.native_library,a.catalog);a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
