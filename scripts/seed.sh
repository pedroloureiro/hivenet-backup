#!/usr/bin/env bash
# Seeds /sdcard/Download/PrepareForBackup/ with demo Source files, replacing whatever is there.
set -euo pipefail

SEED_DIR=/sdcard/Download/PrepareForBackup
PNG_BASE64='iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAYklEQVR42u3PQREAEBAAQA3E8TdCKCSGFtqocQFk8LjfzmyBLX1FqjtaqiIgICAgICAgICAgICAgICAgICAgICAg8B2Ik6vumUpAQEBAQEBAQEBAQEBAQEBAQEBAQEBA4NsDwa71S7Nve3wAAAAASUVORK5CYII='

files=$(mktemp -d)
trap 'rm -rf "$files"' EXIT

echo "A plain text Source file." > "$files/notes.txt"
echo "$PNG_BASE64" | base64 --decode > "$files/photo.png"
# Byte offsets in the xref table are exact for this text: edit it and they break.
cat > "$files/document.pdf" <<'PDF'
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 48 >>
stream
BT /F1 18 Tf 24 72 Td (Prepare for Backup) Tj ET
endstream
endobj
5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000241 00000 n 
0000000339 00000 n 
trailer
<< /Size 6 /Root 1 0 R >>
startxref
409
%%EOF
PDF
: > "$files/empty.txt"
mkdir "$files/folder-a" "$files/folder-b"
echo "Same name, folder A." > "$files/folder-a/same-name.txt"
echo "Same name, folder B." > "$files/folder-b/same-name.txt"
echo "A name with emoji." > "$files/🎉 party 🎂.txt"
# 253 bytes: longer than the 250 a Backup copy name may take, cut mid-emoji.
long_name="long-name$(printf '🌊%.0s' {1..60}).txt"
echo "A very long name." > "$files/$long_name"

adb shell rm -rf "$SEED_DIR"
adb push "$files" "$SEED_DIR"
# Written on the device: pushing 1 GB takes far longer.
adb shell dd if=/dev/zero of="$SEED_DIR/large-1GB.bin" bs=1048576 count=1024 2> /dev/null
adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null

echo "Seeded $SEED_DIR:"
adb shell ls -lR "$SEED_DIR"
