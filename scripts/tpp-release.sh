#!/usr/bin/env bash
# Builds the signed Play bundle (.aab) of TPP Phone.
# Uso: scripts/tpp-release.sh [--apk] [ruta/al/keystore.jks]
#   --apk  también arma un APK firmado (para instalarlo y probar la versión de producción en un celular)
set -euo pipefail

WITH_APK=0
if [ "${1:-}" = "--apk" ]; then
  WITH_APK=1
  shift
fi
KEYSTORE="${1:-$HOME/claves/tpp-phone-upload.jks}"
EXPECTED="19:D6:F7:D9:92:47:A5:69:7C:B3:37:EE:A1:7B:6C:EC:AC:1C:AE:34:AD:0A:60:92:0D:8E:26:7E:30:C4:FC:65"

cd "$(dirname "$0")/.."

# TPP Phone for Play is built only from the release branch, and only from committed code. TPP_RELEASE_ANY_BRANCH=1 skips the checks
# (local tests only, never for Play).
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "${TPP_RELEASE_ANY_BRANCH:-0}" != 1 ]; then
  if [ "$BRANCH" != "release/tpp-phone" ]; then
    echo "Estás en la rama '$BRANCH'. TPP Phone para Play se compila solo desde 'release/tpp-phone':"
    echo "  git fetch && git checkout release/tpp-phone && git pull"
    exit 1
  fi
  if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "Hay cambios sin commitear en la rama. Para Play se compila solo código commiteado:"
    git status --short --untracked-files=no
    exit 1
  fi
fi

if [ ! -f "$KEYSTORE" ]; then
  echo "No encuentro la clave de subida en: $KEYSTORE"
  echo "Pasá la ruta como primer argumento: scripts/tpp-release.sh /ruta/a/la-clave.jks"
  exit 1
fi

# Always forget the password, even if the build fails.
trap 'unset TPP_UPLOAD_KEYSTORE_PASSWORD' EXIT

read -rsp "Contraseña de la clave de subida (no se ve al escribir): " TPP_UPLOAD_KEYSTORE_PASSWORD
echo
export TPP_UPLOAD_KEYSTORE="$KEYSTORE"
export TPP_UPLOAD_KEYSTORE_PASSWORD
export TPP_UPLOAD_KEY_ALIAS="${TPP_UPLOAD_KEY_ALIAS:-tpp-phone-upload}"

echo "Compilando el bundle de TPP Phone (puede tardar varios minutos)..."
if [ "$WITH_APK" = 1 ]; then
  ./gradlew bundleTppphoneRelease assembleTppphoneRelease
else
  ./gradlew bundleTppphoneRelease
fi

AAB="$(find app/build/outputs/bundle/tppphoneRelease -name '*.aab' | head -n 1)"
if [ -z "$AAB" ]; then
  echo "No se generó el .aab. Revisá los mensajes de arriba."
  exit 1
fi

echo
echo "Bundle generado: $PWD/$AAB"
echo "Huella SHA-256 del certificado con que quedó firmado:"
keytool -printcert -jarfile "$AAB" | grep SHA256
echo "Huella esperada de la clave de subida:"
echo "  SHA256: $EXPECTED"

if [ "$WITH_APK" = 1 ]; then
  APK="$(find app/build/outputs/apk/tppphone/release -name '*.apk' | head -n 1)"
  if [ -z "$APK" ]; then
    echo "No se generó el APK. Revisá los mensajes de arriba."
    exit 1
  fi
  echo
  echo "APK de prueba: $PWD/$APK"
  DOWNLOADS="/mnt/c/Users/$(cmd.exe /c 'echo %USERNAME%' 2>/dev/null | tr -d '\r')/Downloads"
  if [ -d "$DOWNLOADS" ]; then
    cp "$APK" "$DOWNLOADS/"
    echo "Copiado a: $DOWNLOADS/$(basename "$APK")"
  fi
  echo "Se instala al lado de la versión de prueba (identificador ar.com.tpp.phone)."
fi
