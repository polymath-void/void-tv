#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=== [VoidTV] Build & Deploy Pipeline ==="

export JAVA_HOME="/home/polymath/.local/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

AAPT2="/home/polymath/phone-backups-A142/termux/android-sdk/build-tools/35.0.0/aapt2"
ANDROID_JAR="/home/polymath/phone-backups-A142/termux/android-sdk/platforms/android-35/android.jar"
D8="/home/polymath/phone-backups-A142/termux/android-sdk/build-tools/35.0.0/d8"
ZIPALIGN="/home/polymath/phone-backups-A142/termux/android-sdk/build-tools/35.0.0/zipalign"
APKSIGNER="/home/polymath/phone-backups-A142/termux/android-sdk/build-tools/35.0.0/apksigner"

rm -rf build compiled_res.zip base_unaligned.apk classes.dex voidtv-aligned.apk voidtv-release.apk
mkdir -p build/gen build/bin

echo "[1/6] Compiling resources with aapt2..."
$AAPT2 compile --dir res -o compiled_res.zip

echo "[2/6] Linking manifest, assets & resources..."
$AAPT2 link -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -A assets \
    compiled_res.zip \
    -o base_unaligned.apk \
    --java build/gen

echo "[3/6] Compiling Java sources with javac..."
javac -cp "$ANDROID_JAR" -d build/bin \
    src/com/polymath/voidtv/*.java \
    $(find build/gen -name "*.java" 2>/dev/null || true)

echo "[4/6] Converting bytecode to classes.dex with d8..."
$D8 --lib "$ANDROID_JAR" --output . $(find build/bin -name "*.class")
zip -u base_unaligned.apk classes.dex

echo "[5/6] 4-byte zip alignment..."
$ZIPALIGN -f -p 4 base_unaligned.apk voidtv-aligned.apk

echo "[6/6] Cryptographic APK signing with debug keystore..."
if [ ! -f "debug.keystore" ]; then
    keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey \
        -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

$APKSIGNER sign --ks debug.keystore --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android \
    --out voidtv-release.apk voidtv-aligned.apk

echo "=== Build Successful: voidtv-release.apk ($(du -h voidtv-release.apk | cut -f1)) ==="
