#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_FILE="$ROOT_DIR/target/unit-test-summary.properties"

cd "$ROOT_DIR"
mkdir -p target

mvn -q -Dtest='com.eaglepoint.venue.service.*Test' test

total=0
failures=0
errors=0
skipped=0

for file in target/surefire-reports/TEST-com.eaglepoint.venue.service.*.xml; do
  [[ -f "$file" ]] || continue
  tests_in_file=$(awk 'match($0,/tests="([0-9]+)"/,a){print a[1]; exit}' "$file")
  failures_in_file=$(awk 'match($0,/failures="([0-9]+)"/,a){print a[1]; exit}' "$file")
  errors_in_file=$(awk 'match($0,/errors="([0-9]+)"/,a){print a[1]; exit}' "$file")
  skipped_in_file=$(awk 'match($0,/skipped="([0-9]+)"/,a){print a[1]; exit}' "$file")

  tests_in_file=${tests_in_file:-0}
  failures_in_file=${failures_in_file:-0}
  errors_in_file=${errors_in_file:-0}
  skipped_in_file=${skipped_in_file:-0}

  total=$((total + tests_in_file))
  failures=$((failures + failures_in_file))
  errors=$((errors + errors_in_file))
  skipped=$((skipped + skipped_in_file))
done

failed=$((failures + errors))
passed=$((total - failed - skipped))

{
  echo "suite=backend_unit"
  echo "total=$total"
  echo "passed=$passed"
  echo "failed=$failed"
  echo "skipped=$skipped"
} > "$REPORT_FILE"

echo "backend_unit total=$total passed=$passed failed=$failed skipped=$skipped"

if [[ "$failed" -gt 0 ]]; then
  exit 1
fi
