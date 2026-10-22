// C ABI for the experimental full-precision 8-bit PQ scanner.
#pragma once
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
const char *pq8_backend(void);
// codes: [nblocks][m][16], each code still selects one of 256 centroids.
// ids: [nblocks][16], with -1 padding; offsets: [nlist+1] block boundaries.
// lut: [m][256] float scores in the same permuted order as codes.
// lists/biases: selected coarse list IDs and query-centroid inner products.
// filter_bits selects bound-table grouping, not stored PQ code precision.
// scores/result_ids: k slots. stats: visited, exact-scored, pruned, blocks.
// Returns zero on success; a negative value rejects invalid inputs.
int pq8_scan(const uint8_t *codes, const int64_t *ids, const int64_t *offsets,
             int64_t nblocks, int nlist, int m, const float *lut,
             const int64_t *lists, const float *biases, int nprobe, int k,
             int filtered, int filter_bits, float *scores, int64_t *result_ids,
             uint64_t *stats);
#ifdef __cplusplus
}
#endif
