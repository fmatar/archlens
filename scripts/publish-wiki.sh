#!/usr/bin/env bash
set -euo pipefail

# Publish local wiki/ directory directly to GitHub Wiki git repository
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WIKI_DIR="${REPO_ROOT}/wiki"

if [ ! -d "${WIKI_DIR}" ]; then
  echo "Error: ${WIKI_DIR} not found."
  exit 1
fi

REMOTE_URL=$(git -C "${REPO_ROOT}" remote get-url origin)
# Convert https://github.com/owner/repo.git or git@github.com:owner/repo.git to wiki remote
if [[ "${REMOTE_URL}" =~ github\.com[:/]([^/]+)/([^/.]+)(\.git)?$ ]]; then
  OWNER="${BASH_REMATCH[1]}"
  REPO="${BASH_REMATCH[2]}"
  WIKI_REMOTE="git@github.com:${OWNER}/${REPO}.wiki.git"
else
  echo "Error: Could not parse GitHub owner/repo from remote URL: ${REMOTE_URL}"
  exit 1
fi

echo "=================================================="
echo "Publishing Archlens Wiki to: ${WIKI_REMOTE}"
echo "=================================================="

TMP_DIR=$(mktemp -d)
trap 'rm -rf "${TMP_DIR}"' EXIT

if git clone "${WIKI_REMOTE}" "${TMP_DIR}" 2>/dev/null; then
  echo "Wiki repository cloned successfully."
  cp -r "${WIKI_DIR}/"* "${TMP_DIR}/"
  cd "${TMP_DIR}"
  git add .
  if git diff --staged --quiet; then
    echo "Wiki is already up to date. Zero changes to push."
  else
    git commit -m "docs(wiki): sync wiki pages from ${REPO_ROOT}"
    git push origin master || git push origin main
    echo "Wiki published successfully to https://github.com/${OWNER}/${REPO}/wiki"
  fi
else
  echo ""
  echo "NOTE: GitHub Wiki git repository is not initialized yet."
  echo "To initialize it, visit: https://github.com/${OWNER}/${REPO}/wiki"
  echo "and click 'Create the first page' (even a blank page)."
  echo "Once initialized, re-run this script to sync all pages automatically!"
  echo ""
  exit 1
fi
