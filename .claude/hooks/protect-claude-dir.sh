#!/usr/bin/env bash
# protect-claude-dir.sh: PreToolUse hook for Edit|Write|MultiEdit|NotebookEdit.
# Blocks a subagent (hook JSON has agent_id) from changing anything under .claude/: the hooks,
# agents, commands, rules and the source copy of the rx scripts are human-owned. rx-check.sh
# shared does not cover .claude/, so this is the only guard. The main thread is allowed.
# Exit 0 allows. Exit 2 blocks with a one-line reason on stderr.

INPUT="$(cat)"
command -v jq >/dev/null 2>&1 || exit 0
printf '%s' "$INPUT" | jq -e . >/dev/null 2>&1 || exit 0

AGENT="$(printf '%s' "$INPUT" | jq -r '.agent_id // ""')"
[ -n "$AGENT" ] || exit 0
FILE="$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // .tool_input.notebook_path // ""')"
[ -n "$FILE" ] || exit 0

ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
case "$FILE" in
  /*) ;;
  *) FILE="$(printf '%s' "$INPUT" | jq -r '.cwd // ""')/$FILE" ;;
esac

case "$FILE" in
  "$ROOT"/.claude/*|*/../.claude/*)
    printf 'protect-claude-dir: .claude/ is human-owned; a subagent may not edit %s\n' "${FILE#"$ROOT"/}" >&2
    exit 2
    ;;
esac
exit 0
