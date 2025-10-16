#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ARCHIVE_NAME="ai-trading-bot-$(date +%Y%m%d%H%M%S).zip"
OUTPUT_DIR="$ROOT_DIR/dist"
mkdir -p "$OUTPUT_DIR"
ZIP_PATH="$OUTPUT_DIR/$ARCHIVE_NAME"
(
  cd "$ROOT_DIR"
  ./scripts/offline-package.sh
  zip -qr "$ZIP_PATH" target frontend README.md scripts pom.xml settings.xml
)
echo "Created archive: $ZIP_PATH"
