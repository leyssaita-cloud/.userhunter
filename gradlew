#!/usr/bin/env sh
# Lightweight Gradle bootstrapper pinned to the version in gradle/wrapper/gradle-wrapper.properties.
set -eu
GRADLE_VERSION="9.5.0"
GRADLE_SHA256="553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL_DIR="$GRADLE_USER_HOME/userhunter-gradle/gradle-$GRADLE_VERSION"
GRADLE_BIN="$INSTALL_DIR/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$GRADLE_USER_HOME/userhunter-gradle"
  ARCHIVE="$GRADLE_USER_HOME/userhunter-gradle/gradle-$GRADLE_VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  echo "Downloading Gradle $GRADLE_VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$URL"
  else
    echo "curl or wget is required to bootstrap Gradle." >&2
    exit 1
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256="$(sha256sum "$ARCHIVE" | awk '{print $1}')"
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA256="$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')"
  else
    echo "sha256sum or shasum is required to verify the Gradle download." >&2
    exit 1
  fi
  if [ "$ACTUAL_SHA256" != "$GRADLE_SHA256" ]; then
    echo "Gradle archive checksum mismatch; refusing to extract it." >&2
    rm -f "$ARCHIVE"
    exit 1
  fi
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required to bootstrap Gradle." >&2; exit 1; }
  TEMP_DIR="$GRADLE_USER_HOME/userhunter-gradle/unpack-$$"
  mkdir -p "$TEMP_DIR"
  unzip -q "$ARCHIVE" -d "$TEMP_DIR"
  rm -rf "$INSTALL_DIR"
  mv "$TEMP_DIR/gradle-$GRADLE_VERSION" "$INSTALL_DIR"
  rmdir "$TEMP_DIR"
fi
exec "$GRADLE_BIN" -p "$BASE_DIR" "$@"
