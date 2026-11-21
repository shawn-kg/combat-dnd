#!/usr/bin/env bash
# Regenerates frontend/src/api/schema.d.ts from the running backend's OpenAPI spec.
# Run the backend first (./gradlew bootRun). openapi-typescript runs through npx because it
# needs TypeScript 5 and the frontend uses TypeScript 7.
# Not wired up yet: springdoc is added in roadmap step 3, when the API exists.
set -euo pipefail
cd "$(dirname "$0")/.."
SPEC_URL="${SPEC_URL:-http://localhost:8080/v3/api-docs}"
npx --yes openapi-typescript@7 "$SPEC_URL" -o frontend/src/api/schema.d.ts
echo "Wrote frontend/src/api/schema.d.ts"
