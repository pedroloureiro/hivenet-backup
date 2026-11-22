#!/usr/bin/env bash
# Deletes a seeded Source file after it was picked, so backing it up fails: Source no longer available.
set -euo pipefail

SEED_DIR=/sdcard/Download/PrepareForBackup
path="$SEED_DIR/${1:?usage: fail-delete-source.sh <name inside $SEED_DIR>}"

adb shell "test -f '$path'" || { echo "No such file: $path" >&2; exit 1; }
adb shell "rm -- '$path'"
echo "Deleted $path. Back it up now: it ends Failed, Source no longer available."
