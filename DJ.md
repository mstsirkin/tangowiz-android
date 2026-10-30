# Optional DJ popularity ranking

Phone and watch 0.5.13 add a **DJ** button. The phone owns the setting; the watch uses the existing acknowledged, revisioned command protocol. It is off by default. Both devices disable the button when the active phone library has no valid DJ data. No separate download is used.

For positive acoustic scores, ranking uses `min(1, acousticScore * (1 + song + orchestra + pair))`. Zero and negative scores stay unchanged. Approximate native scores above one are preserved. No additional DJ threshold is introduced. Song and orchestra contributions are each at most 2.5%; the song+orchestra combination contributes at most 2%, independent of recording year. The combined relative increase is at most 7%, before the existing confidence display mapping. A percentage boost is not a new calibrated probability.

Priors come from distinct named DJs' reviewed event setlists. Repeated selections by one DJ do not create extra votes. Merely appearing in an orchestra dictionary does not earn a bonus. Each component uses normalized `log(1 + distinct DJs)`. Reviewed ambiguous or ineligible titles cannot contribute song/pair votes. Source claims, aliases, singer and year evidence remain in the upstream dataset.

Ranking happens after native candidate search and acoustic speed-fallback decisions. Native scores, fingerprints, the model, silence gates, song-end prediction and fallback guards remain unchanged. DJ mode can reorder the returned candidates; it does not search additional candidates or shorten audio capture. Turning it off restores acoustic ranking.

History retains the original `score` and adds `ranking_score` and a `dj` object with requested/available/enabled/applied flags, data version, three component percentages, total percentage and both scores. History does not combine consecutive entries across a mode/data-version change. Toggle events are also recorded in settings logs, including when there is no recognized song. Watch history carries the same mode record.

## Bundle format

The optional allowlisted ZIP member is `dj-priors.bin`, declared with byte count and SHA-256 in the normal resource manifest. Installation validates the file and its binding to the exact `catalog.bin` SHA-256 before activation. Missing data keeps legacy bundles usable. Invalid installed data disables DJ ranking. An invalid declared download fails validation and preserves the previous bundle.

All integers and float32 values are big endian:

| Field | Size |
|---|---:|
| ASCII `TWDJPR01` | 8 bytes |
| Schema (1), entry count | two uint32 |
| Maximum song/orchestra/pair weights | three float32 |
| Catalog SHA-256 | 32 bytes |
| UTF-8 version length | uint16 |
| Version label | variable |
| Entries, unsigned ID hash order | 11 bytes each |

Each entry is a uint64 FNV-1a hash of the UTF-8 catalog source ID followed by three uint8 normalized priors (0–255). The exporter checks hash collisions over **all** catalog IDs. Missing entries have zero contributions. Runtime uses binary search and compact primitive arrays. Limits are 4 MiB, 200,000 entries, at most 3% per component and 7% total.

The initial file is 470,260 bytes for 42,744 nonzero entries. Regenerate it for every changed catalog; copying a file bound to an older catalog disables the feature. Preserve all unrelated model, keyword and optional resource files during publication. Upstream data and collection scripts are outside this application repository.

## Validation

Four JVM DJ tests cover relative scoring and malformed binary data. Emulator tests cover actual phone on/off controls, missing data, history persistence, catalog binding, revisioned watch command acceptance and stale-command rejection. The watch UI test covers off/on/unavailable states and history markers on an Android emulator. No physical paired-device transport or live audio timing was verified for this release.

A separate frozen-candidate diagnostic used 984 historical cases: top-label agreement changed from 902 to 930, accepted agreeing labels from 757 to 783, and accepted disagreeing labels remained zero. Quantized and full-precision priors made the same decisions. This is label-based reranking analysis, not a fresh production-phone frontend replay or an estimate of time to recognition.
