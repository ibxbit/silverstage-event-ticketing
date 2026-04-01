#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Allow overriding SPRING_PROFILES_ACTIVE, default to 'test' if not set
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-test}"

# Run API functional tests in a Docker container (no Java required on host)
docker run --rm \
	-v "$PWD":/workspace \
	-w /workspace \
	-e SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
	maven:3.9.9-eclipse-temurin-8 \
	bash -c "mkdir -p target/api-functional-tests && javac -d target/api-functional-tests API_tests/ApiFunctionalTests.java && java -cp target/api-functional-tests APIFunctionalTests"
