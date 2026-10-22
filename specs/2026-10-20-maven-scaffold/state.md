---
feature: 2026-10-20-maven-scaffold
phase: done
status: approved
g1: approved:Shanerex:2026-10-20
g2: approved:Shanerex:2026-10-20
g3: approved:Shanerex:2026-10-20
bump: none
next: open a PR; a human merges
---
# State: 2026-10-20-maven-scaffold

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-10-20 intake: feature created
- 2026-10-20 intake: prd: F-01
- 2026-10-20 intake: why: Nothing runs yet
- 2026-10-20 intake: who: every contributor
- 2026-10-20 intake: done-when: `docker compose up` serves the app; CI gates PRs; Modulith verification runs
- 2026-10-20 requirements: requirements.md final; base package dev.kanakku kept; g1 approved by Shanerex
- 2026-10-20 plan: human chose separate web and app (own port, CORS, npm only, no Maven web module); g1 reset, back to requirements; plan.md and tasks.md stale
- 2026-10-20 requirements: human asked independent versioning: backend 0.1.0-SNAPSHOT in maven.config (release drops suffix), web 0.1.0-dev in package.json; PM revising
- 2026-10-20 requirements: revised (separate web/app, independent versioning); g1 approved by Shanerex; plan.md/tasks.md to be redrafted
- 2026-10-20 plan: plan.md + tasks.md final (15 tasks); g2 approved by Shanerex; architecture lines approved
- 2026-10-20 build: T-1..T-15 done, tests green; minio/minio image gone, compose uses pgsty/minio; root .gitignore gained target/ lines
- 2026-10-20 build: 15/15 tasks ticked; tests, spotless, npm checks, compose up all pass locally; moving to verify
- 2026-10-20 verify: review.md written, verdict fail (QA env JDK17; 3 security majors); fix loop 1
- 2026-10-20 verify: fix loop 1 done (JDK25 tests green, minio pinned, .env ignored, mailpit localhost); human accepted licence exception for jakarta.annotation-api (EPL-2.0 OR GPL-2.0+CPE); rerun reviewers
- 2026-10-20 verify: rerun passed (QA + security); review.md final
- 2026-10-20 verify: g3 approved by Shanerex, bump none; closing
- 2026-10-20 close: rx-check done blocked: .mvn/maven.config shared file modified (F-01 creates the configured version_file); knowledge entries written
- 2026-10-20 close: human accepted rx-check shared-file flag on .mvn/maven.config (F-01 creates version_file, bump none); phase done
