#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
required_files=(
  "README.md"
  "backend/pom.xml"
  "backend/src/main/resources/application.yml"
  "frontend/package.json"
  "frontend/src/main.ts"
  "database/schema.sql"
  "docs/03-阶段任务计划.md"
)

for relative_path in "${required_files[@]}"; do
  if [[ ! -f "$project_root/$relative_path" ]]; then
    echo "Missing required file: $relative_path" >&2
    exit 1
  fi
done

if rg -n --hidden --glob '!scripts/check-structure.sh' '(sk-[A-Za-z0-9]{20,}|BEGIN (RSA|OPENSSH) PRIVATE KEY)' "$project_root"; then
  echo "Potential secret detected." >&2
  exit 1
fi

table_count="$(rg -c '^CREATE TABLE' "$project_root/database/schema.sql")"
if [[ "$table_count" -lt 15 ]]; then
  echo "Expected at least 15 database tables, found $table_count." >&2
  exit 1
fi

echo "Structure check passed ($table_count tables)."
