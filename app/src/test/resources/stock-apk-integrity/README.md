# Synthetic stock APK integrity fixtures

These tiny APKs contain only an Android manifest and a stored test payload. They
use package `app.morphe.fixture.stock`, version code `1`, minimum SDK `26`, and
target SDK `36`. The base has version name `1.0`; the `config.en` split has none.

They were linked with Android build-tools 36.0.0 `aapt2`, then signed with v2/v3
using disposable RSA-2048 fixture keys. `stock.apk` and `split.apk` share a key;
`other-signer.apk` and `other-signer-split.apk` have a different key. The other
split fixtures use the stock key but contain another package or version code.
No private key or proprietary app content
is retained. The tests modify the stored `morphe-stock-integrity-payload` bytes
while preserving the APK size and timestamp to reproduce changed contents with
an unchanged signing block.

`rotated.apk` has an old-to-new proof of rotation, using the old signer below
API 33 and the new signer on API 33+. `rotation-certificates.json` records the
SHA-256 fingerprints exported from those two fixture keys. It exercises the
difference between the currently verified signer and the full signing history.
`nested-resource.apk` contains an embedded APK-named resource that must not be
treated as a split module.

`hex-resource-version.apk` and `hex-version-split.apk` use package
`app.morphe.fixture.compatibility`, HEX integer version code `42` and major `1`,
and another disposable RSA-2048 fixture key. The base resolves its version name
from `@string/version_name`; the split has the literal `compatibility 1.0`.
They test Android-resolved base metadata, raw HEX parsing, and the pre-API-28
behavior that ignores `versionCodeMajor`. Their private fixture key was removed.

`stock-variant-low.apk` and `stock-variant-high.apk` use package
`app.morphe.fixture.variant`, the same release version `1.0` and disposable
signer, but version codes `10` and `11`. They ensure a genuine recovery candidate
with a different split-variant code does not force replacement of healthy signed
installed stock. Their private fixture key was removed too.
