#!/usr/bin/env bash
# flyway-immutable.sh: PreToolUse hook for Edit|Write|MultiEdit.
# Flyway migrations are forward-only: a versioned migration (V<n>__*.sql) that already exists on
# base_branch may never change. Write a new V<n+1>__ migration instead.
# Exit 0 allows. Exit 2 blocks with a one-line reason on stderr.

INPUT="$(cat)"
command -v jq >/dev/null 2>&1 || exit 0
printf '%s' "$INPUT" | jq -e . >/dev/null 2>&1 || exit 0

FILE="$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // ""')"
case "$FILE" in
  */db/migration/V*__*.sql) ;;
  *) exit 0 ;;
esac

ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
[ -n "$ROOT" ] || exit 0
case "$FILE" in
  "$ROOT"/*) REL="${FILE#"$ROOT"/}" ;;
  /*) exit 0 ;;
  *) REL="$FILE" ;;
esac

BASE="$(sed -n 's/^base_branch:[[:space:]]*//p' "$ROOT/.rx/config.yml" 2>/dev/null | head -n 1)"
BASE="${BASE:-main}"

if git -C "$ROOT" cat-file -e "$BASE:$REL" 2>/dev/null; then
  printf 'flyway-immutable: %s is already on %s; migrations are forward-only, add a new V<n>__ file instead\n' "$REL" "$BASE" >&2
  exit 2
fi
exit 0
