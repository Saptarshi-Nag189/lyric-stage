#!/usr/bin/env bash
# Regenerates docs/screenshots. Makes static weight-900 cuts of the bundled variable fonts
# (Java2D cannot pick a variable font's weight), then runs the renderer.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=../..
FONTS=build/fonts
mkdir -p "$FONTS"
RES=$ROOT/app/src/main/res/font
python3 -m fontTools.varLib.instancer -q $RES/bricolage.ttf wght=800 opsz=96 wdth=100 -o $FONTS/bricolage_800.ttf
python3 -m fontTools.varLib.instancer -q $RES/bricolage.ttf wght=600 opsz=18 wdth=100 -o $FONTS/bricolage_600.ttf
python3 -m fontTools.varLib.instancer -q $RES/bricolage.ttf wght=400 opsz=14 wdth=100 -o $FONTS/bricolage_400.ttf
for s in devanagari bangla gurmukhi gujarati odia tamil telugu kannada malayalam; do
  python3 -m fontTools.varLib.instancer -q $RES/anek_$s.ttf wght=800 -o $FONTS/anek_${s}_800.ttf
done
python3 -m fontTools.varLib.instancer -q $RES/noto_sans_jp.ttf wght=900 -o $FONTS/noto_sans_jp_900.ttf
$ROOT/gradlew -p . -q installDist
JAVA_OPTS="-Djava.awt.headless=true -Dfonts=$FONTS -Dout=$ROOT/docs/screenshots -Dsamples=$ROOT/core/src/test/resources/samples -DextraSamples=samples -Xmx3g" \
  build/install/shots/bin/shots
