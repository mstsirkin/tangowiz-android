#!/usr/bin/env python3
"""Offline terminal-digital-silence measurements, pinned to exact catalog/source bytes.

Run under the tracked resource pipeline. Quiet fades/noisy tails require separate
musical review: this tool only trims uninterrupted <=-80 dBFS terminal audio.
All channels must be quiet. Full decoded duration prevents seek-timebase errors.
"""
import argparse,array,hashlib,json,math,mmap,struct,subprocess,os,tempfile
from pathlib import Path

def terminal_endpoint(samples,rate,channels,indexed_end):
    if rate<=0 or channels not in (1,2) or len(samples)%channels:return None
    frames=len(samples)//channels;duration=frames/rate
    if not math.isfinite(indexed_end) or indexed_end<=0 or not 0<=duration-indexed_end<=2:return None
    threshold=10**(-80/20);last=frames
    while last>0:
        values=samples[(last-1)*channels:last*channels]
        if any(not math.isfinite(x) or abs(x)>threshold for x in values):break
        last-=1
    # Digital tail must be at least two seconds; round later to the fingerprint grid.
    if last==0 or (frames-last)/rate<2:return None
    end=math.ceil(last/rate*2)/2
    return end if 0<end<indexed_end else None

def catalog_inventory(path):
    with open(path,'rb') as f,mmap.mmap(f.fileno(),0,access=mmap.ACCESS_READ) as m:
        magic=m[:8];assert magic in (b'TWIDX001',b'TWIDX002')
        header=struct.unpack_from('<8Q',m,8);count=header[6];stride=7 if magic==b'TWIDX002' else 6
        sections=[struct.unpack_from('<QQ',m,72+i*16) for i in range(9)]
        bounds=struct.unpack_from('<'+str(count+1)+'Q',m,sections[5][0]);strings=sections[8][0];rows={}
        for i in range(count):
            offset=struct.unpack_from('<I',m,sections[7][0]+i*stride*4)[0];start=strings+offset
            source=m[start:m.find(b'\0',start)].decode('utf-8');assert source not in rows
            rows[source]=(bounds[i+1]-bounds[i])*.5
        return rows

def write(path,value):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    fd,name=tempfile.mkstemp(dir=path.parent,prefix=path.name+'.')
    try:
        with os.fdopen(fd,'w') as f:json.dump(value,f,ensure_ascii=False,sort_keys=True);f.write('\n');f.flush();os.fsync(f.fileno())
        os.replace(name,path)
    finally:
        if os.path.exists(name):os.unlink(name)

def main():
    p=argparse.ArgumentParser();p.add_argument('--catalog',required=True);p.add_argument('--reference-manifest',required=True);p.add_argument('--output',required=True);p.add_argument('--identity');p.add_argument('--source-id',action='append');a=p.parse_args()
    catalog_hash=hashlib.file_digest(open(a.catalog,'rb'),'sha256').hexdigest();inventory=catalog_inventory(a.catalog)
    manifest=json.load(open(a.reference_manifest));rows={};receipt=[]
    identities=json.load(open(a.identity)) if a.identity else {}
    if identities:assert identities['catalog_sha256']==catalog_hash,'Identity proof belongs to another catalog'
    for reference in manifest['recordings']:
        source=reference['source_id']
        if source not in inventory or a.source_id and source not in a.source_id:continue
        row={};family=identities.get('sources',{}).get(source)
        if family:row['family']=family
        audio=Path(reference.get('audio_path') or '')
        item={'source_id':source,'state':'unavailable'}
        if audio.is_file() and reference.get('source_sha256'):
            digest=hashlib.file_digest(audio.open('rb'),'sha256').hexdigest()
            if digest!=reference['source_sha256']:raise ValueError('Source audio hash mismatch: '+source)
            probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','a:0','-show_entries','stream=channels','-of','json',str(audio)]))
            channels=probe['streams'][0]['channels'];item['state']='unsupported_channels'
            if channels in (1,2):
                raw=subprocess.check_output(['ffmpeg','-v','error','-threads','1','-i',str(audio),'-map','0:a:0','-ar','8000','-ac',str(channels),'-f','f32le','-'])
                samples=array.array('f');samples.frombytes(raw)
                import sys
                if sys.byteorder!='little':samples.byteswap()
                end=terminal_endpoint(samples,8000,channels,inventory[source]);item['state']='measured' if end is not None else 'no_safe_correction'
                if end is not None:row.update(musical_end_seconds=end,indexed_end_seconds=inventory[source],audio_sha256=digest)
        if row:rows[source]=row
        receipt.append(item)
    write(a.output,dict(version=1,catalog_sha256=catalog_hash,recordings=rows))
    write(a.output+'.receipt.json',dict(method='terminal-all-channel-digital-silence-v1',catalog_sha256=catalog_hash,records=receipt,identity_proof_sha256=hashlib.file_digest(open(a.identity,'rb'),'sha256').hexdigest() if a.identity else None))
if __name__=='__main__':main()
