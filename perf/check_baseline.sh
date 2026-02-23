#!/usr/bin/env bash
set -euo pipefail

BASELINE_FILE="${1:-perf/baseline.csv}"
LATEST_FILE="${2:-perf/latest.csv}"

if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "Missing baseline file: $BASELINE_FILE" >&2
  exit 1
fi

if [[ ! -f "$LATEST_FILE" ]]; then
  echo "Missing latest benchmark file: $LATEST_FILE" >&2
  exit 1
fi

status=0

while IFS=, read -r name target _; do
  [[ -z "$name" ]] && continue
  full_name="org.animis.perf.${name}"
  score=$(awk -F, -v target_name="\"${full_name}\"" '$1==target_name {gsub(/\"/, "", $5); print $5; exit}' "$LATEST_FILE")

  if [[ -z "$score" ]]; then
    echo "MISSING: ${name} not found in $LATEST_FILE" >&2
    status=1
    continue
  fi

  if ! awk -v s="$score" -v t="$target" 'BEGIN { exit !(s <= t) }'; then
    echo "FAIL: ${name} score=${score}ms target=${target}ms" >&2
    status=1
  else
    echo "PASS: ${name} score=${score}ms target=${target}ms"
  fi
done < <(tail -n +2 "$BASELINE_FILE")

exit $status
