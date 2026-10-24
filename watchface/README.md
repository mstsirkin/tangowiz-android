# TangoWiz Watch Face Format face

This resource-only APK uses Watch Face Format version 1 (Wear OS 4/API33+), with a small clock, large orchestra text and song text below. It contains no executable code. Its LONG_TEXT slot defaults to the companion's `org.tangowiz.wear.TangoComplicationService`; install both packages. The companion explicitly trusts only this face package for default complication access.

Manifest XML/info resource metadata follows the user's working local `runandclick` project. No image assets or code from that project were copied. The bundled preview is an illustrative drawing produced with Pillow; it is not a recognition result. Face XML was checked with the official Google WFF validator available in that project, selecting format version 1. On devices that do not automatically select the default source, choose TangoWiz for the face's orchestra/song complication in the system face editor.

References: https://developer.android.com/training/wearables/wff/setup and https://developer.android.com/training/wearables/complications/exposing-data . Data has a ten-minute validity bound with five-minute unchanged-text renewal while fresh states arrive; explicit stale/disconnect changes request an immediate update. The operating system controls complication update timing; this face cannot perform recognition or guarantee continuous per-second external data refresh.
