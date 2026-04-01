#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

mkdir -p target

UNIT_EXIT=0
FRONTEND_EXIT=0
API_EXIT=0
SERVER_PID=""

cleanup() {
  if [[ -n "${SERVER_PID}" ]]; then
    kill "${SERVER_PID}" >/dev/null 2>&1 || true
    wait "${SERVER_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "[1/3] Running backend unit tests from unit_tests/"
bash unit_tests/run_unit_tests.sh || UNIT_EXIT=$?

echo "[2/3] Running frontend tests"
npm run test:frontend -- --json --outputFile target/frontend-test-summary.json || FRONTEND_EXIT=$?

echo "[3/3] Running API functional tests from API_tests/"
if curl -fsS "http://localhost:8080/api/events" >/dev/null 2>&1; then
  echo "Using existing API server on http://localhost:8080"
else
  echo "Starting API server with SPRING_PROFILES_ACTIVE=test"
  SPRING_PROFILES_ACTIVE=test mvn -q -DskipTests spring-boot:run > target/api-server.log 2>&1 &
  SERVER_PID=$!
  API_READY=0
  for _ in {1..60}; do
    if curl -fsS "http://localhost:8080/api/events" >/dev/null 2>&1; then
      API_READY=1
      break
    fi
    sleep 2
  done
  if [[ "$API_READY" -ne 1 ]]; then
    echo "API server failed to start. See target/api-server.log"
    API_EXIT=1
  fi
fi

if [[ "$API_EXIT" -eq 0 ]]; then
  bash API_tests/run_api_tests.sh || API_EXIT=$?
fi

read_prop() {
  local file="$1"
  local key="$2"
  if [[ ! -f "$file" ]]; then
    echo "0"
    return
  fi
  awk -F= -v k="$key" '$1==k {print $2}' "$file"
}

read_json_number() {
  local file="$1"
  local key="$2"
  if [[ ! -f "$file" ]]; then
    echo "0"
    return
  fi
  local value
  value=$(grep -o '"'"$key"'"[[:space:]]*:[[:space:]]*[0-9]*' "$file" | awk -F: 'NR==1 {gsub(/[[:space:]]/, "", $2); print $2}')
  if [[ -z "$value" ]]; then
    echo "0"
  else
    echo "$value"
  fi
}

UNIT_TOTAL=$(read_prop "target/unit-test-summary.properties" "total")
UNIT_PASSED=$(read_prop "target/unit-test-summary.properties" "passed")
UNIT_FAILED=$(read_prop "target/unit-test-summary.properties" "failed")

FRONTEND_TOTAL=$(read_json_number "target/frontend-test-summary.json" "numTotalTests")
FRONTEND_PASSED=$(read_json_number "target/frontend-test-summary.json" "numPassedTests")
FRONTEND_FAILED=$(read_json_number "target/frontend-test-summary.json" "numFailedTests")

API_TOTAL=$(read_prop "target/api-test-summary.properties" "total")
API_PASSED=$(read_prop "target/api-test-summary.properties" "passed")
API_FAILED=$(read_prop "target/api-test-summary.properties" "failed")

TOTAL_ALL=$((UNIT_TOTAL + FRONTEND_TOTAL + API_TOTAL))
PASSED_ALL=$((UNIT_PASSED + FRONTEND_PASSED + API_PASSED))
FAILED_ALL=$((UNIT_FAILED + FRONTEND_FAILED + API_FAILED))

echo
echo "=== Unified Test Summary ==="
echo "backend_unit: total=$UNIT_TOTAL passed=$UNIT_PASSED failed=$UNIT_FAILED"
echo "frontend: total=$FRONTEND_TOTAL passed=$FRONTEND_PASSED failed=$FRONTEND_FAILED"
echo "api_functional: total=$API_TOTAL passed=$API_PASSED failed=$API_FAILED"
echo "TOTAL: total=$TOTAL_ALL passed=$PASSED_ALL failed=$FAILED_ALL"

if [[ "$UNIT_EXIT" -ne 0 || "$FRONTEND_EXIT" -ne 0 || "$API_EXIT" -ne 0 ]]; then
  exit 1
fi

exit 0
