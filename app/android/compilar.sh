#!/usr/bin/env bash
# Compila el APK de la app sin Android Studio: aapt2 + javac + d8 + zipalign + apksigner.
# Uso (Git Bash):  VERSION_CODE=2 VERSION_NAME=0.2 bash app/android/compilar.sh
# El APK queda en app/android/salida/. Cada versión nueva necesita un VERSION_CODE mayor.
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
SDK="/c/Users/gusta/Android/sdk"
BT="$SDK/build-tools/35.0.0"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
JDK="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
JAVA="$JDK/bin/java"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-0.1}"
B="$AQUI/build"
SALIDA="$AQUI/salida/MiGym-$VERSION_NAME.apk"
w() { cygpath -w "$1"; }

rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/assets" "$AQUI/salida"

echo "1/6 Página de la app"
# La página va en la raíz de assets: aapt2 en Windows guarda las subcarpetas con «\» y Android no las encuentra
python "$(w "$AQUI/preparar_web.py")" "$(w "$AQUI/../app-gym.html")" "$(w "$B/assets")" "$VERSION_NAME"

echo "2/6 Recursos e ícono"
"$BT/aapt2.exe" compile --dir "$(w "$AQUI/res")" -o "$(w "$B/res.zip")"
"$BT/aapt2.exe" link -o "$(w "$B/base.apk")" -I "$(w "$ANDROID_JAR")" --manifest "$(w "$AQUI/AndroidManifest.xml")" \
  -A "$(w "$B/assets")" --java "$(w "$B/gen")" --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" "$(w "$B/res.zip")"

echo "3/6 Código Java"
FUENTES_JAVA=()
while IFS= read -r f; do FUENTES_JAVA+=("$(w "$f")"); done < <(find "$AQUI/src" "$B/gen" -name '*.java')
"$JDK/bin/javac" -source 8 -target 8 -encoding UTF-8 -nowarn -Xlint:-options \
  -bootclasspath "$(w "$ANDROID_JAR")" -d "$(w "$B/classes")" "${FUENTES_JAVA[@]}"

echo "4/6 Pasar a formato Android (dex)"
CLASES=()
while IFS= read -r f; do CLASES+=("$(w "$f")"); done < <(find "$B/classes" -name '*.class')
"$JAVA" -cp "$(w "$BT/lib/d8.jar")" com.android.tools.r8.D8 --release --min-api 26 \
  --lib "$(w "$ANDROID_JAR")" --output "$(w "$B")" "${CLASES[@]}"

echo "5/6 Empaquetar"
cp "$B/base.apk" "$B/sin-firmar.apk"
(cd "$B" && "$BT/aapt.exe" add -v sin-firmar.apk classes.dex > /dev/null)
"$BT/zipalign.exe" -f -p 4 "$(w "$B/sin-firmar.apk")" "$(w "$B/alineado.apk")"

echo "6/6 Firmar"
# La firma debe ser siempre la misma: si cambia, Android no deja actualizar sin desinstalar (y se pierden los datos)
"$JAVA" -jar "$(w "$BT/lib/apksigner.jar")" sign --ks "$(w "$AQUI/firma/migym.keystore")" \
  --ks-pass "file:$(w "$AQUI/firma/clave.txt")" --ks-key-alias migym --v4-signing-enabled false \
  --out "$(w "$SALIDA")" "$(w "$B/alineado.apk")"
"$JAVA" -jar "$(w "$BT/lib/apksigner.jar")" verify "$(w "$SALIDA")"
# Copia con nombre fijo: es la que se sube a GitHub para que el enlace de descarga no cambie
cp "$SALIDA" "$AQUI/salida/MiGym.apk"
echo "Listo: $(w "$SALIDA")"
