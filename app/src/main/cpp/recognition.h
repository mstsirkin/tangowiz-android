#pragma once
#include <cstdint>
#include <string>
#include <vector>
#include <map>
#include <tuple>
#include "reference_grouping.h"
namespace tangowiz {
struct Slice {uint64_t offset,size;};
struct Frame {std::vector<float> vector;std::vector<int64_t> neighbors;};
class Engine {
 const uint8_t* data;uint64_t size;uint64_t nlist,nblocks,ntotal,nrefs;
 const uint8_t* codes;const int64_t* ids;const int64_t* offsets;const float* pq;const float* coarse;const int64_t* bounds;const uint32_t* reverse;const uint32_t* metadata;const char* strings;uint64_t stringsize;
 uint32_t metadataStride=6;
 uint64_t formatVersion=1;
 std::vector<uint64_t> groupMembers;
 std::map<int,std::vector<Frame>> streams;
 std::map<int,uint64_t> totals;
 std::map<int,float> lastOffset;
 float latestOffset=-1;
 struct Ranked {float score;uint64_t track,start;float speed;};
 std::map<int,std::vector<Ranked>> ranked;
 mutable std::map<std::tuple<uint64_t,uint64_t,int64_t>,DuplicateEvidence> duplicateCache;
 public: Engine(const uint8_t* base,uint64_t bytes); std::vector<int64_t> neighbors(const float*q,std::vector<float>*scores=nullptr);std::string search(const float*e,int count,float speed,float offset);double recent(int count)const;void reset(){streams.clear();totals.clear();ranked.clear();lastOffset.clear();latestOffset=-1;}
 private:const uint32_t* metadataRow(uint64_t track)const{return metadata+track*metadataStride;}
 bool reconstruct(uint64_t id,float*out)const;DuplicateEvidence duplicate(const Ranked&a,const Ranked&b)const;float similarity(const float*q,uint64_t id)const;std::string text(uint32_t offset)const;
};
}
