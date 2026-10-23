#include "recognition.h"
#include "scanner_c.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <chrono>
#include <set>
#include <sstream>
#include <stdexcept>
#include <time.h>
#include <tuple>
#include <limits>
namespace tangowiz {
namespace {std::string quote(const std::string&s){std::ostringstream o;o<<'"';for(unsigned char c:s){if(c=='"'||c=='\\')o<<'\\'<<c;else if(c<32)o<<' ';else o<<c;}o<<'"';return o.str();}}
Engine::Engine(const uint8_t*b,uint64_t bytes):data(b),size(bytes){
 if(reinterpret_cast<uintptr_t>(b)%8)throw std::runtime_error("Catalog must be aligned to8bytes");
 if(bytes<4096)throw std::runtime_error("Catalog header is truncated");
 const bool v1=memcmp(b,"TWIDX001",8)==0,v2=memcmp(b,"TWIDX002",8)==0;
 if(!v1&&!v2)throw std::runtime_error("Invalid catalog magic");
 uint64_t h[8];memcpy(h,b+8,sizeof(h));if((v1&&h[0]!=1)||(v2&&h[0]!=2)||h[1]!=128||h[2]!=64||h[7]!=9)throw std::runtime_error("Unsupported catalog");
 formatVersion=h[0];metadataStride=v2?7:6;
 nlist=h[3];nblocks=h[4];ntotal=h[5];nrefs=h[6];if(!nlist||nlist>65536||!nblocks||nblocks>100000000||!nrefs||nrefs>10000000||ntotal>nblocks*16)throw std::runtime_error("Catalog dimensions");
 Slice s[9];memcpy(s,b+72,sizeof(s));for(auto&v:s)if(v.offset<4096||v.offset%4096||v.offset>bytes||v.size>bytes-v.offset)throw std::runtime_error("Catalog section bounds");
 uint64_t expected[]={nblocks*64*16,nblocks*16*8,(nlist+1)*8,64*256*2*4,nlist*128*4,(nrefs+1)*8,ntotal*4,nrefs*metadataStride*4,s[8].size};for(int i=0;i<9;i++)if(s[i].size!=expected[i]||(i&&s[i].offset<s[i-1].offset+s[i-1].size))throw std::runtime_error("Catalog shape mismatch");
 codes=b+s[0].offset;ids=(const int64_t*)(b+s[1].offset);offsets=(const int64_t*)(b+s[2].offset);pq=(const float*)(b+s[3].offset);coarse=(const float*)(b+s[4].offset);bounds=(const int64_t*)(b+s[5].offset);reverse=(const uint32_t*)(b+s[6].offset);metadata=(const uint32_t*)(b+s[7].offset);strings=(const char*)(b+s[8].offset);stringsize=s[8].size;
 if(offsets[0]!=0||offsets[nlist]!=int64_t(nblocks)||bounds[0]!=0||bounds[nrefs]!=int64_t(ntotal)||!stringsize||strings[stringsize-1])throw std::runtime_error("Catalog terminal bounds");
 for(uint64_t i=1;i<=nlist;i++)if(offsets[i]<offsets[i-1])throw std::runtime_error("Catalog list order");for(uint64_t i=1;i<=nrefs;i++)if(bounds[i]<bounds[i-1])throw std::runtime_error("Catalog recording order");
 for(uint64_t track=0;track<nrefs;track++){
  const auto*row=metadataRow(track);for(int field=0;field<6;field++)if(row[field]>=stringsize)throw std::runtime_error("Catalog string bounds");
  if(v2&&row[6])groupMembers.push_back((uint64_t(row[6])<<32)|track);
 }
 if(v2)std::sort(groupMembers.begin(),groupMembers.end());
}
std::string Engine::text(uint32_t at)const {const char*p=strings+at;const void*end=memchr(p,0,stringsize-at);if(!end)throw std::runtime_error("Unterminated string");return std::string(p,(const char*)end-p);}
std::vector<int64_t> Engine::neighbors(const float*q,std::vector<float>*output){
 for(int d=0;d<128;d++)if(!std::isfinite(q[d]))throw std::runtime_error("Nonfinite embedding");
 // Exact coarse IP selection; deterministic exhaustive scan of4096 centroids.
 std::vector<std::pair<float,int64_t>> nearest;nearest.reserve(nlist);
 for(uint64_t i=0;i<nlist;i++){float dot=0;for(int d=0;d<128;d++)dot+=q[d]*coarse[i*128+d];nearest.emplace_back(-dot,i);}
 int probes=std::min<uint64_t>(40,nlist);std::partial_sort(nearest.begin(),nearest.begin()+probes,nearest.end());
 std::vector<int64_t> lists(probes);std::vector<float> biases(probes);for(int j=0;j<probes;j++){lists[j]=nearest[j].second;float v=0;for(int d=0;d<128;d++)v+=q[d]*coarse[lists[j]*128+d];biases[j]=v;}
 float lut[64*256];for(int j=0;j<64;j++)for(int k=0;k<256;k++)lut[j*256+k]=q[j*2]*pq[(j*256+k)*2]+q[j*2+1]*pq[(j*256+k)*2+1];
 std::vector<int64_t> result(20);std::vector<float> scores(20);uint64_t stats[4];int rc=pq8_scan(codes,ids,offsets,nblocks,nlist,64,lut,lists.data(),biases.data(),probes,20,1,6,scores.data(),result.data(),stats);if(rc)throw std::runtime_error("PQ scanner rejected inputs");if(output)*output=scores;return result;
}
float Engine::similarity(const float*q,uint64_t id)const {
 if(id>=ntotal)return -1;uint64_t slot=reverse[id];if(slot>=nblocks*16)return -1;uint64_t block=slot/16,lane=slot%16;auto list=std::upper_bound(offsets,offsets+nlist+1,(int64_t)block)-offsets-1;float sum=0;for(int d=0;d<128;d++)sum+=q[d]*coarse[list*128+d];for(int j=0;j<64;j++){int code=codes[(block*64+j)*16+lane];sum+=q[j*2]*pq[(j*256+code)*2]+q[j*2+1]*pq[(j*256+code)*2+1];}return sum;
}
bool Engine::reconstruct(uint64_t id,float*out)const {
 if(id>=ntotal)return false;
 const uint64_t slot=reverse[id];if(slot>=nblocks*16)return false;
 const uint64_t block=slot/16,lane=slot%16;
 const auto list=std::upper_bound(offsets,offsets+nlist+1,int64_t(block))-offsets-1;
 if(list<0||list>=int64_t(nlist))return false;
 for(int j=0;j<64;j++){
  const int code=codes[(block*64+j)*16+lane];
  for(int d=0;d<2;d++){out[j*2+d]=coarse[list*128+j*2+d]+pq[(j*256+code)*2+d];if(!std::isfinite(out[j*2+d]))return false;}
 }
 return true;
}
DuplicateEvidence Engine::duplicate(const Ranked&a,const Ranked&b)const {
 if(a.track>=nrefs||b.track>=nrefs||std::abs(a.speed-b.speed)>.0001f)return {};
 const Ranked*first=&a,*second=&b;if(first->track>second->track)std::swap(first,second);
 const int64_t delta=int64_t(second->start)-bounds[second->track]-(int64_t(first->start)-bounds[first->track]);
 const auto key=std::make_tuple(first->track,second->track,delta);
 auto found=duplicateCache.find(key);if(found!=duplicateCache.end())return found->second;
 const uint64_t ai=first->track,bi=second->track;
 auto evidence=verifyReferenceOverlap(bounds[ai+1]-bounds[ai],bounds[bi+1]-bounds[bi],delta,
  [&](int which,int64_t frame,float*out){const uint64_t track=which?bi:ai;if(frame<0||frame>=bounds[track+1]-bounds[track])return false;return reconstruct(bounds[track]+frame,out);});
 if(duplicateCache.size()>=2048)duplicateCache.clear();
 duplicateCache.emplace(key,evidence);return evidence;
}
std::string Engine::search(const float*e,int count,float speed,float offset){
 timespec cpuBegin{};clock_gettime(CLOCK_THREAD_CPUTIME_ID,&cpuBegin);auto began=std::chrono::steady_clock::now();if(count<1||count>64||!std::isfinite(speed)||speed<.5||speed>2||!std::isfinite(offset))throw std::runtime_error("Invalid query batch");int key=std::lround(speed*10000);if(lastOffset.count(key)&&std::abs(offset-(lastOffset[key]+.5f*speed))>.1f){streams[key].clear();totals[key]=0;ranked.erase(key);}lastOffset[key]=offset+(count-1)*.5f*speed;latestOffset=std::max(latestOffset,lastOffset[key]);for(auto it=lastOffset.begin();it!=lastOffset.end();){if(latestOffset-it->second>30.f){streams.erase(it->first);ranked.erase(it->first);totals.erase(it->first);it=lastOffset.erase(it);}else ++it;}auto&frames=streams[key];auto lookupBegin=std::chrono::steady_clock::now();for(int i=0;i<count;i++){Frame f;f.vector.assign(e+i*128,e+(i+1)*128);f.neighbors=neighbors(f.vector.data());frames.push_back(std::move(f));totals[key]++;}auto lookupEnd=std::chrono::steady_clock::now();if(frames.size()>60)frames.erase(frames.begin(),frames.end()-60);
 using Hit=Ranked;std::map<uint64_t,Hit> best;
 for(auto&st:streams){if(st.first!=key)continue;auto&fs=st.second;int n=fs.size();std::set<int64_t> starts;for(int j=0;j<n;j++)for(auto id:fs[j].neighbors)if(id>=j&&id-j+n<=int64_t(ntotal))starts.insert(id-j);
 for(auto start:starts){uint64_t tr=std::upper_bound(bounds,bounds+nrefs+1,start)-bounds-1;if(tr>=nrefs||start+n>bounds[tr+1])continue;float score=0;for(int j=0;j<n;j++)score+=similarity(fs[j].vector.data(),start+j);score/=n;auto it=best.find(tr);if(it==best.end()||score>it->second.score)best[tr]={score,tr,(uint64_t)start,st.first/10000.f};}}
 auto&updated=ranked[key];updated.clear();for(auto&h:best)updated.push_back(h.second);best.clear();size_t maxFrames=0;for(auto&s:streams)maxFrames=std::max(maxFrames,s.second.size());for(auto&stream:ranked){if(streams[stream.first].size()*5<maxFrames*4||latestOffset-lastOffset[stream.first]>3.f)continue;for(auto&h:stream.second){auto it=best.find(h.track);if(it==best.end()||h.score>it->second.score)best[h.track]=h;}}std::vector<Hit> hits;for(auto&h:best)hits.push_back(h.second);std::sort(hits.begin(),hits.end(),[](const Hit&a,const Hit&b){return a.score!=b.score?a.score>b.score:a.track<b.track;});
 // Group only direct, sustained reference fingerprint matches to the strongest
 // anchor. Candidate ranking scores and database identities remain untouched.
 auto groupingBegin=std::chrono::steady_clock::now();
 struct Group {Hit anchor;std::vector<Hit> members;std::vector<DuplicateEvidence> evidence;std::vector<uint64_t> metadataTracks;};
 std::vector<Group> groups;
 if(formatVersion==2){
  std::map<uint64_t,size_t> groupIndex;
  for(const auto&hit:hits){
   const uint32_t group=metadataRow(hit.track)[6];
   const uint64_t identity=group?uint64_t(group):(uint64_t(1)<<32)|hit.track;
   auto found=groupIndex.find(identity);
   if(found!=groupIndex.end()){groups[found->second].members.push_back(hit);continue;}
   if(groups.size()>=16)continue;
   Group g{hit,{hit},{},{}};
   if(group){const uint64_t first=uint64_t(group)<<32;
    for(auto it=std::lower_bound(groupMembers.begin(),groupMembers.end(),first);it!=groupMembers.end()&&uint32_t(*it>>32)==group;it++)g.metadataTracks.push_back(uint32_t(*it));
   }else g.metadataTracks.push_back(hit.track);
   groupIndex.emplace(identity,groups.size());groups.push_back(std::move(g));
  }
 }else{
  for(size_t i=0;i<std::min<size_t>(64,hits.size());i++){
   const auto&hit=hits[i];bool joined=false;
   for(auto&g:groups){auto evidence=duplicate(g.anchor,hit);if(evidence.matched){g.members.push_back(hit);g.evidence.push_back(evidence);g.metadataTracks.push_back(hit.track);joined=true;break;}}
   if(!joined&&groups.size()<16)groups.push_back({hit,{hit},{},{hit.track}});
  }
 }
 const auto groupingEnd=std::chrono::steady_clock::now();
 std::ostringstream out;out<<"{\"catalogFormat\":"<<formatVersion<<",\"matcher\":\"pq8-sequence-reconstruction\",\"calibrated\":false,\"candidates\":[";
 for(size_t index=0;index<std::min<size_t>(5,groups.size());index++){
  const auto&g=groups[index];const auto&h=g.anchor;auto*m=metadataRow(h.track);
  std::string orch=text(m[2]);if(orch.empty())orch=text(m[5]);
  std::set<std::string> sourceIds;std::array<std::set<std::string>,4> alternatives;
  for(auto track:g.metadataTracks){auto*mm=metadataRow(track);sourceIds.insert(text(mm[0]));std::string orchestra=text(mm[2]);if(orchestra.empty())orchestra=text(mm[5]);if(!orchestra.empty())alternatives[0].insert(orchestra);if(mm[1])alternatives[1].insert(text(mm[1]));if(mm[3])alternatives[2].insert(text(mm[3]));if(mm[4])alternatives[3].insert(text(mm[4]));}
  if(index)out<<',';
  out<<"{\"id\":"<<quote(text(m[0]))<<",\"title\":"<<quote(text(m[1]))<<",\"orchestra\":"<<quote(orch)<<",\"singer\":"<<quote(text(m[3]))<<",\"year\":"<<quote(text(m[4]))<<",\"score\":"<<h.score<<",\"confidence\":"<<std::clamp(h.score,0.f,1.f)<<",\"referenceSeconds\":"<<(h.start-bounds[h.track])*.5<<",\"speed\":"<<h.speed<<",\"groupId\":"<<quote("acoustic:"+*sourceIds.begin())<<",\"groupSize\":"<<sourceIds.size()<<",\"sourceIds\":[";
  int item=0;for(auto&id:sourceIds){if(item++)out<<',';out<<quote(id);}out<<"],\"metadataAlternatives\":{";
  const char*names[]={"orchestra","title","singer","year"};for(int field=0;field<4;field++){if(field)out<<',';out<<quote(names[field])<<":[";item=0;for(auto&value:alternatives[field]){if(item++)out<<',';out<<quote(value);}out<<']';}
  out<<"},\"sourceMetadata\":[";for(size_t j=0;j<g.metadataTracks.size();j++){if(j)out<<',';const auto track=g.metadataTracks[j];auto*mm=metadataRow(track);std::string orchestra=text(mm[2]);if(orchestra.empty())orchestra=text(mm[5]);out<<"{\"id\":"<<quote(text(mm[0]))<<",\"title\":"<<quote(text(mm[1]))<<",\"orchestra\":"<<quote(orchestra)<<",\"singer\":"<<quote(text(mm[3]))<<",\"year\":"<<quote(text(mm[4]))<<",\"scored\":"<<(best.count(track)?"true":"false")<<",\"score\":";if(best.count(track))out<<best.at(track).score;else out<<"null";out<<'}';}
  out<<"],\"groupingEvidence\":{\"method\":"<<quote(formatVersion==2?"catalog_duplicate_group_v2":"reference_pq_cosine_v1")<<",\"pairs\":[";
  for(size_t j=0;j<g.evidence.size();j++){if(j)out<<',';const auto&v=g.evidence[j];out<<"{\"anchorId\":"<<quote(text(m[0]))<<",\"sourceId\":"<<quote(text(metadataRow(g.members[j+1].track)[0]))<<",\"meanCosine\":"<<v.mean<<",\"p10Cosine\":"<<v.p10<<",\"overlapSeconds\":"<<v.overlapSeconds<<",\"samples\":"<<v.samples<<'}';}out<<"]}}";
 }
 out<<"],\"rawCandidates\":[";for(size_t i=0;i<std::min<size_t>(20,hits.size());i++){if(i)out<<',';auto&h=hits[i];out<<"{\"id\":"<<quote(text(metadataRow(h.track)[0]))<<",\"score\":"<<h.score<<",\"speed\":"<<h.speed<<",\"referenceSeconds\":"<<(h.start-bounds[h.track])*.5<<'}';}timespec cpuEnd{};clock_gettime(CLOCK_THREAD_CPUTIME_ID,&cpuEnd);out<<"],\"groupingMs\":"<<std::chrono::duration<double,std::milli>(groupingEnd-groupingBegin).count()<<",\"lookupMs\":"<<std::chrono::duration<double,std::milli>(lookupEnd-lookupBegin).count()<<",\"cpuMs\":"<<((cpuEnd.tv_sec-cpuBegin.tv_sec)*1000.+(cpuEnd.tv_nsec-cpuBegin.tv_nsec)/1e6)<<",\"elapsedMs\":"<<std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-began).count()<<",\"frames\":"<<frames.size()<<'}';return out.str();
}
double Engine::recent(int count)const {
 if(count<1||count>60)throw std::runtime_error("Invalid recent window count");
 auto stream=streams.find(10000);if(stream==streams.end()||stream->second.size()<(size_t)count)return std::numeric_limits<double>::quiet_NaN();
 const auto&frames=stream->second;size_t first=frames.size()-count;std::set<int64_t> starts;
 for(int j=0;j<count;j++)for(auto id:frames[first+j].neighbors)if(id>=j&&id-j+count<=int64_t(ntotal))starts.insert(id-j);
 float best=-std::numeric_limits<float>::infinity();
 for(auto start:starts){uint64_t tr=std::upper_bound(bounds,bounds+nrefs+1,start)-bounds-1;if(tr>=nrefs||start+count>bounds[tr+1])continue;float sum=0;for(int j=0;j<count;j++)sum+=similarity(frames[first+j].vector.data(),start+j);best=std::max(best,sum/count);}
 return best;
}

}
