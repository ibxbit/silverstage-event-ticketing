#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Allow overriding SPRING_PROFILES_ACTIVE, default to 'test' if not set
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-test}"
SILVERSTAGE_BASE_URL="${SILVERSTAGE_BASE_URL:-http://localhost:8080}"

if command -v javac >/dev/null 2>&1; then
	mkdir -p target/api-functional-tests
	javac -d target/api-functional-tests API_tests/ApiFunctionalTests.java
	SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" SILVERSTAGE_BASE_URL="$SILVERSTAGE_BASE_URL" \
		java -cp target/api-functional-tests ApiFunctionalTests
	exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
	echo "Neither javac nor docker is available. Install JDK 17+ or Docker to run API functional tests."
	exit 1
fi

docker run --rm \
	-v "$PWD":/workspace \
	-w /workspace \
	--add-host host.docker.internal:host-gateway \
	-e SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
	-e SILVERSTAGE_BASE_URL="${SILVERSTAGE_BASE_URL/http:\/\/localhost/http:\/\/host.docker.internal}" \
	maven:3.9.9-eclipse-temurin-17 \
	bash -c "mkdir -p target/api-functional-tests && javac -d target/api-functional-tests API_tests/ApiFunctionalTests.java && java -cp target/api-functional-tests ApiFunctionalTests"
