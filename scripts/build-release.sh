#!/bin/sh
# Build and verify a signed release APK.

set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
APK_DIR="$ROOT_DIR/app/build/outputs/apk/release"
APK_PATH="$APK_DIR/app-release.apk"
OUTPUT_METADATA="$APK_DIR/output-metadata.json"
KEYSTORE_PATH="$ROOT_DIR/release.jks"
LOCAL_PROPERTIES="$ROOT_DIR/local.properties"

read_prop() {
    key="$1"
    file="$2"
    if [ ! -f "$file" ]; then
        return 0
    fi
    line=$(grep -E "^${key}=" "$file" | tail -n 1 || true)
    if [ -n "$line" ]; then
        printf '%s' "${line#*=}"
    fi
}

resolve_apksigner() {
    if command -v apksigner >/dev/null 2>&1; then
        command -v apksigner
        return 0
    fi

    sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(read_prop sdk.dir "$LOCAL_PROPERTIES")}}"
    if [ -z "$sdk_dir" ]; then
        return 0
    fi

    latest=""
    for candidate in "$sdk_dir"/build-tools/*/apksigner; do
        if [ -x "$candidate" ]; then
            latest="$candidate"
        fi
    done

    if [ -n "$latest" ]; then
        printf "%s" "$latest"
    fi
}

detect_version_name() {
    if [ ! -f "$OUTPUT_METADATA" ]; then
        return 0
    fi
    line=$(grep -E '"versionName"\s*:' "$OUTPUT_METADATA" | head -n 1 || true)
    if [ -z "$line" ]; then
        return 0
    fi
    value=${line#*:}
    value=$(printf "%s" "$value" | tr -d ' ",')
    printf "%s" "$value"
}

detect_git_tag() {
    if ! command -v git >/dev/null 2>&1; then
        return 0
    fi
    tag=$(git -C "$ROOT_DIR" describe --tags --exact-match 2>/dev/null || true)
    if [ -n "$tag" ]; then
        printf "%s" "$tag"
    fi
}

KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-$(read_prop KEYSTORE_PASSWORD "$LOCAL_PROPERTIES")}" 
KEY_ALIAS="${KEY_ALIAS:-$(read_prop KEY_ALIAS "$LOCAL_PROPERTIES")}" 
KEY_PASSWORD="${KEY_PASSWORD:-$(read_prop KEY_PASSWORD "$LOCAL_PROPERTIES")}" 

prompt_hidden() {
    prompt="$1"
    printf "%s" "$prompt" >&2
    stty -echo
    IFS= read -r value
    stty echo
    printf "\n" >&2
    printf "%s" "$value"
}

prompt_visible() {
    prompt="$1"
    printf "%s" "$prompt" >&2
    IFS= read -r value
    printf "%s" "$value"
}

detect_alias() {
    if ! command -v keytool >/dev/null 2>&1; then
        return 0
    fi
    if [ -z "$KEYSTORE_PASSWORD" ]; then
        return 0
    fi
    aliases=$(keytool -list -keystore "$KEYSTORE_PATH" -storepass "$KEYSTORE_PASSWORD" 2>/dev/null | grep -E ', .*Entry' | cut -d, -f1 || true)
    count=$(printf "%s\n" "$aliases" | grep -c . || true)
    if [ "$count" -eq 1 ]; then
        printf "%s" "$aliases"
    fi
}

if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "Missing keystore: $KEYSTORE_PATH"
    exit 1
fi

if [ -z "$KEYSTORE_PASSWORD" ]; then
    KEYSTORE_PASSWORD="$(prompt_hidden "Keystore password: ")"
fi

if [ -z "$KEY_ALIAS" ]; then
    KEY_ALIAS="$(detect_alias)"
fi

if [ -z "$KEY_ALIAS" ]; then
    KEY_ALIAS="$(prompt_visible "Key alias: ")"
fi

if [ -z "$KEY_PASSWORD" ]; then
    KEY_PASSWORD="$KEYSTORE_PASSWORD"
fi

if [ -z "$KEY_PASSWORD" ]; then
    KEY_PASSWORD="$(prompt_hidden "Key password: ")"
fi

if [ -z "$KEYSTORE_PASSWORD" ] || [ -z "$KEY_ALIAS" ] || [ -z "$KEY_PASSWORD" ]; then
    echo "Signing values cannot be empty"
    exit 1
fi

cd "$ROOT_DIR"
./gradlew \
  -PKEYSTORE_PASSWORD="$KEYSTORE_PASSWORD" \
  -PKEY_ALIAS="$KEY_ALIAS" \
  -PKEY_PASSWORD="$KEY_PASSWORD" \
  assembleRelease

if [ ! -f "$APK_PATH" ]; then
    fallback_apk="$APK_DIR/app-release-unsigned.apk"
    if [ -f "$fallback_apk" ]; then
        echo "Build produced unsigned APK: $fallback_apk"
        echo "Signing was not applied. Check keystore path/credentials."
        exit 1
    fi
    echo "Build did not produce APK at: $APK_PATH"
    exit 1
fi

APK_SIGNER="$(resolve_apksigner)"
if [ -n "$APK_SIGNER" ]; then
    "$APK_SIGNER" verify --print-certs "$APK_PATH"
elif command -v jarsigner >/dev/null 2>&1; then
    echo "Warning: apksigner not found; jarsigner cannot validate APK Signature Scheme v2/v3"
    jarsigner -verify -certs "$APK_PATH"
else
    echo "Warning: could not verify signature (apksigner/jarsigner not found)"
fi

VERSION_NAME="$(detect_version_name)"
if [ -z "$VERSION_NAME" ]; then
    VERSION_NAME="release"
fi

GIT_TAG="$(detect_git_tag)"
if [ -z "$GIT_TAG" ]; then
    echo "No exact git tag found on HEAD. Tag the release commit first (e.g. v1.0)."
    exit 1
fi

case "$GIT_TAG" in
  v*) TAG_VERSION="${GIT_TAG#v}" ;;
  *)
    echo "Tag must start with 'v' (e.g. v1.0). Found: $GIT_TAG"
    exit 1
    ;;
esac

if [ "$TAG_VERSION" != "$VERSION_NAME" ]; then
    echo "Tag/version mismatch: tag=$GIT_TAG, versionName=$VERSION_NAME"
    echo "Update app versionName or retag before building release."
    exit 1
fi

RENAMED_APK_PATH="$APK_DIR/dutchtrains-${GIT_TAG}.apk"
cp "$APK_PATH" "$RENAMED_APK_PATH"

echo "APK: $RENAMED_APK_PATH"
