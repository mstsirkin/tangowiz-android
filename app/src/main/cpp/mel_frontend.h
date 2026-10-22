#pragma once
#include <vector>
#include <cstddef>
namespace tangowiz {
// Exact catalog frontend: 8000 mono float samples -> [256,33,1] mel.
void resampleWindow(const float* input, size_t count, double start, double speed, float* output8000);
void computeMel(const float* pcm8000, float* output8448);
// Stateful windowed-sinc lowpass downsampler. Fixed48k input,8k output.
class AudioResampler {
public:
 void reset();
 void append(const float* input,size_t count,std::vector<float>& output);
private:
 std::vector<float> history_;
 size_t origin_=0,next_=0;
};
}
