#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ANDROID_JAR="${ANDROID_JAR:-$ROOT/tools/android33.jar}"
SIGNING_ENV="${ORVID_SIGNING_ENV:-$HOME/keys/orvid-release.env}"
DEBUG_KEYSTORE="$ROOT/tools/debug.keystore"
R8_JAR="$ROOT/tools/r8.jar"
OUT="$ROOT/build"
LIBDEX="$ROOT/.libdex"
MIN_SDK=26
TARGET_SDK=33

if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in /data/data/com.termux/files/usr/lib/jvm/java-21-openjdk \
                   /usr/lib/jvm/java-21-openjdk; do
    [ -x "$candidate/bin/javac" ] && JAVA_HOME="$candidate" && break
  done
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "JDK 21 not found. Set JAVA_HOME, or on Termux: pkg install openjdk-21" >&2
  exit 1
fi

for tool in aapt2 zipalign apksigner zip unzip; do
  command -v "$tool" >/dev/null || {
    echo "$tool not found. On Termux: pkg install aapt2 apksigner zip unzip" >&2
    exit 1
  }
done

if [ ! -f "$R8_JAR" ]; then
  echo ">> downloading r8"
  curl -sfL -o "$R8_JAR" https://dl.google.com/android/maven2/com/android/tools/r8/8.13.25/r8-8.13.25.jar
fi

D8() { "$JAVA_HOME/bin/java" -cp "$R8_JAR" com.android.tools.r8.D8 "$@"; }

RUNTIME_LIBS=()
for j in "$ROOT"/libs/*.jar; do
  case "$(basename "$j")" in jsr305-*|annotation-1*) ;; *) RUNTIME_LIBS+=("$j") ;; esac
done
CLASSPATH="$(printf '%s:' "$ROOT"/libs/*.jar)"

LIBSUM="$(cat "${RUNTIME_LIBS[@]}" | sha256sum | cut -c1-16)"
if [ ! -f "$LIBDEX/$LIBSUM/classes.dex" ]; then
  echo ">> d8 libraries (one time, a few minutes)"
  rm -rf "$LIBDEX" && mkdir -p "$LIBDEX/$LIBSUM"
  D8 --release --lib "$ANDROID_JAR" --min-api $MIN_SDK --output "$LIBDEX/$LIBSUM" "${RUNTIME_LIBS[@]}"
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/stage"

echo ">> aapt2 compile"
aapt2 compile --dir "$ROOT/res" -o "$OUT/res.zip"
MANIFEST="$ROOT/AndroidManifest.xml"
DEVRES=()
SRCDIRS=("$ROOT/src")
if [ "${DEV:-0}" = 1 ]; then
  aapt2 compile --dir "$ROOT/dev/res" -o "$OUT/devres.zip"
  DEVRES=(-R "$OUT/devres.zip")
  SRCDIRS+=("$ROOT/dev/src")
  MANIFEST="$OUT/AndroidManifest.dev.xml"
  sed -e 's#<uses-permission android:name="android.permission.INTERNET" />#&\n    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />#' \
      -e 's#</application>#        <activity android:name=".dev.DevUpdate" android:exported="true" android:launchMode="singleTop" android:theme="@android:style/Theme.Translucent.NoTitleBar" />\n        <activity android:name=".dev.SearchProbe" android:exported="true" android:theme="@android:style/Theme.Translucent.NoTitleBar" />\n        <activity android:name=".dev.ThumbProbe" android:exported="true" android:theme="@android:style/Theme.Translucent.NoTitleBar" />\n        <activity android:name=".dev.Drive$Cmd" android:exported="true" android:noHistory="true" android:excludeFromRecents="true" android:theme="@android:style/Theme.Translucent.NoTitleBar" />\n        <provider android:name=".dev.Drive$Init" android:authorities="com.orvid.dev.drive" android:exported="false" />\n    </application>#' \
      "$ROOT/AndroidManifest.xml" > "$MANIFEST"
fi
LIBRES=()
for d in "$ROOT"/libs/res/*/; do
  name="$(basename "$d")"
  aapt2 compile --dir "$d" -o "$OUT/libres-$name.zip"
  LIBRES+=(-R "$OUT/libres-$name.zip")
done

echo ">> aapt2 link"
aapt2 link -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$MANIFEST" \
  "${LIBRES[@]}" \
  -R "$OUT/res.zip" \
  "${DEVRES[@]}" \
  --java "$OUT/gen" \
  --extra-packages androidx.media3.exoplayer \
  --min-sdk-version $MIN_SDK \
  --target-sdk-version $TARGET_SDK \
  --auto-add-overlay

echo ">> javac"
command find "${SRCDIRS[@]}" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
"$JAVA_HOME/bin/javac" -parameters --release 11 -encoding UTF-8 \
  -nowarn -Xlint:-options \
  -cp "$ANDROID_JAR:$CLASSPATH" \
  -d "$OUT/classes" "@$OUT/sources.txt" 2>"$OUT/javac.log" || { cat "$OUT/javac.log" >&2; exit 1; }

echo ">> d8 app"
command find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
D8 --release --lib "$ANDROID_JAR" --classpath "${RUNTIME_LIBS[0]}" \
  $(printf -- '--classpath %s ' "${RUNTIME_LIBS[@]:1}") \
  --min-api $MIN_SDK --output "$OUT/dex" "@$OUT/classes.txt"

echo ">> packaging"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
n=2
for dex in $(ls "$LIBDEX/$LIBSUM"/classes*.dex | sort -V); do
  cp "$dex" "$OUT/dex/classes$n.dex"
  n=$((n + 1))
done
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes*.dex)
(cd "$OUT/stage" && unzip -q -o "$ROOT/libs/rhino-"*.jar 'org/mozilla/javascript/resources/*' \
  && zip -qr "$OUT/unsigned.apk" org)

for abi in "$ROOT"/libs/native/*/; do
  abi="$(basename "$abi")"
  mkdir -p "$OUT/stage/lib/$abi"
  cp "$ROOT/libs/native/$abi/"*.so "$OUT/stage/lib/$abi/"
done
(cd "$OUT/stage" && zip -qr "$OUT/unsigned.apk" lib)

echo ">> zipalign"
zipalign -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo ">> signing"
if [ -f "$SIGNING_ENV" ]; then
  set -a
  . "$SIGNING_ENV"
  set +a
  echo "   release key: $ORVID_KEYSTORE"
  apksigner sign --ks "$ORVID_KEYSTORE" --ks-key-alias "${ORVID_KEY_ALIAS:-orvid}" \
    --ks-pass env:ORVID_KS_PASS --key-pass env:ORVID_KS_PASS \
    --min-sdk-version $MIN_SDK \
    --out "$OUT/orvid.apk" "$OUT/aligned.apk"
else
  if [ ! -f "$DEBUG_KEYSTORE" ]; then
    keytool -genkeypair -keystore "$DEBUG_KEYSTORE" -storetype PKCS12 -alias debug -keyalg RSA \
      -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Orvid Debug" >/dev/null
  fi
  echo "   debug key (set ORVID_SIGNING_ENV for a release build)"
  apksigner sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --min-sdk-version $MIN_SDK \
    --out "$OUT/orvid.apk" "$OUT/aligned.apk"
fi

apksigner verify --min-sdk-version $MIN_SDK "$OUT/orvid.apk"
echo ""
echo "OK -> $OUT/orvid.apk ($(du -h "$OUT/orvid.apk" | cut -f1))"
