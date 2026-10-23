#include "recognition.h"
#include <array>
#include <cassert>
#include <cstring>
#include <iostream>
#include <fstream>
#include <stdexcept>
#include <vector>
using namespace tangowiz;
struct Fixture {
 std::vector<uint8_t> bytes;
 std::array<Slice,9> section{};
 int version;
 explicit Fixture(int v,uint32_t ga=42,uint32_t gb=42):version(v){
  bytes.resize(4096);std::memcpy(bytes.data(),v==1?"TWIDX001":"TWIDX002",8);
  uint64_t header[]={uint64_t(v),128,64,1,4,64,2,9};std::memcpy(bytes.data()+8,header,sizeof(header));
  std::vector<uint8_t> codes(4*64*16);for(int block=0;block<4;block++)for(int lane=0;lane<16;lane++)codes[block*64*16+lane]=block<2?1:2;
  add(0,codes.data(),codes.size());
  std::array<int64_t,64> ids;for(int i=0;i<64;i++)ids[i]=i;add(1,ids.data(),sizeof(ids));
  int64_t offsets[]={0,4};add(2,offsets,sizeof(offsets));
  std::vector<float> pq(64*256*2);pq[2]=1;pq[4]=-1;add(3,pq.data(),pq.size()*4);
  std::array<float,128> coarse{};add(4,coarse.data(),sizeof(coarse));
  int64_t bounds[]={0,32,64};add(5,bounds,sizeof(bounds));
  std::array<uint32_t,64> reverse;for(int i=0;i<64;i++)reverse[i]=i;add(6,reverse.data(),sizeof(reverse));
  std::string strings(1,'\0');auto text=[&](const char*s){uint32_t at=strings.size();strings+=s;strings+='\0';return at;};
  const uint32_t a=text("source-a"),b=text("source-b"),title=text("Example"),orch=text("Example Ensemble"),singer=text("Example Singer"),year=text("1949"),date=text("1949-07-20");
  std::vector<uint32_t> rows;for(int i=0;i<2;i++){for(auto value:{i?b:a,title,orch,i?singer:0u,i?date:year,0u})rows.push_back(value);if(v==2)rows.push_back(i?gb:ga);}
  add(7,rows.data(),rows.size()*4);add(8,strings.data(),strings.size());
  std::memcpy(bytes.data()+72,section.data(),sizeof(section));
 }
 void add(int index,const void*p,size_t size){bytes.resize((bytes.size()+4095)/4096*4096);section[index]={bytes.size(),size};auto*b=static_cast<const uint8_t*>(p);bytes.insert(bytes.end(),b,b+size);}
 void row(int index,int field,uint32_t value){std::memcpy(bytes.data()+section[7].offset+(index*(version==1?6:7)+field)*4,&value,4);}
 void reject(){bool failed=false;try{Engine e(bytes.data(),bytes.size());}catch(const std::runtime_error&){failed=true;}assert(failed);}
 std::string query(){Engine e(bytes.data(),bytes.size());std::array<float,384> q{};q[0]=q[128]=q[256]=1;return e.search(q.data(),3,1,0);}
};
int main(int argc,char**argv){
 Fixture v1(1);auto old=v1.query();assert(old.find("\"groupSize\":1")!=std::string::npos);assert(old.find("reference_pq_cosine_v1")!=std::string::npos);
 Fixture v2(2);auto current=v2.query();assert(current.find("\"groupSize\":2")!=std::string::npos);assert(current.find("\"sourceIds\":[\"source-a\",\"source-b\"]")!=std::string::npos);assert(current.find("Example Singer")!=std::string::npos);assert(current.find("1949-07-20")!=std::string::npos);assert(current.find("\"scored\":false,\"score\":null")!=std::string::npos);assert(current.find("catalog_duplicate_group_v2")!=std::string::npos);assert(current.find("\"score\":1,")!=std::string::npos);
 if(argc>1){std::ofstream out(argv[1],std::ios::binary);out.write(reinterpret_cast<const char*>(v2.bytes.data()),v2.bytes.size());}
 Fixture maximum(2,UINT32_MAX,UINT32_MAX);assert(maximum.query().find("\"groupSize\":2")!=std::string::npos);
 Fixture zero(2,0,0);assert(zero.query().find("\"groupSize\":1")!=std::string::npos);assert(zero.query().find("Example Singer")==std::string::npos);
 Fixture distinct(2,1,2);assert(distinct.query().find("\"groupSize\":1")!=std::string::npos);
 Fixture wrongVersion(1);wrongVersion.bytes[8]=2;wrongVersion.reject();
 Fixture wrongMagicVersion(2);wrongMagicVersion.bytes[8]=1;wrongMagicVersion.reject();
 Fixture wrongStride(2);uint64_t shortRows=48;std::memcpy(wrongStride.bytes.data()+72+7*16+8,&shortRows,8);wrongStride.reject();
 Fixture badString(2);badString.row(1,3,UINT32_MAX);badString.reject();
 Fixture shortFile(2);shortFile.bytes.resize(4096);shortFile.reject();
 std::cout<<"PASS: V1/V2 compatibility, whole-group unranked metadata, sparse/zero/maximum IDs, unchanged score, malformed versions/stride/strings/truncation\n";
}
