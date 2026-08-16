#!/usr/bin/env bash
# Builds the release APK and pushes a v* tag so the GitHub release workflow runs.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(./gradlew :app:printVersionName -q --no-daemon | grep VERSION_NAME | cut -d= -f2)
echo "Releasing Pouch $VERSION"

# First release of a month is tagged without the .0 (e.g. 2026.08),
# subsequent ones carry the full version (e.g. 2026.08.1).
if [[ "$VERSION" == *.0 ]]; then
  TAG="${VERSION%.0}"
else
  TAG="$VERSION"
fi

git tag "$TAG"
git push origin "$TAG"

echo "Pushed tag $TAG — the Release workflow will build and publish the APK."
