#!/usr/bin/env bash
# Keeps appending to a seeded Source file, so the bytes read differ from its reported size: Copy didn't match source.
set -euo pipefail

SEED_DIR=/sdcard/Download/PrepareForBackup
path="$SEED_DIR/${1:?usage: grow-file.sh <name inside $SEED_DIR>}"

adb shell "test -f '$path'" || { echo "No such file: $path" >&2; exit 1; }
echo "Appending to $path every second; pick it and back it up. Ctrl-C stops."
while true; do
  adb shell "echo grown >> '$path'"
  sleep 1
done
