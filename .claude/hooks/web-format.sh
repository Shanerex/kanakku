#!/usr/bin/env bash
# web-format.sh: PostToolUse hook for Edit|Write|MultiEdit.
# Runs the web app's own Prettier on an edited web/src file. No-op until web/ has its
# node_modules (after F-01), and for any other path. Java and Kotlin formatting stays with
# `./mvnw spotless:apply`, which is too slow to run per edit.

INPUT="$(cat)"
command -v jq >/dev/null 2>&1 || exit 0
FILE="$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // ""' 2>/dev/null)"
case "$FILE" in
  */web/src/*.ts|*/web/src/*.tsx|*/web/src/*.css|*/web/src/*.json) ;;
  *) exit 0 ;;
esac

ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
PRETTIER="$ROOT/web/node_modules/.bin/prettier"
[ -x "$PRETTIER" ] && [ -f "$FILE" ] || exit 0
(cd "$ROOT/web" && "$PRETTIER" --write --log-level warn "$FILE") >&2 || true
exit 0
