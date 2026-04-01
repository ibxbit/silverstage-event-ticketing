#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_FILE="$ROOT_DIR/target/unit-test-summary.properties"

cd "$ROOT_DIR"
mkdir -p target

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven executable not found in PATH"
  exit 1
fi

MAVEN_LOG="target/unit-test-maven.log"
if ! env -u CLASSPATH mvn -q -Dtest='com.eaglepoint.venue.service.*Test' test >"$MAVEN_LOG" 2>&1; then
  if grep -q "org.codehaus.plexus.classworlds.launcher.Launcher" "$MAVEN_LOG"; then
    echo "Maven bootstrap failed (classworlds launcher not found). See $MAVEN_LOG"
  else
    echo "Backend unit test Maven execution failed. See $MAVEN_LOG"
  fi
  exit 1
fi

total=0
failures=0
errors=0
skipped=0

for file in target/surefire-reports/TEST-com.eaglepoint.venue.service.*.xml; do
  [[ -f "$file" ]] || continue
  tests_in_file=$(grep -m1 -o 'tests="[0-9]*"' "$file" | cut -d'"' -f2)
  failures_in_file=$(grep -m1 -o 'failures="[0-9]*"' "$file" | cut -d'"' -f2)
  errors_in_file=$(grep -m1 -o 'errors="[0-9]*"' "$file" | cut -d'"' -f2)
  skipped_in_file=$(grep -m1 -o 'skipped="[0-9]*"' "$file" | cut -d'"' -f2)

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
