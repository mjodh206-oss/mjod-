# Signing

`majwuda-community.p12` is a **public community key** tracked in this repository so that
tagged releases built by CI are reproducible and verifiable by anyone. It intentionally has a
well-known password (see `keystore.properties`). Any APK signed with it can be "updated" by any
other APK signed with the same key, which is exactly what we want for a sideloaded community
build — do not use it for privileged or enterprise distribution.

Private builds can (and should) override it via environment variables:

- `MAJWUDA_KEYSTORE_FILE` — path to your own `.p12` / `.jks` keystore
- `MAJWUDA_KEYSTORE_PASSWORD` — keystore password
- `MAJWUDA_KEY_ALIAS` — key alias
- `MAJWUDA_KEY_PASSWORD` — key password

Current community certificate SHA-256 (v1.0.0):
`2B:88:87:3F:F1:83:58:D9:EB:1C:FA:4E:70:F8:8F:1A:A3:2D:94:FA:ED:96:C0:BF:3C:27:EC:FF:E1:A6:D6:97`
