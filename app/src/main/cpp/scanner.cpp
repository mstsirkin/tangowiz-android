// Experimental conservative SIMD filter for complete 8-bit PQ codes.
// Eight-bit code precision is retained. Lower precision is used only to bound
// scores; every surviving candidate is scored with the original float32 LUT.
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <queue>
#include <vector>
#if defined(__aarch64__) && defined(__ARM_NEON) && !defined(PQ8_FORCE_SCALAR)
#include <arm_neon.h>
#elif defined(__SSSE3__) && !defined(PQ8_FORCE_SCALAR)
#include <tmmintrin.h>
#endif

#include "scanner_c.h"
#include "simd_filter.hpp"

namespace {
struct Hit {
  float score;
  int64_t id;
};
struct Better {
  bool operator()(const Hit &a, const Hit &b) const {
    return a.score != b.score ? a.score > b.score : a.id < b.id;
  }
};
using Heap = std::priority_queue<Hit, std::vector<Hit>, Better>;
void add(Heap &heap, int k, float score, int64_t id) {
  Hit h{score, id};
  if (int(heap.size()) < k)
    heap.push(h);
  else if (Better{}(h, heap.top())) {
    heap.pop();
    heap.push(h);
  }
}

} // namespace

extern "C" const char *pq8_backend() {
#if defined(__aarch64__) && defined(__ARM_NEON) && !defined(PQ8_FORCE_SCALAR)
  return "ARM_NEON";
#elif defined(__SSSE3__) && !defined(PQ8_FORCE_SCALAR)
  return "X86_SSSE3";
#else
  return "PORTABLE_SCALAR";
#endif
}

extern "C" int pq8_scan(const uint8_t *codes, const int64_t *ids,
                        const int64_t *offsets, int64_t nblocks, int nlist,
                        int m, const float *lut, const int64_t *lists,
                        const float *biases, int nprobe, int k, int filtered,
                        int filter_bits, float *scores, int64_t *result_ids,
                        uint64_t *stats) {
  try {
    if (filter_bits != 4 && filter_bits != 5 && filter_bits != 6 &&
        filter_bits != 8)
      return -6;
    const int groups = 1 << filter_bits, width = 256 / groups;
    if (!codes || !ids || !offsets || !lut || !lists || !biases || !scores ||
        !result_ids || !stats || m < 1 || m > 128 || nlist < 1 || nblocks < 1 ||
        nprobe < 1 || k < 1 || k > 1024)
      return -1;
    std::fill(stats, stats + 4, 0);
    std::vector<double> maxima(m * groups);
    double base = 0, range = 0, maxabs_sum = 0;
    for (int j = 0; j < m; ++j) {
      double low = std::numeric_limits<double>::infinity(), high = -low,
             maxabs = 0;
      for (int group = 0; group < groups; ++group) {
        double v = -std::numeric_limits<double>::infinity();
        for (int z = 0; z < width; ++z) {
          float x = lut[j * 256 + group * width + z];
          if (!std::isfinite(x))
            return -2;
          v = std::max(v, double(x));
          maxabs = std::max(maxabs, std::abs(double(x)));
        }
        maxima[j * groups + group] = v;
        low = std::min(low, v);
        high = std::max(high, v);
      }
      base += low;
      range = std::max(range, high - low);
      maxabs_sum += maxabs;
    }
    double step = range > 0 ? range / 254.0 : 1.0;
    std::vector<uint8_t> upper(m * groups);
    for (int j = 0; j < m; ++j) {
      double low = *std::min_element(maxima.begin() + j * groups,
                                     maxima.begin() + (j + 1) * groups);
      for (int g = 0; g < groups; ++g)
        upper[j * groups + g] = uint8_t(std::min(
            255.0,
            std::max(0.0,
                     std::ceil((maxima[j * groups + g] - low) / step) + 1.0)));
    }
    Heap heap;
    auto exact_block = [&](int64_t b, float bias, uint32_t mask) {
      while (mask) {
        int lane = __builtin_ctz(mask);
        mask &= mask - 1;
        int64_t id = ids[b * 16 + lane];
        if (id < 0)
          continue;
        float s = 0;
        for (int j = 0; j < m; ++j)
          s += lut[j * 256 + codes[(b * m + j) * 16 + lane]];
        s += bias;
        add(heap, k, s, id);
        ++stats[1];
      }
    };
    // Seed the heap from the first four blocks of every selected inverted list.
    // The main pass skips these blocks, so no candidate is inserted twice.
    for (int p = 0; p < nprobe; ++p) {
      int64_t l = lists[p];
      if (l < 0 || l >= nlist || !std::isfinite(biases[p]))
        return -3;
      if (offsets[l] < 0 || offsets[l + 1] < offsets[l] ||
          offsets[l + 1] > nblocks)
        return -4;
      for (int64_t b = offsets[l]; b < std::min(offsets[l] + 4, offsets[l + 1]);
           ++b)
        exact_block(b, biases[p], 65535);
    }
    for (int p = 0; p < nprobe; ++p) {
      int64_t l = lists[p];
      float bias = biases[p];
      // Covers serial float32 LUT reduction error in both directions. Quantized
      // bounds have an additional positive unit; equality is never pruned.
      double guard = 4.0 * (m + 2) * std::numeric_limits<float>::epsilon() *
                     (maxabs_sum + std::abs(double(bias)) + 1.0);
      for (int64_t b = std::min(offsets[l] + 4, offsets[l + 1]);
           b < offsets[l + 1]; ++b) {
        uint32_t mask = 65535;
        if (filtered && int(heap.size()) == k) {
          double cut =
              (double(heap.top().score) - double(bias) - base - guard) / step;
          int threshold = cut <= 0        ? 0
                          : cut > 255 * m ? 255 * m + 1
                                          : int(std::ceil(cut));
          switch (filter_bits) {
          case 4:
            mask = mask16<4>(codes + b * m * 16, upper.data(), m, threshold);
            break;
          case 5:
            mask = mask16<5>(codes + b * m * 16, upper.data(), m, threshold);
            break;
          case 6:
            mask = mask16<6>(codes + b * m * 16, upper.data(), m, threshold);
            break;
          case 8:
            mask = mask16<8>(codes + b * m * 16, upper.data(), m, threshold);
            break;
          }
        }
        ++stats[3];
        exact_block(b, bias, mask);
      }
      for (int64_t b = offsets[l]; b < offsets[l + 1]; ++b)
        for (int lane = 0; lane < 16; ++lane)
          if (ids[b * 16 + lane] >= 0)
            ++stats[0];
    }
    stats[2] = stats[0] - stats[1];
    std::vector<Hit> sorted;
    while (!heap.empty()) {
      sorted.push_back(heap.top());
      heap.pop();
    }
    std::sort(sorted.begin(), sorted.end(), Better{});
    for (int i = 0; i < k; ++i) {
      scores[i] = i < int(sorted.size())
                      ? sorted[i].score
                      : -std::numeric_limits<float>::infinity();
      result_ids[i] = i < int(sorted.size()) ? sorted[i].id : -1;
    }
    return 0;
  } catch (...) {
    return -5;
  }
}
