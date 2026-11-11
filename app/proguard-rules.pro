# ---------------------------------------------------------------------------------------------
# Majwuda R8 / ProGuard rules
# ---------------------------------------------------------------------------------------------

# Readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room, DataStore, OkHttp, Okio, Compose and kotlinx.coroutines ship their own consumer rules.
# JSON handling uses the kotlinx.serialization JsonElement tree only (no generated serializers).

# Optional TLS providers referenced by OkHttp but not bundled.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
