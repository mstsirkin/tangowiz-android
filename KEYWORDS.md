# Optional orchestra display data

Orchestra keyword preferences belong to downloaded resources, not either APK. The watch displays only a validated, optional hint supplied by the phone. An absent capability, absent hint, unknown name, uncertain credit or nonfitting layout retains the original uniform name display. Full orchestra names and recognition identities never change.

The single resource ZIP contains `catalog.bin` (index and song metadata), `nmfp.tflite`, and the optional `orchestra-keywords.json`. Its checksum and size are listed with the other members in the manifest's `files` map. The JSON has `version: 1` and `names: [[full_name, keyword, append_boolean], ...]`, limited to 256 KiB.

The existing downloader validates the archive and every declared member, including the table schema, before atomically activating the complete bundle. A failed update preserves the previous bundle. There is no separate table download or update mechanism. Original two-member ZIPs remain supported; absence of the optional table retains normal display. The native index format and index/model bytes are unchanged.

The phone caches the table by resource generation and optional file state and matches full display credits conservatively. It sends only the current candidates' hints: `orchestraKeyword: {text: ..., placement: "in_name" | "append"}`. No table is transferred to or shipped in the watch APK. A cached state may retain its individual hint until a newer state replaces it.

The watch's optional **Short orchestra name** setting displays just the supplied preferred keyword or alias. Without that setting the existing emphasis layout is retained. Full orchestra credits remain in cached state, history and accessibility descriptions; the setting does not alter the resource table or wire protocol.

## Protocol compatibility

State and command headers advertise `protocol: 1`, `supportedProtocols: [1]` and `capabilities: ["orchestra-keyword-v1"]`. Missing headers mean legacy protocol 1 with no optional keyword capability. Existing commands/settings revisions/audio framing retain their meanings.

The phone omits direct-message/ACK hints for a peer that has not advertised the capability. The persistent latest-state DataItem may contain optional hints; a receiving watch uses them only when the sender advertises the capability and its wire protocol is supported. An updated watch without a hint displays the original name; an older watch ignores new fields. Unsupported wire versions are rejected with an update message before settings/capture commands execute. The watch retains its previous cached state and stops watch capture when incompatible state is received; a subsequent compatible state recovers. Supported-version advertisements do not permit executing commands in an unsupported wire format.

Bluetooth and ADB pairing are unaffected. Future protocol versions must explicitly negotiate a supported wire encoding before using new command semantics; optional display data alone does not bump protocol 1.
