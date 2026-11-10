#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export GRADLE_USER_HOME="$ROOT/.cache/gradle"
export ANDROID_USER_HOME="$ROOT/.cache/android-user"
export TMPDIR="$ROOT/.cache/tmp"
mkdir -p "$GRADLE_USER_HOME" "$ANDROID_USER_HOME" "$TMPDIR"
if [ -z "${JAVA_HOME:-}" ] && [ -x '/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home/bin/java' ]; then
    export JAVA_HOME='/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home'
fi
if [ -z "${ANDROID_HOME:-}" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "$ROOT/../family-location/.cache/android-sdk" ]; then
        export ANDROID_HOME="$ROOT/../family-location/.cache/android-sdk"
    fi
fi
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.io.tmpdir=$TMPDIR -Duser.home=$ANDROID_USER_HOME"
cd "$ROOT"
exec ./gradlew --no-daemon --console=plain "$@"
