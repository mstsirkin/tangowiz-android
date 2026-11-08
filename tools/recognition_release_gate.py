#!/usr/bin/env python3
"""Fail-closed, always-fresh native release regression using private phone fixtures."""
import argparse,collections,ctypes as C,hashlib,json,os,pathlib,shutil,subprocess,sys,time,traceback,unicodedata,uuid

REPO=pathlib.Path(__file__).resolve().parents[1]
TERMINAL={'complete','failed','cancelled','stopped'}
class GateFailure(RuntimeError):pass
def require(value,message):
 if not value:raise GateFailure(message)
def sha(path):
 with pathlib.Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def read(path):return json.loads(pathlib.Path(path).read_text())
def signature(candidate):
 files={}
 for prefix,root in [('app',candidate/'app/src'),('tools',REPO/'tools'),('tests',REPO/'tests')]:
  for p in sorted(root.rglob('*')):
   if p.is_file() and p.suffix in {'.kt','.java','.cpp','.h','.hpp','.py'}:files[prefix+'/'+str(p.relative_to(root))]=sha(p)
 for p in [candidate/'build.gradle',candidate/'app/build.gradle',candidate/'settings.gradle',candidate/'gradle.properties',candidate/'app/src/main/cpp/CMakeLists.txt']:
  if p.is_file():files[str(p.relative_to(candidate))]=sha(p)
 return hashlib.sha256(json.dumps(files,sort_keys=True).encode()).hexdigest()
def folded(s):return ''.join(c.lower() for c in unicodedata.normalize('NFKD',s) if c.isalnum())
def top(r):return next(iter(r.get('candidates',[])),{})
def comparable(r):return {k:v for k,v in r.items() if k not in ['elapsedMs','cpuMs','lookupMs','groupingMs']}
def choose_cores():
 used=set();chosen=[]
 for cpu in sorted(os.sched_getaffinity(0)):
  base=pathlib.Path(f'/sys/devices/system/cpu/cpu{cpu}/topology')
  identity=((base/'physical_package_id').read_text(),(base/'core_id').read_text())
  if identity not in used:used.add(identity);chosen.append(cpu)
  if len(chosen)==2:break
 require(bool(chosen),'No host CPU available');os.sched_setaffinity(0,set(chosen));return chosen
class Native:
 def __init__(self,path,index,candidate=False):
  self.lib=C.CDLL(str(path));self.index=index;p=np.ctypeslib.ndpointer(dtype=np.float32,flags='C_CONTIGUOUS');l=self.lib
  l.phone_open.argtypes=[C.c_char_p];l.phone_open.restype=C.c_void_p;l.phone_close.argtypes=[C.c_void_p];l.phone_reset.argtypes=[C.c_void_p]
  l.phone_search.argtypes=[C.c_void_p,p,C.c_float,C.c_float];l.phone_search.restype=C.c_char_p;l.phone_recent.argtypes=[C.c_void_p,C.c_int];l.phone_recent.restype=C.c_double
  if candidate:
   l.phone_recent_result.argtypes=[C.c_void_p,C.c_int];l.phone_recent_result.restype=C.c_char_p;l.phone_retain_recent.argtypes=[C.c_void_p,C.c_int];l.phone_retain_recent.restype=C.c_int
 def open(self):
  h=self.lib.phone_open(str(self.index).encode());require(h,'Catalog load failed');return h
 def close(self,h):self.lib.phone_close(h)
 def reset(self,h):self.lib.phone_reset(h)
 def search(self,h,v,t):
  raw=self.lib.phone_search(h,np.ascontiguousarray(v,dtype='f4'),1.,t);require(raw,'Native matching failed');return json.loads(raw)
 def recent(self,h):
  raw=self.lib.phone_recent_result(h,10);require(raw,'Native recent matching failed');return json.loads(raw)
 def retain(self,h):require(self.lib.phone_retain_recent(h,10)==1,'Native context rebase failed')
class Worker:
 def __init__(self,java,classes,name):
  self.p=subprocess.Popen([str(java/'java'),'-cp',str(classes),name],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,bufsize=1)
 def call(self,*args):
  self.p.stdin.write('\t'.join(map(str,args))+'\n');self.p.stdin.flush();r=self.p.stdout.readline().strip();require(r,'Production Java helper stopped');return r
 def clear(self):self.call('C')
 def close(self):self.p.stdin.close();require(self.p.wait(timeout=10)==0,'Production Java helper failed')
class Predictor(Worker):
 def reset(self,t):return self.call('R',t)
 def observe(self,t,r):
  c=top(r)
  if c:self.call('O',t,c.get('groupId',c['id']),c['score'],c['referenceSeconds'],c['referenceEndSeconds'],r['frames'],c['speed'])
class Recovery(Worker):
 def check(self,t,score,frames):return self.call('S',t,score,frames,'true')=='1'
 def observe(self,t,r,current):
  c=top(r);return self.call('O',t,c.get('id',''),c.get('score',0),current,c.get('referenceSeconds',float('nan')),r.get('frames',0),c.get('speed',float('nan')))=='1'
def worker(args,config,save):
 global np
 import numpy as np
 out=args.output;began=time.monotonic();cores=choose_cores()
 def status(phase,done=0,total=1,**kw):save(out/'status.json',dict(state=phase,updated_at=time.time(),completed_units=done,total_units=total,workers=0 if phase in TERMINAL else 1,physical_cores=len(cores),**kw))
 status('verify_private_release_fixtures')
 require(signature(args.candidate_root)==args.source_signature,'Candidate sources changed before validation')
 mp=pathlib.Path(config['fixture_manifest']);require(sha(mp)==config['fixture_manifest_sha256'],'Private fixture manifest changed')
 manifest=read(mp);require(manifest['version']==1,'Unsupported fixture version');base=mp.parent;index=pathlib.Path(config['catalog_path'])
 require(index.stat().st_size==manifest['catalog_bytes'] and sha(index)==manifest['catalog_sha256'],'Pinned release catalog differs')
 for name,receipt in manifest['files'].items():
  path=base/name;require(path.is_file() and path.stat().st_size==receipt['bytes'] and sha(path)==receipt['sha256'],'Private release fixture missing or changed')
 require(len(manifest['cases'])==6 and sum(bool(c['negative']) for c in manifest['cases'])==2,'Required positive/negative corpus incomplete')
 status('native_score_cache_unit_tests',0,10)
 with (out/'score-cache-unit-tests.log').open('w') as log:
  subprocess.run([sys.executable,str(args.candidate_root/'tests/test_score_cache.py')],cwd=args.candidate_root,check=True,stdout=log,stderr=subprocess.STDOUT)
 snap=out/'source';cpp=snap/'cpp';cpp.mkdir(parents=True,exist_ok=True)
 original_cpp=args.candidate_root/'app/src/main/cpp'
 for p in original_cpp.iterdir():
  if p.suffix in {'.cpp','.h','.hpp'}:shutil.copy2(p,cpp/p.name);require(sha(p)==sha(cpp/p.name),'Source copy changed')
 classes=out/'java';classes.mkdir(exist_ok=True);java=pathlib.Path(config['java_home'])/'bin'
 jsrc=snap/'java';jsrc.mkdir(exist_ok=True)
 java_files=[args.candidate_root/'app/src/main/java/org/tangowiz/app/RecentNormalRecovery.java',args.candidate_root/'app/src/main/java/org/tangowiz/app/SongEndPredictor.java',REPO/'tests/host/RecentNormalRecoveryWorker.java',REPO/'tests/host/PhoneSongEndWorker.java']
 for p in java_files:shutil.copy2(p,jsrc/p.name)
 bridge=snap/'recovery_bridge.cpp';shutil.copy2(REPO/'tests/host/recovery_bridge.cpp',bridge)
 status('compile_release_native_regression')
 with (out/'compile.log').open('w') as log:
  for name,source,candidate in [('baseline',base/'baseline-native',False),('candidate',cpp,True)]:
   cmd=['g++','-std=c++17','-O3','-mssse3','-fno-fast-math','-ffp-contract=off','-shared','-fPIC','-pthread','-I',str(source)]
   if candidate:cmd+=['-DTANGO_RECOVERY_API']
   subprocess.run(cmd+[str(bridge),str(source/'recognition.cpp'),str(source/'scanner.cpp'),'-o',str(out/(name+'.so'))],check=True,stdout=log,stderr=subprocess.STDOUT)
  subprocess.run([str(java/'javac'),'-d',str(classes),*[str(p) for p in jsrc.glob('*.java')]],check=True,stdout=log,stderr=subprocess.STDOUT)
 dtype=np.dtype([('t','>f8'),('s','>f4'),('v','>f4',(128,))]);frames={c['name']:np.fromfile(base/c['file'],dtype=dtype) for c in manifest['cases']}
 require(sum(len(f) for f in frames.values())==manifest['expected_windows'],'Release corpus window count differs')
 require(all(np.all(f['s']==1) and np.all(np.isfinite(f['v'])) for f in frames.values()),'Invalid physical-phone embeddings')
 old=Native(out/'baseline.so',index);new=Native(out/'candidate.so',index,True)
 # Recent ranking must reuse exactly the stored evidence, and retaining it must preserve continuity.
 a=new.open();b=new.open()
 try:
  sample=frames['un_amor']
  for r in sample[:40]:new.search(a,r['v'],float(r['t']))
  recent=new.recent(a)
  for r in sample[30:40]:fresh=new.search(b,r['v'],float(r['t']))
  require(comparable(recent)==comparable(fresh),'Recent matching differs from ten fresh frames')
  require(abs(top(recent)['score']-new.lib.phone_recent(a,10))<1e-6,'Recent candidate differs from score guard')
  new.retain(a)
  for r in sample[40:50]:require(comparable(new.search(a,r['v'],float(r['t'])))==comparable(new.search(b,r['v'],float(r['t']))),'Rebased context loses subsequent matching continuity')
 finally:new.close(a);new.close(b)
 cases=[];done=0;total=manifest['expected_windows'];strong_count=0;replay_began=time.monotonic()
 for case in manifest['cases']:
  a=old.open();b=new.open();p0=Predictor(java,classes,'PhoneSongEndWorker');p1=Predictor(java,classes,'PhoneSongEndWorker');policy=Recovery(java,classes,'RecentNormalRecoveryWorker');first={};recoveries=[];lost=[];conflicts=[]
  try:
   for r in frames[case['name']]:
    t=float(r['t']);end=t+1.5
    if p0.reset(t)!='NONE':old.reset(a)
    if p1.reset(t)!='NONE':new.reset(b);policy.clear()
    x=old.search(a,r['v'],t);y=new.search(b,r['v'],t);before=top(x);after=top(y)
    if policy.check(t,after.get('score',0),y['frames']):
     short=new.recent(b)
     if policy.observe(t,short,after.get('score',0)):
      new.retain(b);y=short;after=top(y);p1.clear();policy.clear();recoveries.append(dict(audio_seconds=end,title=after.get('title'),orchestra=after.get('orchestra')))
    p0.observe(t,x);p1.observe(t,y)
    if x['frames']>=10 and before.get('score',0)>=.65:
     strong_count+=1
     if after.get('score',0)<.65:lost.append(dict(audio_seconds=end,before=before,after=after))
     aliases=bool(set(before.get('sourceIds',[before.get('id')]))&set(after.get('sourceIds',[after.get('id')])))
     if not aliases and (folded(before.get('title',''))!=folded(after.get('title','')) or folded(before.get('orchestra',''))!=folded(after.get('orchestra',''))):conflicts.append(dict(audio_seconds=end,before=before,after=after))
    for mode,result in [('baseline',x),('fixed',y)]:
     c=top(result)
     if result['frames']>=10 and c.get('score',0)>=.65:first.setdefault(mode+'|'+folded(c.get('title',''))+'|'+folded(c.get('orchestra','')),end)
    done+=1
    if done%100==0:
     elapsed=time.monotonic()-replay_began;status('replay_release_recovery_corpus',done,total,eta_seconds=(total-done)*elapsed/done,eta_scope='Native release replay only; final checks follow')
  finally:old.close(a);new.close(b);p0.close();p1.close();policy.close()
  result=dict(name=case['name'],windows=len(frames[case['name']]),recoveries=recoveries,lost_strong_windows=lost,conflicting_labels=conflicts,first_strong_titles=first,targets=[]);cases.append(result);save(out/'cases.json',cases)
  require(not lost and not conflicts,'Native recovery regressed previously strong recognition')
  require(not case['negative'] or not recoveries,'Recovery fired on an unmatched control')
  require(not case['negative'] or not any(k.startswith('fixed|') for k in first),'Strong recognition appeared on an unmatched control')
  for target in case['targets']:
   suffix=folded(target['title'])+'|'+folded(target['orchestra']);old_t=first.get('baseline|'+suffix);new_t=first.get('fixed|'+suffix)
   result['targets'].append(dict(title=target['title'],baseline_seconds=old_t,fixed_seconds=new_t))
   require(new_t is not None,'Expected recovery disappeared')
   if 'minimum_gain_seconds' in target:require(old_t is not None and old_t-new_t>=target['minimum_gain_seconds'],'Required recent-normal latency improvement disappeared')
   if 'latest_fixed_seconds' in target:require(new_t<=target['latest_fixed_seconds'],'Recovery missed the recorded session')
  save(out/'cases.json',cases)
 require(done==total and strong_count>=manifest['minimum_baseline_strong_windows'],'Release regression coverage incomplete')
 from validate_field_fixtures import validate as validate_saved_phone_field_fixtures
 status('replay_solo_tango_saved_phone_field_fixtures',0,9)
 solo_tango_field_report=validate_saved_phone_field_fixtures(config,out/'candidate.so')
 save(out/'solo-tango-field-report.json',solo_tango_field_report)
 require(signature(args.candidate_root)==args.source_signature,'Candidate sources changed during release validation')
 report=dict(passed=True,score_cache_unit_tests=10,solo_tango_field_cases=len(solo_tango_field_report['cases']),source_signature=args.source_signature,fixture_manifest_sha256=config['fixture_manifest_sha256'],index_bundle=manifest['index_bundle'],windows=done,baseline_strong_windows=strong_count,cases=cases,cpu_affinity=cores,host_wall_seconds=time.monotonic()-began,method='Exact production C++ and Java policy with saved phone embeddings; frozen legacy comparator and pinned index. No microphone audio or model inference. Alternate-speed asynchronous workers and physical-phone timing are outside this native regression. Emulator release checks remain separate.')
 save(out/'report.json',report);status('complete',done,total)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--config',type=pathlib.Path,default=pathlib.Path(os.environ.get('TANGOWIZ_RELEASE_FIXTURES',pathlib.Path.home()/'.config/tangowiz/recognition-release-fixtures.json')));parser.add_argument('--output',type=pathlib.Path,default=REPO/'build/reports/recognition-release');parser.add_argument('--candidate-root',type=pathlib.Path,default=REPO);parser.add_argument('--worker',action='store_true');parser.add_argument('--source-signature');args=parser.parse_args();args.candidate_root=args.candidate_root.resolve()
 require(args.config.is_file(),'Private recognition-release fixtures are not configured; see README release validation')
 config=read(args.config);require(config.get('version')==1,'Unsupported release fixture configuration');sys.path.insert(0,config['progress_tools'])
 from pipeline_progress import save
 if args.worker:
  args.output.mkdir(parents=True,exist_ok=True)
  try:worker(args,config,save)
  except BaseException:
   (args.output/'failure.log').write_text(traceback.format_exc());save(args.output/'report.json',dict(passed=False,source_signature=args.source_signature))
   last=read(args.output/'status.json') if (args.output/'status.json').exists() else {}
   if (args.output/'cases.json').exists():last['completed_units']=max(last.get('completed_units',0),sum(c['windows'] for c in read(args.output/'cases.json')))
   last.update(state='failed',updated_at=time.time(),workers=0);save(args.output/'status.json',last);raise
  return
 source=signature(args.candidate_root);run_id='android-release-recovery-'+time.strftime('%Y%m%dT%H%M%S')+'-'+uuid.uuid4().hex[:6];unit='tango-'+run_id+'.service';args.output=args.output.resolve()/run_id;args.output.mkdir(parents=True)
 save(args.output/'status.json',dict(state='queued',updated_at=time.time(),workers=0));progress=pathlib.Path(config['progress_tools'])/'pipeline_progress.py'
 subprocess.run([sys.executable,str(progress),'register','--id',run_id,'--title','Native recognition release regression','--status-file',str(args.output/'status.json'),'--service',unit],check=True,stdout=subprocess.DEVNULL)
 from collection_progress import Progress
 require(any(j['id']==run_id for j in Progress().snapshot()['jobs']),'Release regression not visible in progress dashboard')
 command=['systemd-run','--user','--quiet','--unit='+unit,'--property=WorkingDirectory='+str(REPO),'--property=CPUQuota=200%',sys.executable,str(progress),'run','--id',run_id,'--title','Native recognition release regression','--status-file',str(args.output/'status.json'),'--service',unit,'--',sys.executable,str(pathlib.Path(__file__).resolve()),'--worker','--config',str(args.config.resolve()),'--output',str(args.output),'--candidate-root',str(args.candidate_root),'--source-signature',source]
 subprocess.run(command,check=True)
 def push():
  try:
   result=subprocess.run([sys.executable,str(pathlib.Path(config['progress_tools'])/'progress_bridge.py'),'--once'],capture_output=True,text=True,timeout=20)
   print('Release progress bridge checked.' if result.returncode==0 else 'Release progress bridge unavailable; native tests continue.',flush=True)
  except (OSError,subprocess.TimeoutExpired):print('Release progress bridge unavailable; native tests continue.',flush=True)
 push();last='';began=time.monotonic()
 while True:
  state=subprocess.run(['systemctl','--user','show',unit,'-p','ActiveState','-p','Result'],capture_output=True,text=True,check=True).stdout
  status=read(args.output/'status.json');line=f"Native release regression: {status['state']} {status.get('completed_units',0)}/{status.get('total_units',1)}"
  if line!=last:print(line,flush=True);last=line
  if 'ActiveState=inactive' in state or 'ActiveState=failed' in state:break
  require(time.monotonic()-began<3600,'Native release regression exceeded one hour');time.sleep(5)
 report_path=args.output/'report.json';report=read(report_path) if report_path.exists() else {}
 fresh=signature(args.candidate_root)==source
 if not fresh:save(args.output/'status.json',dict(state='failed',updated_at=time.time(),workers=0));save(report_path,dict(passed=False,reason='sources_changed'))
 push();require('Result=success' in state and report.get('passed') and fresh,'Native recognition release regression failed; inspect its local report')
 save(args.output.parent/'latest.json',dict(run_id=run_id,report=report));print('Native recognition release gate passed.',flush=True)
if __name__=='__main__':
 try:main()
 except (GateFailure,OSError,ValueError,subprocess.SubprocessError) as e:print('Recognition release validation failed: '+str(e),file=sys.stderr);sys.exit(1)
