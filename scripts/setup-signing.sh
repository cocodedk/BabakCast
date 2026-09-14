#!/bin/sh
# setup-signing.sh
# Generates or reuses the release keystore, writes the four signing values to
# ~/.gradle/gradle.properties (so local debug builds can be release-signed and
# update the published APK in place without losing user data), and uploads the
# same four values + base64-encoded keystore to GitHub Secrets.
#
# These used to live in local.properties, which Android Studio regenerates from
# scratch — silently erasing them and leaving every later build unsigned. The
# Gradle home file sits outside the project, so the IDE never touches it.
#
# Run once from the project root: ./scripts/setup-signing.sh
set -eu

restore_tty() { stty echo 2>/dev/null || true; }
trap restore_tty EXIT INT TERM

REPO="cocodedk/BabakCast"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="${KEYSTORE_FILE:-$PROJECT_ROOT/release.keystore}"
ALIAS="${KEYSTORE_ALIAS:-android}"
LOCAL_PROPS="$PROJECT_ROOT/local.properties"
GRADLE_PROPS="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"

echo ""
echo "=== BabakCast Release Signing Setup ==="
echo ""

# ── Prerequisites ────────────────────────────────────────────────────────────
command -v keytool >/dev/null 2>&1 || { echo "ERROR: keytool not found — install JDK 17+"; exit 1; }
command -v gh     >/dev/null 2>&1 || { echo "ERROR: gh not found — install GitHub CLI";    exit 1; }
command -v base64 >/dev/null 2>&1 || { echo "ERROR: base64 not found";                     exit 1; }

gh auth status >/dev/null 2>&1 || { echo "ERROR: gh not authenticated — run: gh auth login"; exit 1; }

# ── Keystore ─────────────────────────────────────────────────────────────────
if [ -f "$KEYSTORE" ]; then
    echo "Found existing keystore: $KEYSTORE"
    echo "Skipping generation — using existing file."
else
    echo "Generating release keystore..."
    echo "You will be prompted for a keystore password, a key password,"
    echo "and some name/org fields (those can be anything)."
    echo ""
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000
fi

# ── Read passwords securely ───────────────────────────────────────────────────
echo ""
printf "Keystore password: "
stty -echo 2>/dev/null || true
read -r KSPASS
restore_tty
case "$KSPASS" in
  *'"'*)
    echo 'ERROR: Password must not contain " (double quote).'
    exit 1
    ;;
esac
echo ""

printf "Key password (Enter = same as keystore password): "
stty -echo 2>/dev/null || true
read -r KEYPASS
restore_tty
case "$KEYPASS" in
  *'"'*)
    echo 'ERROR: Key password must not contain " (double quote).'
    exit 1
    ;;
esac
echo ""

[ -z "$KEYPASS" ] && KEYPASS="$KSPASS"

# ── Verify ───────────────────────────────────────────────────────────────────
echo "Verifying keystore..."
keytool -list -keystore "$KEYSTORE" -alias "$ALIAS" \
    -storepass "$KSPASS" -keypass "$KEYPASS" >/dev/null 2>&1 || {
    echo ""
    echo "ERROR: Wrong password or alias. Nothing was written or uploaded."
    exit 1
}
echo "✓ Keystore valid"

# ── Write to ~/.gradle/gradle.properties ──────────────────────────────────────
# Outside the project, so Android Studio cannot regenerate it away. Strip any
# prior signing entries first so re-running doesn't accumulate stale lines.
strip_signing_keys() {
    grep -v '^KEYSTORE_PATH=' "$1" \
        | grep -v '^KEYSTORE_PASSWORD=' \
        | grep -v '^KEY_ALIAS=' \
        | grep -v '^KEY_PASSWORD='
}

echo "Writing signing values to $GRADLE_PROPS..."
mkdir -p "$(dirname "$GRADLE_PROPS")"
touch "$GRADLE_PROPS"
TMP="$(mktemp)"
strip_signing_keys "$GRADLE_PROPS" > "$TMP" || true
{
    cat "$TMP"
    echo "KEYSTORE_PATH=$KEYSTORE"
    echo "KEYSTORE_PASSWORD=$KSPASS"
    echo "KEY_ALIAS=$ALIAS"
    echo "KEY_PASSWORD=$KEYPASS"
} > "$GRADLE_PROPS"
rm -f "$TMP"
chmod 600 "$GRADLE_PROPS"
echo "✓ gradle.properties updated (mode 600)"

# Drop any copies left in local.properties so there is one source of truth and
# no secret lingers in a file the IDE rewrites.
if [ -f "$LOCAL_PROPS" ] && grep -q '^KEYSTORE_PATH=' "$LOCAL_PROPS" 2>/dev/null; then
    TMP="$(mktemp)"
    strip_signing_keys "$LOCAL_PROPS" > "$TMP" || true
    cat "$TMP" > "$LOCAL_PROPS"
    rm -f "$TMP"
    echo "✓ removed stale signing values from local.properties"
fi

# ── Upload secrets ────────────────────────────────────────────────────────────
KEYSTORE_B64=$(base64 "$KEYSTORE" | tr -d '\n')

echo "Uploading secrets to $REPO..."
printf '%s' "$KEYSTORE_B64" | gh secret set KEYSTORE_BASE64  --repo "$REPO"
printf '%s' "$KSPASS"       | gh secret set KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS"        | gh secret set KEY_ALIAS         --repo "$REPO"
printf '%s' "$KEYPASS"      | gh secret set KEY_PASSWORD      --repo "$REPO"

# ── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo "✓ All 4 GitHub Secrets uploaded:"
echo "    KEYSTORE_BASE64    ✓"
echo "    KEYSTORE_PASSWORD  ✓"
echo "    KEY_ALIAS          ✓  ($ALIAS)"
echo "    KEY_PASSWORD       ✓"
echo ""
echo "✓ local.properties populated. Locally-built debug APKs are now signed"
echo "  with the release key, so 'adb install -r' over the published APK"
echo "  works as a regular update — no uninstall, no data loss."
echo ""
echo "IMPORTANT: $KEYSTORE is gitignored — back it up somewhere secure."
echo "           If you lose it, you cannot update the app on any store."
echo ""
