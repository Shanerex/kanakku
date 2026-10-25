---
description: Start the next Kanakku backlog row as an rx feature, with the intake filled from the PRD backlog table. Use instead of /rx-feature for rows 1-29.
argument-hint: "<row number or backlog name, e.g. 5 or ledger-core>"
allowed-tools: Bash(.rx/scripts/rx-new-feature.sh *), Bash(.rx/scripts/rx-status.sh *), Bash(git branch *), Grep, Read, Edit
---
Start backlog row: $ARGUMENTS

1. If `.rx/config.yml` does not exist, tell the human to run `/rx-init` first and stop. If `.rx/scripts/rx-status.sh` shows an active feature that is not `done`, show it and stop.
2. Find the row in the "Feature backlog, in build order" table of `docs/Kanakku_PRD.md` with Grep (`^\| <n> \|` for a number, `` `<name>` `` or `\| <name> \|` for a name). Read only that line. No match: list the row numbers and names (one Grep over the table) and stop.
3. From the row take: name, PRD (`F-nn`, maybe `(part n)`), Depends on, and the Intake cell split on ` / ` into why, who, done-when.
4. Depends on: for each row number, find its name the same way and check `git branch --list 'feature/*-<name>'` or `specs/*-<name>/`. If one has no branch or spec, warn in one line and ask whether to go ahead; stop on anything but yes.
5. Run `.rx/scripts/rx-new-feature.sh '<name>'` with Bash (the name as one single-quoted word). On a nonzero exit, show its message and stop; never retry with another id.
6. With Edit on `specs/<id>/state.md`, append to `## Log` (date as in the file):
   - `- <date> intake: prd: F-nn` (add ` part n` for a split row; /rx-next gives the pm that PRD block)
   - `- <date> intake: why: <why>`
   - `- <date> intake: who: <who>`
   - `- <date> intake: done-when: <done-when>`
   Set `phase: requirements`, `status: draft`, `next: /rx-next, pm drafts requirements.md`.
7. Reply in at most 6 lines: row, id, branch, the three intake answers in one line each, and "edit the Log in state.md if any answer is off, then run /rx-next". For rows 2, 3, 4, 16, 22, 24, 25, 27 and 29 add: "security review matters most here (PRD)".

Do not commit, write other spec files, or read the codebase. Gates `g1` to `g3` stay `none`. Small follow-ups that are not backlog rows use `/rx-feature --quick`.
