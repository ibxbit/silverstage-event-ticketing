#!/usr/bin/env bash
set -uo pipefail

echo "=== Running all tests in Docker ==="

mkdir -p target

# 1. Backend unit + integration tests
echo "[1/3] Backend unit + integration tests"
UNIT_EXIT=0
mvn -q -Dtest='com.eaglepoint.venue.service.*Test,com.eaglepoint.venue.api.*Test' test 2>&1 | tee target/unit-test-maven.log || UNIT_EXIT=$?

# Collect surefire results
total=0; failures=0; errors=0; skipped=0
for file in target/surefire-reports/TEST-com.eaglepoint.venue.*.xml; do
  [[ -f "$file" ]] || continue
  t=$(grep -m1 -o 'tests="[0-9]*"' "$file" | cut -d'"' -f2); t=${t:-0}
  f=$(grep -m1 -o 'failures="[0-9]*"' "$file" | cut -d'"' -f2); f=${f:-0}
  e=$(grep -m1 -o 'errors="[0-9]*"' "$file" | cut -d'"' -f2); e=${e:-0}
  s=$(grep -m1 -o 'skipped="[0-9]*"' "$file" | cut -d'"' -f2); s=${s:-0}
  total=$((total+t)); failures=$((failures+f)); errors=$((errors+e)); skipped=$((skipped+s))
done
failed=$((failures+errors)); passed=$((total-failed-skipped))
{
  echo "suite=backend_unit"
  echo "total=$total"
  echo "passed=$passed"
  echo "failed=$failed"
  echo "skipped=$skipped"
} > target/unit-test-summary.properties
echo "backend tests: total=$total passed=$passed failed=$failed"

# 2. Frontend tests
echo "[2/3] Frontend tests"
FRONTEND_EXIT=0
npx jest --runInBand --json --outputFile target/frontend-test-summary.json 2>/dev/null || FRONTEND_EXIT=$?

# 3. API functional tests (start app, run tests, stop)
echo "[3/3] API functional tests"
API_EXIT=0
SERVER_PID=""

cleanup() {
  if [[ -n "${SERVER_PID}" ]]; then
    kill "${SERVER_PID}" >/dev/null 2>&1 || true
    wait "${SERVER_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

SPRING_PROFILES_ACTIVE=test mvn -q -DskipTests spring-boot:run > target/api-server.log 2>&1 &
SERVER_PID=$!
API_READY=0
for _ in $(seq 1 60); do
  if curl -fsS "http://localhost:8080/api/events" >/dev/null 2>&1; then
    API_READY=1; break
  fi
  sleep 2
done

if [[ "$API_READY" -eq 1 ]]; then
  mkdir -p target/api-functional-tests
  javac -d target/api-functional-tests API_tests/*.java
  SPRING_PROFILES_ACTIVE=test SILVERSTAGE_BASE_URL=http://localhost:8080 \
    java -cp target/api-functional-tests ApiFunctionalTests || API_EXIT=$?
else
  echo "API server failed to start. See target/api-server.log"
  API_EXIT=1
fi

# Summary
echo ""
echo "=== Docker Test Summary ==="
echo "backend: exit=$UNIT_EXIT"
echo "frontend: exit=$FRONTEND_EXIT"
echo "api_functional: exit=$API_EXIT"

if [[ "$UNIT_EXIT" -ne 0 || "$FRONTEND_EXIT" -ne 0 || "$API_EXIT" -ne 0 ]]; then
  exit 1
fi
