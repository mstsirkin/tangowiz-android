#pragma once
#ifndef TANGO_SCORE_CACHE
#define TANGO_SCORE_CACHE 1
#endif
#include <cstdint>
#include <string>
#include <vector>
#include <map>
#include <tuple>
#if TANGO_SCORE_CACHE
#include <unordered_map>
#endif
#include <chrono>
#include <time.h>
#include "reference_grouping.h"
namespace tangowiz {
struct Slice {uint64_t offset,size;};
struct Frame {std::vector<float> vector;std::vector<int64_t> neighbors;
#if TANGO_SCORE_CACHE
 std::unordered_map<uint64_t,float> scoreCache;
#endif
};
class Engine {
#ifdef TANGO_SCORE_CACHE_TESTING
 friend struct ScoreCacheTestProbe;
 uint64_t scoreCacheHits=0,scoreCacheMisses=0;
#endif
#if TANGO_SCORE_CACHE
#ifndef TANGO_SCORE_CACHE_LIMIT
#define TANGO_SCORE_CACHE_LIMIT 4096
#endif
 static_assert(TANGO_SCORE_CACHE_LIMIT>0,"Score cache bound must be positive");
 float cachedSimilarity(Frame& frame,uint64_t id);
#endif
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
 public: Engine(const uint8_t* base,uint64_t bytes); std::vector<int64_t> neighbors(const float*q,std::vector<float>*scores=nullptr);std::string search(const float*e,int count,float speed,float offset);double recent(int count)const;
 std::string recentResult(int count);void retainRecent(int count);
 void reset(){streams.clear();totals.clear();ranked.clear();lastOffset.clear();latestOffset=-1;}
 private:const uint32_t* metadataRow(uint64_t track)const{return metadata+track*metadataStride;}
 bool reconstruct(uint64_t id,float*out)const;DuplicateEvidence duplicate(const Ranked&a,const Ranked&b)const;float similarity(const float*q,uint64_t id)const;std::string text(uint32_t offset)const;
 std::string render(const std::map<uint64_t,Ranked>&best,size_t frameCount,std::chrono::steady_clock::time_point began,double lookupMs,timespec cpuBegin);
};
}
