#include "reference_grouping.h"
#include <cassert>
#include <iostream>
#include <limits>
#include <random>
using namespace tangowiz;
int main(){
 auto identical=[](int,int64_t frame,float*out){out[frame%128]=1;return true;};
 auto same=verifyReferenceOverlap(380,380,0,identical);assert(same.matched&&same.samples==48&&same.mean>.9999);
 // Padding and a fixed reference offset do not change performance identity.
 auto shifted=[](int which,int64_t frame,float*out){out[(frame-(which?3:0))%128]=1;return true;};
 auto pad=verifyReferenceOverlap(380,385,3,shifted);assert(pad.matched);
 assert(!verifyReferenceOverlap(40,40,0,identical).matched); // Not enough evidence.
 assert(!verifyReferenceOverlap(380,380,150,identical).matched); // Partial repeated passage.
 auto commonIntro=[](int which,int64_t frame,float*out){out[0]=1;if(which&&frame>40){out[0]=.6f;out[1]=.8f;}return true;};
 assert(!verifyReferenceOverlap(380,380,0,commonIntro).matched); // Same title/melody cannot bypass audio disagreement.
 auto differentYear=[](int which,int64_t,float*out){out[0]=which?.8f:1.f;out[1]=which?.6f:0.f;return true;};
 assert(!verifyReferenceOverlap(380,380,0,differentYear).matched);
 auto invalid=[](int,int64_t,float*out){out[0]=std::numeric_limits<float>::quiet_NaN();return true;};
 assert(!verifyReferenceOverlap(380,380,0,invalid).matched);
 auto zero=[](int,int64_t,float*){return true;};assert(!verifyReferenceOverlap(380,380,0,zero).matched);
 // A-B and B-C can agree while A-C disagree: a group must test its anchor,
 // never infer equivalence through the intermediate reference.
 auto angles=[](float a,float b){return [=](int which,int64_t,float*out){float t=which?b:a;out[0]=std::cos(t);out[1]=std::sin(t);return true;};};
 assert(verifyReferenceOverlap(380,380,0,angles(0,.28f)).matched);
 assert(verifyReferenceOverlap(380,380,0,angles(.28f,.56f)).matched);
 assert(!verifyReferenceOverlap(380,380,0,angles(0,.56f)).matched);
 // Exact early rejection must preserve the exhaustive48-sample decision.
 std::mt19937 random(29);std::normal_distribution<float> noise(0,.035f);
 for(int trial=0;trial<300;trial++){
  std::array<float,600> cosine;for(auto&v:cosine)v=std::clamp(.93f+(trial%7)*.005f+noise(random),-1.f,1.f);
  auto loader=[&](int which,int64_t frame,float*out){if(!which)out[0]=1;else{out[0]=cosine[frame];out[1]=std::sqrt(std::max(0.f,1-out[0]*out[0]));}return true;};
  bool oracle=false;
  for(int shift=-1;shift<=1;shift++){
   int begin=std::max(0,-shift),end=std::min(600,600-shift),first=begin+4,last=end-5;
   std::vector<double> values;double sum=0;
   for(int j=0;j<48;j++){int frame=first+(last-first)*j/47;float c=cosine[frame+shift];float sine=std::sqrt(std::max(0.f,1-c*c));double value=c/std::sqrt(double(c)*c+double(sine)*sine);values.push_back(value);sum+=value;}
   std::sort(values.begin(),values.end());oracle|=sum/48>=.95&&values[4]>=.93;
  }
  assert(verifyReferenceOverlap(600,600,0,loader).matched==oracle);
 }
 std::cout<<"PASS: duplicates, padding, minimum duration, coverage, different performances, finite norms, nontransitive controls\n";
}
