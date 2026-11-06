#!/bin/bash
# Haalt de Linphone-bouwdoos (liblinphone voor iOS, Swift Package) op van download.linphone.org.
# De git-opslag van Linphone (gitlab.linphone.org) is vanaf GitHub Actions niet te klonen;
# de losse release-bestanden wel. De grote XCFrameworks downloadt Xcode daarna zelf (met controle-som).
set -euo pipefail
VERSION="${LINPHONE_SDK_VERSION:-5.5.39}"
BASE="https://download.linphone.org/releases/ios/spm/linphone-sdk-swift-ios-${VERSION}"
DEST="$(cd "$(dirname "$0")/.." && pwd)/Packages/linphone-sdk"
rm -rf "$DEST"
mkdir -p "$DEST/Sources/linphonesw" "$DEST/Sources/linphonexcframeworks"
for f in Package.swift LICENSE.txt Sources/linphonesw/LinphoneWrapper.swift Sources/linphonesw/LinphoneSdkInfos.swift \
         Sources/linphonexcframeworks/Dummy.swift; do
  curl -fsSL --retry 3 "$BASE/$f" -o "$DEST/$f"
done
echo "Linphone-bouwdoos $VERSION staat in $DEST"
