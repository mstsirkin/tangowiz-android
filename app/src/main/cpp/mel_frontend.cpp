#include "mel_frontend.h"
#include "mel_coefficients.h"
#include <algorithm>
#include <array>
#include <cmath>
#include <complex>
#include <map>
namespace tangowiz {
namespace {
constexpr double pi=3.14159265358979323846;
void fft(std::array<std::complex<double>,1024>& x) {
 for(int i=1,j=0;i<1024;++i){int b=512;for(;j&b;b>>=1)j^=b;j^=b;if(i<j)std::swap(x[i],x[j]);}
 for(int n=2;n<=1024;n*=2){auto w0=std::polar(1.0,-2*pi/n);for(int s=0;s<1024;s+=n){std::complex<double>w(1);for(int k=0;k<n/2;++k){auto a=x[s+k],b=x[s+k+n/2]*w;x[s+k]=a+b;x[s+k+n/2]=a-b;w*=w0;}}}
}
}
void computeMel(const float* pcm,float* output) {
 std::fill(output,output+256*33,0.f);
 for(int t=0;t<33;++t){
  std::array<std::complex<double>,1024>x;
  for(int j=0;j<1024;++j){int pos=t*256+j-512;x[j]=pos>=0&&pos<8000?double(pcm[pos])*kHann[j]:0.;}
  fft(x);std::array<float,513>spec;
  for(int j=0;j<513;++j)spec[j]=float(std::abs(x[j]));
  for(auto w:kMelWeights)output[w.band*33+t]+=w.weight*spec[w.bin];
 }
 float maxv=1e-5f;for(int i=0;i<8448;++i){output[i]=std::max(output[i],1e-5f);maxv=std::max(maxv,output[i]);}
 for(int i=0;i<8448;++i)output[i]=1.f+std::max(-80.f,20.f*std::log10(output[i]/maxv))/40.f;
}
void resampleWindow(const float* input,size_t count,double start,double speed,float* output) {
 if(speed==1. && start==std::floor(start) && start>=0 && start+8000<=count){std::copy(input+size_t(start),input+size_t(start)+8000,output);return;}
 // Call on the recognition worker only. Cache one interpolation table per speed.
 static thread_local std::map<int,std::vector<float>> tables;
 int key=int(std::round(speed*100000));auto& weights=tables[key];
 constexpr int phases=1024,taps=64,radius=32;
 if(weights.empty()){
  weights.resize(phases*taps);double cutoff=.5/std::max(1.,speed);
  for(int p=0;p<phases;++p){double fraction=double(p)/phases,norm=0;
   for(int k=0;k<taps;++k){double d=k-radius+1-fraction;double z=2*cutoff*d;double sinc=std::abs(d)<1e-12?2*cutoff:std::sin(pi*z)/(pi*d);double win=.42+.5*std::cos(pi*d/radius)+.08*std::cos(2*pi*d/radius);weights[p*taps+k]=float(sinc*win);norm+=sinc*win;}
   for(int k=0;k<taps;++k)weights[p*taps+k]/=float(norm);
  }
 }
 for(int i=0;i<8000;++i){double pos=start+i*speed;long center=long(std::floor(pos));int phase=int((pos-center)*phases);double v=0;
  for(int k=0;k<taps;++k){long j=center+k-radius+1;if(j>=0&&size_t(j)<count)v+=input[j]*weights[phase*taps+k];}
  output[i]=float(v);
 }
}
void AudioResampler::reset(){history_.clear();origin_=next_=0;}
void AudioResampler::append(const float* input,size_t count,std::vector<float>& output){
 history_.insert(history_.end(),input,input+count);
 constexpr int radius=96;
 // 3.6kHz cutoff,193-tap Blackman-windowed sinc.96sample/2ms lookahead.
 while(next_+radius<origin_+history_.size()){
  double v=0,norm=0;
  for(int d=-radius;d<=radius;++d){double z=2.*3600./48000.*d;double sinc=d?std::sin(pi*z)/(pi*d):2.*3600./48000.;double win=.42+.5*std::cos(pi*d/radius)+.08*std::cos(2*pi*d/radius);double w=sinc*win;norm+=w;long p=long(next_)+d-long(origin_);if(p>=0&&size_t(p)<history_.size())v+=w*history_[p];}
  output.push_back(float(v/norm));next_+=6;
 }
 if(next_>origin_+radius){size_t drop=next_-origin_-radius;history_.erase(history_.begin(),history_.begin()+drop);origin_+=drop;}
}
}
