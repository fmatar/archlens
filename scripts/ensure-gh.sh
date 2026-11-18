#!/usr/bin/env bash
# Ensures the GitHub CLI (`gh`) is available on the runner (#234).
#
# The self-hosted local-archlens runner image does not ship `gh`. When it is missing, this
# downloads the pinned official release from github.com/cli/cli, verifies its SHA-256 against the
# release's checksums file, installs it under ~/.local/gh-<version>, and adds it to PATH
# (and $GITHUB_PATH when running in Actions).
#
# Env: GH_VERSION (default below), GH_INSTALL_ROOT (default ~/.local), GH_INSTALL_DRY_RUN=1 to only
# print what would be downloaded.
set -euo pipefail

GH_VERSION="${GH_VERSION:-2.105.0}"
INSTALL_ROOT="${GH_INSTALL_ROOT:-$HOME/.local}"

if command -v gh >/dev/null 2>&1; then
  echo "gh already available: $(gh --version | head -n 1)"
  exit 0
fi

case "$(uname -s)" in
  Linux) OS=linux ;;
  Darwin) OS=macOS ;;
  *) echo "::error::Unsupported OS for automatic gh install: $(uname -s)"; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64 | amd64) ARCH=amd64 ;;
  aarch64 | arm64) ARCH=arm64 ;;
  *) echo "::error::Unsupported architecture for automatic gh install: $(uname -m)"; exit 1 ;;
esac

EXT=tar.gz
[ "$OS" = "macOS" ] && EXT=zip
ASSET="gh_${GH_VERSION}_${OS}_${ARCH}.${EXT}"
BASE_URL="${GH_DOWNLOAD_BASE:-https://github.com/cli/cli/releases/download/v${GH_VERSION}}"
TARGET_DIR="${INSTALL_ROOT}/gh-${GH_VERSION}"

if [ "${GH_INSTALL_DRY_RUN:-}" = "1" ]; then
  echo "Would download ${BASE_URL}/${ASSET} and install to ${TARGET_DIR}"
  exit 0
fi

if [ ! -x "${TARGET_DIR}/bin/gh" ]; then
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  curl -fsSL --retry 5 --retry-all-errors -o "${TMP_DIR}/${ASSET}" "${BASE_URL}/${ASSET}"
  curl -fsSL --retry 5 --retry-all-errors -o "${TMP_DIR}/checksums.txt" "${BASE_URL}/gh_${GH_VERSION}_checksums.txt"
  EXPECTED="$(awk -v f="$ASSET" '$2 == f { print $1 }' "${TMP_DIR}/checksums.txt")"
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL="$(sha256sum "${TMP_DIR}/${ASSET}" | awk '{ print $1 }')"
  else
    ACTUAL="$(shasum -a 256 "${TMP_DIR}/${ASSET}" | awk '{ print $1 }')"
  fi
  if [ -z "$EXPECTED" ] || [ "$EXPECTED" != "$ACTUAL" ]; then
    echo "::error::Checksum mismatch for ${ASSET} (expected '${EXPECTED}', got '${ACTUAL}')"
    exit 1
  fi
  mkdir -p "$INSTALL_ROOT"
  if [ "$EXT" = "zip" ]; then
    unzip -q "${TMP_DIR}/${ASSET}" -d "$TMP_DIR"
  else
    tar -xzf "${TMP_DIR}/${ASSET}" -C "$TMP_DIR"
  fi
  rm -rf "${TARGET_DIR}.tmp"
  mv "${TMP_DIR}/gh_${GH_VERSION}_${OS}_${ARCH}" "${TARGET_DIR}.tmp"
  mv -T "${TARGET_DIR}.tmp" "$TARGET_DIR" 2>/dev/null || mv "${TARGET_DIR}.tmp" "$TARGET_DIR"
fi

export PATH="${TARGET_DIR}/bin:$PATH"
if [ -n "${GITHUB_PATH:-}" ]; then
  echo "${TARGET_DIR}/bin" >> "$GITHUB_PATH"
fi
echo "Installed $("${TARGET_DIR}/bin/gh" --version | head -n 1) at ${TARGET_DIR}/bin/gh"
