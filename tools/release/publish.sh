#!/usr/bin/env bash
# Publish a YiliGram test update through Telegram.
#
#  1. bumps YILI_VERSION_CODE in gradle.properties (the installed app compares version codes),
#  2. builds the phone APK (arm64-v8a + armeabi-v7a), optimized with -PyiliFast (R8, not debuggable,
#     no verbose logging) but debug-signed with the same package, so it installs over the beta,
#  3. hands it to YiliGram on the running emulator, which sends it to the account's Saved Messages
#     with "#yiligram_update vc=<code> v=<name>" and the commit list as release notes.
#
# Phones signed into the same account see "Update" on the next start (or Settings -> YiliGram
# appearance -> Check for updates). Needs: the emulator running with YiliGram (debug) logged in.
#
# Usage: tools/release/publish.sh ["release notes"]
set -euo pipefail
cd "$(dirname "$0")/../.."
source ~/Android/env.sh

PKG=com.yiligram.messenger.beta
PROPS=gradle.properties
code=$(grep '^YILI_VERSION_CODE=' "$PROPS" | cut -d= -f2)
name=$(grep '^YILI_VERSION_NAME=' "$PROPS" | cut -d= -f2)
code=$((code + 1))
sed -i "s/^YILI_VERSION_CODE=.*/YILI_VERSION_CODE=$code/" "$PROPS"

echo "== building $name (code $code)"
./gradlew -q :TMessagesProj_App:assembleAfatDebug -PyiliAbis=arm64-v8a,armeabi-v7a -PyiliFast
APK=TMessagesProj_App/build/outputs/apk/afat/debug/YiliGram-$name-debug.apk
vc=$(( code * 10 + 9 ))   # versionCodeOverride for the afat flavor (abiVersionCode 9)

notes=${1:-}
if [ -z "$notes" ]; then
    since=$(git log -1 --format=%H --grep '^Release: test update' 2>/dev/null || true)
    if [ -n "$since" ]; then
        notes=$(git log --format='• %s' "$since"..HEAD | head -15)
    else
        notes=$(git log --format='• %s' -8)
    fi
fi
caption="#yiligram_update vc=$vc v=$name
YiliGram $name (сборка $code)
$notes"

DEST=/sdcard/Android/data/$PKG/files/share
TMP=$(mktemp -d)
printf '%s' "$caption" | head -c 1000 > "$TMP/caption.txt"
adb shell "rm -f $DEST/*.apk $DEST/caption.txt; mkdir -p $DEST"
adb push "$APK" "$DEST/YiliGram-$name-$code.apk" >/dev/null
adb push "$TMP/caption.txt" "$DEST/caption.txt" >/dev/null
rm -rf "$TMP"
adb shell am start -n "$PKG/org.telegram.ui.LaunchActivity" >/dev/null; sleep 6
echo "== sending to Saved Messages"
adb shell am broadcast -a com.yiligram.update.PUBLISH -n "$PKG/com.yiligram.update.PublishReceiver" \
    --es path "$DEST/YiliGram-$name-$code.apk" --es caption_file "$DEST/caption.txt" | tail -1

git add "$PROPS"
git commit -q -m "Release: test update $name build $code" -m "Published to Saved Messages as #yiligram_update vc=$vc."
echo "== done: version code $vc. The upload finishes in the background on the emulator."
