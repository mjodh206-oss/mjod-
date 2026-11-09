#!/usr/bin/env bash
# ============================================================================
#  مَحلولة — سكربت بناء APK كامل من الصفر (بدون Gradle وبدون إنترنت)
#  السلسلة: JDK 17 → aapt2 → javac → d8 → zip → zipalign → apksigner
# ============================================================================
set -euo pipefail

TOOLCHAIN="${TOOLCHAIN:-/home/user/toolchain}"
export JAVA_HOME="${JAVA_HOME:-$TOOLCHAIN/jdk-17.0.20.1+1}"
BT="$TOOLCHAIN/sdk/build-tools/34.0.0"
PLATFORM="$TOOLCHAIN/sdk/platforms/android-34/android.jar"
export PATH="$JAVA_HOME/bin:$BT:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/app/src/main"
BUILD="$ROOT/build"
OUT="$ROOT/dist"
KEYSTORE="$ROOT/keys/mahloula-release.jks"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.4.0}"

echo "──────────────────────────────────────────────"
echo " بناء تطبيق مَحلولة  v$VERSION_NAME ($VERSION_CODE)"
echo "──────────────────────────────────────────────"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/gen" "$BUILD/dex" "$OUT"

# 1) ترجمة الموارد -----------------------------------------------------------
echo "[1/7] aapt2 compile …"
aapt2 compile --dir "$APP/res" -o "$BUILD/res.zip" 2>"$BUILD/aapt2c.log" || { cat "$BUILD/aapt2c.log"; exit 1; }

# 2) الربط + توليد R.java -----------------------------------------------------
echo "[2/7] aapt2 link …"
aapt2 link -o "$BUILD/app-linked.apk" \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --auto-add-overlay \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$BUILD/res.zip" 2>"$BUILD/aapt2l.log" || { cat "$BUILD/aapt2l.log"; exit 1; }

# 3) ترجمة Java --------------------------------------------------------------
echo "[3/7] javac …"
find "$APP/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -nowarn -encoding UTF-8 \
  -source 8 -target 8 \
  -bootclasspath "$BT/core-lambda-stubs.jar:$PLATFORM" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt" 2>"$BUILD/javac.log" || { tail -60 "$BUILD/javac.log"; exit 1; }

# 4) DEX ---------------------------------------------------------------------
echo "[4/7] d8 …"
find "$BUILD/classes" -name '*.class' > "$BUILD/classes.txt"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --release --min-api 24 --output "$BUILD/dex" \
  $(cat "$BUILD/classes.txt") 2>"$BUILD/d8.log" || { tail -30 "$BUILD/d8.log"; exit 1; }

# 5) التجميع: dex + assets ----------------------------------------------------
echo "[5/7] packaging …"
cp "$BUILD/app-linked.apk" "$BUILD/app-unsigned.apk"
(cd "$BUILD/dex" && zip -q "$BUILD/app-unsigned.apk" classes.dex)
if [ -d "$APP/assets" ]; then
  (cd "$APP" && zip -rq "$BUILD/app-unsigned.apk" assets)
fi

# 6) محاذاة ------------------------------------------------------------------
echo "[6/7] zipalign …"
zipalign -f -p 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

# 7) التوقيع -----------------------------------------------------------------
echo "[7/7] apksigner …"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$ROOT/keys"
  keytool -genkeypair -keystore "$KEYSTORE" -alias mahloula \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass mahloula2026 -keypass mahloula2026 \
    -dname "CN=Amjad Habib, OU=Mahloula, O=Mahloula, C=JO" >/dev/null 2>&1
fi
APK="$OUT/Mahloula-v$VERSION_NAME.apk"
apksigner sign --ks "$KEYSTORE" --ks-key-alias mahloula \
  --ks-pass pass:mahloula2026 --key-pass pass:mahloula2026 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$APK" "$BUILD/app-aligned.apk"

echo "──────────────────────────────────────────────"
apksigner verify --print-certs "$APK" | head -4 | sed 's/^/   ✔ /'
ls -lh "$APK" | awk '{print "   ✅ APK: "$9"  ("$5")"}'
echo "──────────────────────────────────────────────"
