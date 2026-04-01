#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Allow overriding SPRING_PROFILES_ACTIVE, default to 'test' if not set
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-test}"
SILVERSTAGE_BASE_URL="${SILVERSTAGE_BASE_URL:-http://host.docker.internal:8080}"

# Run API functional tests in a Docker container (no Java required on host)
docker run --rm \
	-v "$PWD":/workspace \
	-w /workspace \
	--add-host host.docker.internal:host-gateway \
	-e SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
	-e SILVERSTAGE_BASE_URL="$SILVERSTAGE_BASE_URL" \
	maven:3.9.9-eclipse-temurin-17 \
	bash -c "mkdir -p target/api-functional-tests && javac -d target/api-functional-tests API_tests/ApiFunctionalTests.java && java -cp target/api-functional-tests ApiFunctionalTests"
