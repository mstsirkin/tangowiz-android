#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <vector>

namespace tangowiz {
struct DuplicateEvidence {
 bool matched=false;
 double mean=0,p10=0,overlapSeconds=0;
 int samples=0;
 int64_t delta=0;
};
// A conservative, bounded reference-to-reference check. This does not inspect
// metadata or query scores, and never follows transitive relationships.
// load(which,localFrame,out128) reconstructs one reference embedding.
template<class Loader>
DuplicateEvidence verifyReferenceOverlap(int64_t aCount,int64_t bCount,
                                         int64_t proposedDelta,Loader load) {
 DuplicateEvidence best;
 if(aCount<60||bCount<60)return best;
 for(int shift=-1;shift<=1;shift++) {
  const int64_t delta=proposedDelta+shift;
  const int64_t begin=std::max<int64_t>(0,-delta);
  const int64_t end=std::min(aCount,bCount-delta);
  const int64_t overlap=end-begin;
  if(overlap<60||double(overlap)/std::min(aCount,bCount)<.8)continue;
  // Ignore two seconds at either edge to avoid leading/trailing padding.
  const int64_t first=begin+4,last=end-5;
  if(last<first)continue;
  std::vector<double> similarities;
  bool valid=true;double runningSum=0;int belowP10=0;
  for(int i=0;i<48;i++) {
   const int64_t frame=first+(last-first)*i/47;
   std::array<float,128>a{},b{};
   if(!load(0,frame,a.data())||!load(1,frame+delta,b.data())){valid=false;break;}
   double dot=0,aa=0,bb=0;
   for(int d=0;d<128;d++){dot+=double(a[d])*b[d];aa+=double(a[d])*a[d];bb+=double(b[d])*b[d];}
   if(!std::isfinite(dot)||!std::isfinite(aa)||!std::isfinite(bb)||aa<=1e-12||bb<=1e-12){valid=false;break;}
   const double cosine=dot/std::sqrt(aa*bb);
   if(!std::isfinite(cosine)){valid=false;break;}
   const double value=std::clamp(cosine,-1.,1.);
   similarities.push_back(value);runningSum+=value;if(value<.93)belowP10++;
   // Exact rejection bounds: even perfect remaining samples cannot recover
   // the required mean, or five low samples already violate the p10 gate.
   if(runningSum+(48-similarities.size())<.95*48||belowP10>4){valid=false;break;}
  }
  if(!valid||similarities.size()!=48)continue;
  double mean=0;for(double s:similarities)mean+=s;mean/=similarities.size();
  std::sort(similarities.begin(),similarities.end());
  const double p10=similarities[(similarities.size()-1)/10];
  if(mean>=.95&&p10>=.93&&(!best.matched||mean>best.mean))
   best={true,mean,p10,overlap*.5,48,delta};
 }
 return best;
}
}
