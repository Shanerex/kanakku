#!/usr/bin/env bash
# AC-8: the backend version lives only in .mvn/maven.config, as a -SNAPSHOT value;
# every pom references ${revision}. specs/ is excluded (requirements quote the value).
set -euo pipefail
cd "$(dirname "$0")/.."

cfg=.mvn/maven.config
value=$(sed -n 's/^-Drevision=\(.*\)$/\1/p' "$cfg" | head -n1)
if [[ -z "$value" ]]; then
  echo "FAIL: $cfg has no -Drevision=<value>" >&2; exit 1
fi
if [[ "$value" != *-SNAPSHOT ]]; then
  echo "FAIL: revision '$value' in $cfg does not end in -SNAPSHOT" >&2; exit 1
fi

fail=0
while IFS= read -r pom; do
  if ! grep -q '<version>${revision}</version>' "$pom"; then
    echo "FAIL: $pom does not reference \${revision}" >&2; fail=1
  fi
done < <(git ls-files --cached --others --exclude-standard -- 'pom.xml' '*/pom.xml')

hits=$(git grep -F -l --untracked -e "$value" -- . ':!specs/' ':!web/package-lock.json' || true)
if [[ -n "$hits" && "$hits" != "$cfg" ]]; then
  echo "FAIL: version '$value' found outside $cfg:" >&2; echo "$hits" >&2; fail=1
fi
exit $fail
