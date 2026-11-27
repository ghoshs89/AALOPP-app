#!/usr/bin/env bash
set -euo pipefail
apk=${1:?Supply the signed APK path}
tag=${2:?Supply the release tag}
version=$(cat version.txt)
[[ "$tag" == "v$version" ]]
IFS=. read -r major minor patch <<< "$version"
code=$((10#$major * 10000 + 10#$minor * 100 + 10#$patch))
build_tools="${ANDROID_HOME:?}/build-tools/36.0.0"
identity=$("$build_tools/aapt" dump badging "$apk" | sed -n '1p')
[[ "$identity" == "package: name='com.puzzlequest.app' versionCode='$code' versionName='$version' "* ]]
signature=$("$build_tools/apksigner" verify --print-certs "$apk")
grep -Fxq "Signer #1 certificate SHA-256 digest: df06125b10888e172a1c8c649f1db634a633b773296f7337c9085ebbd8aa4d3d" <<< "$signature"
