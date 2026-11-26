#!/usr/bin/env bash
# Turns the app folder that `./gradlew :desktop:createDistributable` (or packageAppImage) builds on Linux
# into one ELM-IPTV-<version>-x86_64.AppImage file. Needs appimagetool (CI downloads it).
#
#   desktop/packaging/make-appimage.sh <version> [appimagetool]
set -euo pipefail

version="${1:?version}"
tool="${2:-appimagetool}"
here="$(cd "$(dirname "$0")" && pwd)"
desktop="$(cd "$here/.." && pwd)"
app="$desktop/build/compose/binaries/main/app/ELM-IPTV"
out="$desktop/build/appimage"

[[ -x "$app/bin/ELM-IPTV" ]] || { echo "No app folder at $app; run :desktop:packageAppImage first." >&2; exit 1; }

rm -rf "$out" && mkdir -p "$out"
appdir="$out/ELM-IPTV.AppDir"
cp -a "$app" "$appdir"

cat > "$appdir/AppRun" <<'EOF'
#!/bin/sh
here="$(dirname "$(readlink -f "$0")")"
exec "$here/bin/ELM-IPTV" "$@"
EOF
chmod +x "$appdir/AppRun"

cp "$here/elm.png" "$appdir/elm-iptv.png"
cat > "$appdir/elm-iptv.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=ELM IPTV
Comment=IPTV player
Exec=ELM-IPTV
Icon=elm-iptv
Categories=AudioVideo;Video;Player;TV;
Terminal=false
EOF

# --appimage-extract-and-run: works where FUSE is not available (CI containers).
ARCH=x86_64 "$tool" --appimage-extract-and-run "$appdir" "$out/ELM-IPTV-$version-x86_64.AppImage"
ls -l "$out"/*.AppImage
