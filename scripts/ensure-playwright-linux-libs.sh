#!/usr/bin/env bash
# Ensures shared libraries required by Playwright's Chromium headless shell are available
# on Linux CI runners — including unprivileged / user-namespaced Actions Runner Controller (ARC)
# containers where sudo/apt cannot chown or rename inside /var/cache/apt/archives.
set -euo pipefail

if [ "$(uname -s)" != "Linux" ]; then
  exit 0
fi

BROWSERS_DIR="${PLAYWRIGHT_BROWSERS_PATH:-$HOME/.cache/ms-playwright}"
SHELL_BIN=$(find "$BROWSERS_DIR" -type f -name "chrome-headless-shell" 2>/dev/null | head -n 1 || true)
if [ -z "$SHELL_BIN" ]; then
  SHELL_BIN=$(find "$BROWSERS_DIR" -type f -name "chrome" 2>/dev/null | head -n 1 || true)
fi

if [ -z "$SHELL_BIN" ]; then
  echo "No Playwright Chromium binary found under ${BROWSERS_DIR}; skipping shared library check."
  exit 0
fi

export_sysroot_ld_path() {
  local sysroot="$1"
  if [ ! -d "$sysroot" ]; then
    return 0
  fi
  local lib_dirs
  lib_dirs=$(find "$sysroot" \( -type f -o -type l \) -name "*.so*" -exec dirname {} \; 2>/dev/null | sort -u | paste -sd: - || true)
  if [ -n "$lib_dirs" ]; then
    export LD_LIBRARY_PATH="${lib_dirs}${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
    if [ -n "${GITHUB_ENV:-}" ]; then
      echo "LD_LIBRARY_PATH=${LD_LIBRARY_PATH}" >> "$GITHUB_ENV"
    fi
  fi
}

has_missing_libs() {
  ldd "$SHELL_BIN" 2>/dev/null | grep -q "=> not found"
}

if ! has_missing_libs; then
  echo "All shared libraries for ${SHELL_BIN} are already satisfied."
  exit 0
fi

CACHE_BASE="${ARCHLENS_PLAYWRIGHT_LIBS_DIR:-$HOME/.cache/archlens-playwright-libs}"
SYSROOT="${CACHE_BASE}/sysroot"
APT_DIR="${CACHE_BASE}/apt"

# Fast-path: check if a cached user-space sysroot already satisfies ldd.
if [ -d "$SYSROOT" ]; then
  export_sysroot_ld_path "$SYSROOT"
  if ! has_missing_libs; then
    echo "Satisfied Playwright shared libraries from cached sysroot (${SYSROOT})."
    exit 0
  fi
fi

# Always resolve & extract .deb packages in user space under $CACHE_BASE so unprivileged
# user-namespaced containers (where sudo exists without CAP_CHOWN / writable /var/cache/apt)
# never fail on _apt sandbox chown/rename errors.
rm -rf "$APT_DIR"
mkdir -p \
  "$SYSROOT" \
  "$APT_DIR/state/lists/partial" \
  "$APT_DIR/cache/archives/partial" \
  "$APT_DIR/log"

APT_OPTS=(
  -o "Dir::State=$APT_DIR/state"
  -o "Dir::State::Lists=$APT_DIR/state/lists"
  -o "Dir::State::status=/var/lib/dpkg/status"
  -o "Dir::Cache=$APT_DIR/cache"
  -o "Dir::Cache::Archives=$APT_DIR/cache/archives"
  -o "Dir::Log=$APT_DIR/log"
  -o "Debug::NoLocking=1"
  -o "APT::Sandbox::User=root"
)

echo "Updating user-space apt metadata for Playwright Chromium runtime libraries..."
apt-get "${APT_OPTS[@]}" update -qq

CANDIDATE_PKGS=(
  libasound2t64 libasound2
  libatk-bridge2.0-0t64 libatk-bridge2.0-0
  libatk1.0-0t64 libatk1.0-0
  libatspi2.0-0t64 libatspi2.0-0
  libcairo2
  libcups2t64 libcups2
  libdbus-1-3
  libdrm2
  libexpat1
  libgbm1
  libglib2.0-0t64 libglib2.0-0
  libnspr4
  libnss3
  libpango-1.0-0
  libwayland-client0
  libx11-6
  libxcb1
  libxcomposite1
  libxdamage1
  libxext6
  libxfixes3
  libxkbcommon0
  libxrandr2
  libxshmfence1
)

AVAILABLE_PKGS=()
for pkg in "${CANDIDATE_PKGS[@]}"; do
  if apt-cache "${APT_OPTS[@]}" show "$pkg" >/dev/null 2>&1; then
    AVAILABLE_PKGS+=("$pkg")
  fi
done

if [ "${#AVAILABLE_PKGS[@]}" -eq 0 ]; then
  echo "::error::No Playwright runtime library packages could be resolved via user-space apt-cache."
  exit 1
fi

apt-get "${APT_OPTS[@]}" install -y -qq --download-only --no-install-recommends "${AVAILABLE_PKGS[@]}"

shopt -s nullglob
for deb in "$APT_DIR/cache/archives/"*.deb; do
  dpkg-deb -x "$deb" "$SYSROOT"
done
shopt -u nullglob

rm -rf "$APT_DIR"
export_sysroot_ld_path "$SYSROOT"

if has_missing_libs; then
  echo "::error::Playwright Chromium still has unresolved shared libraries after sysroot extraction:"
  ldd "$SHELL_BIN" | grep "=> not found" || true
  exit 1
fi

echo "Playwright Linux runtime libraries extracted cleanly to ${SYSROOT}."
