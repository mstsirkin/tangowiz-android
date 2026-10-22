// Conservative SIMD lookup bounds for complete 8-bit PQ codes.
#pragma once
#include <stdint.h>
#if defined(__aarch64__) && defined(__ARM_NEON) && !defined(PQ8_FORCE_SCALAR)
#include <arm_neon.h>
#elif defined(__SSSE3__) && !defined(PQ8_FORCE_SCALAR)
#include <tmmintrin.h>
#endif

template <int BITS>
uint32_t mask16(const uint8_t *codes, const uint8_t *upper, int m, int cutoff) {
  constexpr int GROUPS = 1 << BITS;
  if (cutoff <= 0)
    return 65535;
  if (cutoff > 255 * m)
    return 0;
#if defined(__aarch64__) && defined(__ARM_NEON) && !defined(PQ8_FORCE_SCALAR)
  uint16x8_t lo = vdupq_n_u16(0), hi = vdupq_n_u16(0);
  for (int j = 0; j < m; ++j) {
    uint8x16_t c = vld1q_u8(codes + 16 * j);
    if constexpr (BITS < 8)
      c = vshrq_n_u8(c, 8 - BITS);
    const uint8_t *t = upper + GROUPS * j;
    uint8x16_t values;
    if constexpr (BITS == 4)
      values = vqtbl1q_u8(vld1q_u8(t), c);
    else if constexpr (BITS == 5) {
      uint8x16x2_t a{{vld1q_u8(t), vld1q_u8(t + 16)}};
      values = vqtbl2q_u8(a, c);
    } else if constexpr (BITS == 6) {
      uint8x16x4_t a{
          {vld1q_u8(t), vld1q_u8(t + 16), vld1q_u8(t + 32), vld1q_u8(t + 48)}};
      values = vqtbl4q_u8(a, c);
    } else {
      uint8x16x4_t a{
          {vld1q_u8(t), vld1q_u8(t + 16), vld1q_u8(t + 32), vld1q_u8(t + 48)}};
      values = vqtbl4q_u8(a, c);
      for (int g = 1; g < 4; ++g) {
        const uint8_t *u = t + g * 64;
        uint8x16x4_t b{{vld1q_u8(u), vld1q_u8(u + 16), vld1q_u8(u + 32),
                        vld1q_u8(u + 48)}};
        values = vqtbx4q_u8(values, b, vsubq_u8(c, vdupq_n_u8(g * 64)));
      }
    }
    lo = vaddw_u8(lo, vget_low_u8(values));
    hi = vaddw_u8(hi, vget_high_u8(values));
  }
  uint8x16_t passed =
      vcombine_u8(vmovn_u16(vcgeq_u16(lo, vdupq_n_u16(cutoff))),
                  vmovn_u16(vcgeq_u16(hi, vdupq_n_u16(cutoff))));
  const uint8_t weights_data[16] = {1, 2, 4, 8, 16, 32, 64, 128,
                                    1, 2, 4, 8, 16, 32, 64, 128};
  uint8x16_t bits = vandq_u8(passed, vld1q_u8(weights_data));
  return uint32_t(vaddv_u8(vget_low_u8(bits))) |
         (uint32_t(vaddv_u8(vget_high_u8(bits))) << 8);
#elif defined(__SSSE3__) && !defined(PQ8_FORCE_SCALAR)
  __m128i lo = _mm_setzero_si128(), hi = lo, zero = lo;
  for (int j = 0; j < m; ++j) {
    __m128i c =
        _mm_loadu_si128(reinterpret_cast<const __m128i *>(codes + 16 * j));
    c = _mm_and_si128(_mm_srli_epi16(c, 8 - BITS), _mm_set1_epi8(GROUPS - 1));
    __m128i low = _mm_and_si128(c, _mm_set1_epi8(15));
    __m128i values = zero;
    for (int g = 0; g < GROUPS / 16; ++g) {
      __m128i part =
          _mm_shuffle_epi8(_mm_loadu_si128(reinterpret_cast<const __m128i *>(
                               upper + GROUPS * j + g * 16)),
                           low);
      __m128i select =
          _mm_cmpeq_epi8(_mm_and_si128(c, _mm_set1_epi8(char(0xf0))),
                         _mm_set1_epi8(char(g * 16)));
      values = _mm_or_si128(values, _mm_and_si128(part, select));
    }
    lo = _mm_add_epi16(lo, _mm_unpacklo_epi8(values, zero));
    hi = _mm_add_epi16(hi, _mm_unpackhi_epi8(values, zero));
  }
  __m128i threshold = _mm_set1_epi16(cutoff - 1);
  return uint32_t(_mm_movemask_epi8(_mm_packs_epi16(
      _mm_cmpgt_epi16(lo, threshold), _mm_cmpgt_epi16(hi, threshold))));
#else
  uint32_t mask = 0;
  for (int lane = 0; lane < 16; ++lane) {
    int sum = 0;
    for (int j = 0; j < m; ++j)
      sum += upper[j * GROUPS + (codes[j * 16 + lane] >> (8 - BITS))];
    if (sum >= cutoff)
      mask |= 1u << lane;
  }
  return mask;
#endif
}
