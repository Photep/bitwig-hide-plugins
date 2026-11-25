#!/bin/sh
# Builds the mod on macOS or Linux: the steps of Patchbay's modder guide (docs/sdk/building.md).
#
#   BML_HOME=/path/to/BML-<version>-macos sh tools/build.sh
#
# Writes the installable folder to out/<mod-id>/ and out/<mod-id>-<version>.zip (for Patchbay's Add Mod...).
#
# Needs: a Patchbay (BML) install, for its mod kit and frameworks; JDK 25; Bitwig Studio 6.1.4; Python 3.
# Environment:
#   BML_HOME    the Patchbay install folder (the one holding modkit/ and mods/). Required.
#   JDK         the folder with javac, java and jar. Default: Homebrew's openjdk@25, else what is on PATH.
#   BITWIG_APP  macOS: the Bitwig 6.1.4 app to compile against. Default: /Applications/Bitwig Studio 6.1.4.app,
#               else /Applications/Bitwig Studio.app.
#   BITWIG_BIN  the folder holding bitwig.jar and libs.jar, if not inside BITWIG_APP (Linux: Bitwig's bin folder).
set -eu

TOOLS=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MOD=$(dirname -- "$TOOLS")

[ -n "${BML_HOME:-}" ] || { echo "BUILD_ERROR set BML_HOME to your Patchbay (BML) install folder" >&2; exit 2; }
KIT="$BML_HOME/modkit"

if [ -z "${JDK:-}" ]; then
  if [ -x /opt/homebrew/opt/openjdk@25/bin/javac ]; then JDK=/opt/homebrew/opt/openjdk@25/bin
  else JDK=$(dirname -- "$(command -v javac)"); fi
fi
if [ -z "${BITWIG_BIN:-}" ]; then
  if [ -z "${BITWIG_APP:-}" ]; then
    BITWIG_APP="/Applications/Bitwig Studio 6.1.4.app"
    [ -d "$BITWIG_APP" ] || BITWIG_APP="/Applications/Bitwig Studio.app"
  fi
  BITWIG_BIN="$BITWIG_APP/Contents/Java"
fi
BITWIG_JAR="$BITWIG_BIN/bitwig.jar"
LIBS_JAR="$BITWIG_BIN/libs.jar"

for f in "$MOD/mod.json" "$MOD/build.json" "$BITWIG_JAR" "$LIBS_JAR" "$JDK/javac" "$KIT/bml-modkit.jar" "$KIT/bml-api.jar"; do
  [ -e "$f" ] || { echo "BUILD_ERROR missing: $f" >&2; exit 2; }
done

MOD_ID=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["mod"]["id"])' "$MOD/mod.json")
VERSION=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["mod"]["version"])' "$MOD/mod.json")
WORK="$MOD/.build/$MOD_ID"
OUT="$MOD/out/$MOD_ID"

# 1. Start clean.
rm -rf "$WORK" "$OUT"
mkdir -p "$WORK/classes" "$OUT"

# 2. The compile jar, once per Bitwig build (keyed by the jar's hash). Made from your own Bitwig; never shared.
HASH=$(shasum -a 256 "$BITWIG_JAR" | cut -c1-12)
COMPILE="$MOD/.build/bitwig-compile-$HASH.jar"
if [ ! -f "$COMPILE" ]; then
  "$JDK/java" -jar "$KIT/bml-modkit.jar" compile-jar "$BITWIG_JAR" "$COMPILE"
fi

# 3. The frameworks the mod compiles against: build.json's classpath_mods, from the Patchbay install's mods/.
DEPS=""
for id in $(python3 -I -c 'import json,sys; [print(m.split("@")[0]) for m in json.load(open(sys.argv[1])).get("classpath_mods", [])]' "$MOD/build.json"); do
  jar="$BML_HOME/mods/$id/helper.jar"
  [ -f "$jar" ] || { echo "BUILD_ERROR framework not installed: $id ($jar)" >&2; exit 2; }
  DEPS="$DEPS:$jar"
done

# 4. Compile.
CP="$COMPILE:$LIBS_JAR:$KIT/bml-api.jar$DEPS"
find "$MOD/src" -name '*.java' > "$WORK/sources.txt"
"$JDK/javac" --release 21 -proc:none -encoding UTF-8 -implicit:none -sourcepath "$MOD/src" \
  -cp "$CP" -d "$WORK/classes" @"$WORK/sources.txt"

# 5. Finish: check hooks against Bitwig's jar, write hooks.json, rewrite the classes.
set -- "$WORK/classes" "$WORK/hooks.json" "$BITWIG_JAR" "$LIBS_JAR"
OLDIFS=$IFS; IFS=:
for d in $DEPS; do [ -n "$d" ] && set -- "$@" "$d"; done
IFS=$OLDIFS
"$JDK/java" -jar "$KIT/bml-modkit.jar" finish "$@"

# 6. helper.jar, with any non-Java files from src/.
(cd "$MOD/src" && find . -type f ! -name '*.java' ! -name '.DS_Store' -exec sh -c 'mkdir -p "$2/$(dirname "$1")" && cp "$1" "$2/$1"' _ {} "$WORK/classes" \;)
"$JDK/jar" --create --file "$OUT/helper.jar" -C "$WORK/classes" .

# 7. The finished mod.json.
python3 -I "$TOOLS/finish_manifest.py" "$MOD/mod.json" "$WORK/hooks.json" "$OUT/mod.json"

# 8. README, licence, images, package files; source and build.json, as Patchbay's own mods ship them.
cp "$MOD/README.md" "$OUT/"
if [ -f "$MOD/LICENSE" ]; then cp "$MOD/LICENSE" "$OUT/"; fi
if [ -d "$MOD/images" ]; then cp -R "$MOD/images" "$OUT/"; fi
if [ -d "$MOD/package" ]; then
  if [ -e "$MOD/package/mod.json" ]; then echo "BUILD_ERROR package/ must not hold a mod.json" >&2; exit 2; fi
  cp -R "$MOD/package/." "$OUT/"
fi
cp -R "$MOD/src" "$OUT/src"
cp "$MOD/build.json" "$OUT/"

ZIP="$MOD/out/$MOD_ID-$VERSION.zip"
rm -f "$ZIP"
(cd "$MOD/out" && zip -qr "$ZIP" "$MOD_ID" -x '*.DS_Store')
echo "BUILD_OK $MOD_ID $VERSION -> $OUT"
echo "ZIP: $ZIP"
