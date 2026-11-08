#include "recognition.h"
#include <algorithm>
#include "recovery_bridge.cpp"
namespace tangowiz {
struct ScoreCacheTestProbe {
 static uint64_t stats(Engine& e,int which){
  if(which==0)return e.scoreCacheHits;
  if(which==1)return e.scoreCacheMisses;
  uint64_t value=0;
  for(auto&stream:e.streams)for(auto&frame:stream.second){
   if(which==4){++value;continue;}
#ifdef TANGO_SCORE_CACHE
   if(which==2)value+=frame.scoreCache.size();
   if(which==3)value=std::max<uint64_t>(value,frame.scoreCache.size());
#endif
  }
  return value;
 }
};
}
extern "C" uint64_t phone_cache_stats(void*h,int which){return tangowiz::ScoreCacheTestProbe::stats(*((Handle*)h)->engine,which);}
