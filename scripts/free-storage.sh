#!/usr/bin/env bash
# Emulator only. Undoes fill-storage.sh.
set -euo pipefail

FILL_FILE=/data/local/tmp/fill-storage.bin

case "$(adb shell 'getprop ro.boot.qemu; getprop ro.kernel.qemu')" in
  *1*) ;;
  *) echo "Refusing to run: the connected device is not an emulator." >&2; exit 1 ;;
esac

adb shell rm -f "$FILL_FILE"
adb shell df -h /data/local/tmp
