#!/usr/bin/env bash
# Build from the repository root with aapt, dx, JDK 8 and Android platform jar.
# Supply a signing keystore explicitly; never commit signing keys to the repository.
set -euo pipefail
: "${AAPT:?Set AAPT to the aapt binary}" "${DX_JAR:?Set DX_JAR to dx.jar}" "${ANDROID_JAR:?Set ANDROID_JAR to android.jar}" "${KEYSTORE:?Set KEYSTORE to a signing keystore}" "${KEY_ALIAS:?Set KEY_ALIAS}" "${KEYSTORE_PASSWORD:?Set KEYSTORE_PASSWORD}" "${KEY_PASSWORD:?Set KEY_PASSWORD}"
cd "$(dirname "$0")/.."
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
mkdir -p "$build/assets/assets" "$build/classes" "$build/gen"
cp index.html style.css app.js android.js manifest.webmanifest sw.js "$build/assets/"
cp assets/* "$build/assets/assets/"
"$AAPT" package -f -m -M android/app/src/main/AndroidManifest.xml -S android/app/src/main/res -I "$ANDROID_JAR" -J "$build/gen" -F "$build/unsigned.apk" -A "$build/assets"
javac -encoding UTF-8 -source 1.7 -target 1.7 -bootclasspath "$ANDROID_JAR" -d "$build/classes" android/app/src/main/java/com/remoty/app/MainActivity.java
java -jar "$DX_JAR" --dex --output="$build/classes.dex" "$build/classes"
(cd "$build" && zip -q unsigned.apk classes.dex)
mkdir -p android/output
jarsigner -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASSWORD" -keypass "$KEY_PASSWORD" -sigalg SHA256withRSA -digestalg SHA-256 -signedjar android/output/remoty.apk "$build/unsigned.apk" "$KEY_ALIAS"
jarsigner -verify android/output/remoty.apk
