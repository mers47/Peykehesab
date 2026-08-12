#!/usr/bin/env bash
set -Eeuo pipefail

APK="${1:?مسیر APK لازم است}"
REQUIRE_SIGNATURE="${2:-no}"

if [[ ! -f "$APK" ]]; then
  echo "APK پیدا نشد: $APK" >&2
  exit 1
fi

BUILD_TOOLS="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/36.0.0"

if command -v aapt >/dev/null 2>&1; then
  AAPT="$(command -v aapt)"
elif [[ -x "$BUILD_TOOLS/aapt" ]]; then
  AAPT="$BUILD_TOOLS/aapt"
elif command -v aapt2 >/dev/null 2>&1; then
  AAPT="$(command -v aapt2)"
elif [[ -x "$BUILD_TOOLS/aapt2" ]]; then
  AAPT="$BUILD_TOOLS/aapt2"
else
  echo "aapt/aapt2 پیدا نشد" >&2
  exit 1
fi
BADGING="$("$AAPT" dump badging "$APK")"

grep -q "package: name='ir.peykhesab.app" <<<"$BADGING" || {
  echo "applicationId مورد انتظار داخل APK پیدا نشد" >&2
  exit 1
}
grep -q "sdkVersion:'23'" <<<"$BADGING" || {
  echo "minSdk APK برابر 23 نیست" >&2
  exit 1
}
grep -q "targetSdkVersion:'37'" <<<"$BADGING" || {
  echo "targetSdk APK برابر 37 نیست" >&2
  exit 1
}

if grep -q '^uses-feature:' <<<"$BADGING"; then
  echo "APK نباید سخت‌افزار اجباری داشته باشد؛ برای نصب یونیورسال uses-feature اجباری پیدا شد" >&2
  grep '^uses-feature:' <<<"$BADGING" >&2
  exit 1
fi

mapfile -t ABIS < <(unzip -Z1 "$APK" | awk -F/ '/^lib\/[^/]+\/[^/]+\.so$/ {print $2}' | sort -u)
if (( ${#ABIS[@]} > 0 )); then
  printf 'ABIهای داخل APK: %s\n' "${ABIS[*]}"
  for required in arm64-v8a armeabi-v7a x86_64 x86; do
    if ! printf '%s\n' "${ABIS[@]}" | grep -qx "$required"; then
      echo "ABI لازم برای APK یونیورسال وجود ندارد: $required" >&2
      exit 1
    fi
  done
fi

if [[ "$REQUIRE_SIGNATURE" == "yes" ]]; then
  if command -v apksigner >/dev/null 2>&1; then
    APKSIGNER="$(command -v apksigner)"
  elif [[ -x "$BUILD_TOOLS/apksigner" ]]; then
    APKSIGNER="$BUILD_TOOLS/apksigner"
  else
    echo "apksigner پیدا نشد" >&2
    exit 1
  fi
  "$APKSIGNER" verify --verbose --print-certs "$APK"
fi

unzip -t "$APK" >/dev/null
printf 'APK_VERIFY_OK: %s\n' "$APK"
