#!/usr/bin/env bash
# KMP-ActivityPub の Cloud Agent 向けセットアップ。
# JDK 25（jvmToolchain）と Android SDK 37 を入れ、依存関係を取得してコンパイルする。
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

JAVA_HOME_CANDIDATES=(
  /usr/lib/jvm/java-25-openjdk-amd64
  /usr/lib/jvm/temurin-25-jdk-amd64
)

find_java25() {
  local candidate
  for candidate in "${JAVA_HOME_CANDIDATES[@]}"; do
    if [ -x "$candidate/bin/java" ]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

if ! JAVA_HOME="$(find_java25)"; then
  sudo apt-get update
  if ! sudo apt-get install -y openjdk-25-jdk; then
    sudo apt-get install -y wget apt-transport-https gpg ca-certificates
    sudo mkdir -p /etc/apt/keyrings
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
      | sudo gpg --dearmor --yes -o /etc/apt/keyrings/adoptium.gpg
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "$VERSION_CODENAME") main" \
      | sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null
    sudo apt-get update
    sudo apt-get install -y temurin-25-jdk
  fi
  JAVA_HOME="$(find_java25)"
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

ANDROID_HOME=/opt/android-sdk
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

sudo mkdir -p "$ANDROID_HOME"
sudo chown -R "$(id -un):$(id -gn)" "$ANDROID_HOME"

CMDLINE_TOOLS_VERSION=15859902
CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/cmdtools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  echo "${CMDLINE_TOOLS_SHA256}  $tmp/cmdtools.zip" | sha256sum -c -
  unzip -q "$tmp/cmdtools.zip" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
set -o pipefail

installed="$("$SDKMANAGER" --sdk_root="$ANDROID_HOME" --list_installed)"
packages=()
# compileSdk 37 の実パッケージ名は platforms;android-37 ではなく android-37.0
echo "$installed" | grep -q 'platforms;android-37.0' || packages+=("platforms;android-37.0")
echo "$installed" | grep -q 'build-tools;37.0.0' || packages+=("build-tools;37.0.0")
echo "$installed" | grep -q 'platform-tools' || packages+=("platform-tools")
if [ "${#packages[@]}" -gt 0 ]; then
  "$SDKMANAGER" --sdk_root="$ANDROID_HOME" "${packages[@]}"
fi

sudo tee /etc/profile.d/kmp-activitypub-dev.sh >/dev/null <<EOF
export JAVA_HOME=${JAVA_HOME}
export ANDROID_HOME=${ANDROID_HOME}
export ANDROID_SDK_ROOT=${ANDROID_HOME}
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
EOF
sudo chmod 644 /etc/profile.d/kmp-activitypub-dev.sh

# 非ログインのシェルでも Gradle と java が同じ場所を見る
marker_begin="# >>> kmp-activitypub-dev >>>"
marker_end="# <<< kmp-activitypub-dev <<<"
bashrc_block=$(cat <<EOF
$marker_begin
export JAVA_HOME=${JAVA_HOME}
export ANDROID_HOME=${ANDROID_HOME}
export ANDROID_SDK_ROOT=${ANDROID_HOME}
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
$marker_end
EOF
)
if [ -f "$HOME/.bashrc" ] && grep -q "$marker_begin" "$HOME/.bashrc"; then
  python3 - "$HOME/.bashrc" "$marker_begin" "$marker_end" "$bashrc_block" <<'PY'
import pathlib, sys
path, begin, end, block = sys.argv[1:]
file = pathlib.Path(path)
file_text = file.read_text()
start = file_text.index(begin)
stop = file_text.index(end, start) + len(end)
file.write_text(file_text[:start] + block + file_text[stop:])
PY
else
  printf '\n%s\n' "$bashrc_block" >> "$HOME/.bashrc"
fi

cd /workspace
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew :compileAll --no-daemon
./gradlew --stop || true
