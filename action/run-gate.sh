#!/usr/bin/env bash
# Entrypoint for the Archlens GitHub Action (action.yml).
#
# Arguments are built as a bash array so inputs containing spaces or quotes reach the CLI
# verbatim (#230). The CLI that runs is the one bundled with this Action revision, so a pinned
# `uses: fmatar/archlens@vX` always runs the matching CLI instead of whatever is newest on npm
# (#231).
set -uo pipefail

ACTION_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ARGS=(check -p "${INPUT_PATH:-.}" --max-violations "${INPUT_MAX_VIOLATIONS:-0}" --format sarif -O "${INPUT_SARIF_FILE:-archlens-results.sarif}")
if [ "${INPUT_DETECT_CYCLES:-true}" = "true" ]; then
  ARGS+=(--detect-cycles)
fi
if [ -n "${INPUT_SCREAMING_THRESHOLD:-}" ]; then
  ARGS+=(--screaming-threshold "$INPUT_SCREAMING_THRESHOLD")
fi
if [ -n "${INPUT_FITNESS_THRESHOLD:-}" ]; then
  ARGS+=(--fitness-threshold "$INPUT_FITNESS_THRESHOLD")
fi

CLI="${ARCHLENS_CLI:-$ACTION_DIR/cli/bin/index.js}"
if [ -f "$CLI" ]; then
  CMD=(node "$CLI")
else
  VERSION="$(node -p "require(process.argv[1]).version" "$ACTION_DIR/cli/package.json" 2>/dev/null || true)"
  if [ -z "$VERSION" ]; then
    echo "::error title=Archlens Action::Cannot determine the bundled CLI version from $ACTION_DIR/cli/package.json"
    exit 1
  fi
  CMD=(npx -y "@fmatar/archlens-skill@${VERSION}")
fi

printf 'Executing:'
printf ' %q' "${CMD[@]}" "${ARGS[@]}"
printf '\n'

EXIT_CODE=0
"${CMD[@]}" "${ARGS[@]}" || EXIT_CODE=$?

echo "Archlens gate check completed with code: $EXIT_CODE"
if [ "$EXIT_CODE" -ne 0 ]; then
  echo "::error title=Archlens Clean Architecture Gate Failed::Violations, dependency cycles, thresholds or invalid inputs failed the gate (exit $EXIT_CODE)."
fi
exit "$EXIT_CODE"
