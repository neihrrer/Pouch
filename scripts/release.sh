#!/usr/bin/env bash
# Builds the release APK and pushes a v* tag so the GitHub release workflow runs.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(./gradlew :app:printVersionName -q --no-daemon | grep VERSION_NAME | cut -d= -f2)
echo "Releasing Pouch $VERSION"

git tag "v$VERSION"
git push origin "v$VERSION"

echo "Pushed tag v$VERSION — the Release workflow will build and publish the APK."
