#!/usr/bin/env bash
# Emulator only. Leaves ~50 MB free on /data, so a Backup copy fails: Not enough space. free-storage.sh undoes it.
set -euo pipefail

FILL_FILE=/data/local/tmp/fill-storage.bin
# 1 MB over RunBatch's 50 MB margin: small files still fit, a large one fails the space check.
LEAVE_FREE_BYTES=$((51 * 1024 * 1024))

case "$(adb shell 'getprop ro.boot.qemu; getprop ro.kernel.qemu')" in
  *1*) ;;
  *) echo "Refusing to run: the connected device is not an emulator." >&2; exit 1 ;;
esac

adb shell rm -f "$FILL_FILE"
free_kb=$(adb shell df -k /data/local/tmp | awk 'NR == 2 { print $4 }')
adb shell fallocate -l $((free_kb * 1024 - LEAVE_FREE_BYTES)) "$FILL_FILE"
adb shell df -h /data/local/tmp
