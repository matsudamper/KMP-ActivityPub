#!/usr/bin/env bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"

# 構築済みの SDK を無視して入れ直さないよう、local.properties の sdk.dir も候補にする
sdk_dir_in_local_properties=""
if [ -f local.properties ]; then
  sdk_dir_in_local_properties="$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' local.properties | tail -n 1)"
fi
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${sdk_dir_in_local_properties:-${HOME}/android-sdk}}}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
SDKMANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"

# SDK のパッケージはビルド時に AGP が取りに行くので、ここでは sdkmanager とライセンスだけ用意する
if [ ! -x "${SDKMANAGER}" ]; then
  echo "[setup] cmdline-tools を導入する"
  mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
  tmp="$(mktemp -d)"
  trap 'rm -rf "${tmp}"' EXIT
  # プロキシ経由だと転送が途中で切れて壊れた zip になることがあるので、確かめて取り直す
  for attempt in 1 2 3 4; do
    if curl -fsSL -o "${tmp}/cmdline-tools.zip" "${CMDLINE_TOOLS_URL}" && unzip -tq "${tmp}/cmdline-tools.zip" > /dev/null; then
      break
    fi
    if [ "${attempt}" = 4 ]; then
      echo "cmdline-tools を取得できなかった" >&2
      exit 1
    fi
  done
  unzip -q "${tmp}/cmdline-tools.zip" -d "${tmp}"
  rm -rf "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
  mv "${tmp}/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
fi

mkdir -p "${ANDROID_SDK_ROOT}/licenses"
printf '\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n' > "${ANDROID_SDK_ROOT}/licenses/android-sdk-license"

# 環境変数(ANDROID_HOME)はセットアップ後のシェルに残らないので local.properties に書く。
# 他のローカル設定を消さないよう sdk.dir の行だけ差し替える
tmp_local_properties="$(mktemp ./.local.properties.XXXXXX)"
if [ -f local.properties ]; then
  grep -v -E '^[[:space:]]*sdk\.dir[[:space:]]*=' local.properties > "${tmp_local_properties}" || true
fi
echo "sdk.dir=${ANDROID_SDK_ROOT}" >> "${tmp_local_properties}"
mv "${tmp_local_properties}" local.properties

echo "[setup] 完了"
