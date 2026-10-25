#!/usr/bin/env bash
# Compila una versión nueva y la publica en GitHub para instalarla desde el celular.
# Uso (Git Bash):  bash app/android/publicar.sh 0.3 "Qué cambió en esta versión"
# El enlace https://github.com/gaqlh/mi-gym/releases/latest/download/MiGym.apk siempre baja la última.
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
REPO="gaqlh/mi-gym"
VERSION="${1:?Falta la versión, por ejemplo 0.3}"
NOTAS="${2:-Versión $VERSION}"

# 0.3 → 3, 1.0 → 100, 1.12 → 112: siempre crece si la versión crece
VERSION_CODE=$(python -c "import sys; a, b = (sys.argv[1] + '.0').split('.')[:2]; print(int(a) * 100 + int(b))" "$VERSION")

VERSION_CODE="$VERSION_CODE" VERSION_NAME="$VERSION" bash "$AQUI/compilar.sh"
gh release create "v$VERSION" "$(cygpath -w "$AQUI/salida/MiGym.apk")" --repo "$REPO" --title "Mi gym $VERSION" --notes "$NOTAS"
echo "Publicada: https://github.com/$REPO/releases/tag/v$VERSION"
