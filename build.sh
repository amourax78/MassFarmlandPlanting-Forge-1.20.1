#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f gradle/wrapper/gradle-wrapper.jar || ! -f gradlew ]]; then
  VERSION='1.20.1-47.4.28'
  SHA1='eee9145e892bb5ca5fe72323bd81c8522c53417f'
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  URL="https://maven.minecraftforge.net/net/minecraftforge/forge/${VERSION}/forge-${VERSION}-mdk.zip"
  echo 'Downloading the official Forge MDK for its Gradle wrapper...'
  curl --fail --location --silent --show-error "$URL" --output "$TMP/mdk.zip"
  CHECK="$(sha1sum "$TMP/mdk.zip" | cut -d' ' -f1)"
  if [[ "$CHECK" != "$SHA1" ]]; then
      echo "MDK archive checksum mismatch: $CHECK" >&2
      exit 1
  fi
  unzip -q "$TMP/mdk.zip" -d "$TMP/mdk"
  mkdir -p gradle/wrapper
  cp "$TMP/mdk/gradlew" "$TMP/mdk/gradlew.bat" .
  cp "$TMP/mdk/gradle/wrapper/gradle-wrapper.jar" "$TMP/mdk/gradle/wrapper/gradle-wrapper.properties" gradle/wrapper/
  chmod +x gradlew
fi
./gradlew build
