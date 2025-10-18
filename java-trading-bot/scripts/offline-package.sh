#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/target"
CLASS_DIR="$BUILD_DIR/classes"
JAR_NAME="ai-trading-bot-0.3.0-SNAPSHOT.jar"
MAIN_CLASS="com.cryptobot.TradingBotApplication"

rm -rf "$BUILD_DIR"
mkdir -p "$CLASS_DIR"

find "$ROOT_DIR/src/main/java" -name '*.java' > "$BUILD_DIR/sources.list"
if [[ ! -s "$BUILD_DIR/sources.list" ]]; then
  echo "No Java sources found." >&2
  exit 1
fi

javac --release 17 -d "$CLASS_DIR" @"$BUILD_DIR/sources.list"

cat > "$BUILD_DIR/manifest.mf" <<MANIFEST
Manifest-Version: 1.0
Main-Class: $MAIN_CLASS
MANIFEST

jar --create --file "$BUILD_DIR/$JAR_NAME" --manifest "$BUILD_DIR/manifest.mf" -C "$CLASS_DIR" .
rm "$BUILD_DIR/sources.list" "$BUILD_DIR/manifest.mf"

echo "Created $BUILD_DIR/$JAR_NAME"
