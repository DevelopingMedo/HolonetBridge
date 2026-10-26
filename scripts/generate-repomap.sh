#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
command -v aider >/dev/null || {
    echo 'Install Aider first: uv tool install aider-chat==0.86.2' >&2
    exit 1
}

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

COLUMNS=240 aider --show-repo-map \
    --map-tokens 4096 --map-multiplier-no-files 1 \
    --model gpt-4o --no-show-model-warnings \
    --no-check-update --no-show-release-notes --no-analytics \
    --no-gitignore --no-auto-commits --no-pretty \
    > "$tmp_dir/output.txt"

# Strip the CLI banner and Aider-specific chat instructions from the export.
awk '
    /^Here are summaries of some files present in my git repository\.$/ { found=1; next }
    found && !body && /^$/ { body=1; next }
    body { sub(/[ 	]+$/, ""); print }
    END { if (!body) exit 1 }
' "$tmp_dir/output.txt" > "$tmp_dir/map.txt"
test -s "$tmp_dir/map.txt"

{
    printf '# Repository map\n\n'
    printf 'Generated with Aider; source HEAD before generation: `%s`.\n\n' "$(git rev-parse HEAD)"
    printf 'Selected code excerpts, not a complete API or a description of runtime behavior.\n'
    printf 'Regenerate with `bash scripts/generate-repomap.sh`. See [usage](repomap-guide.md).\n\n'
    printf '```text\n'
    cat "$tmp_dir/map.txt"
    printf '```\n'
} > "$tmp_dir/repomap.md"
mv "$tmp_dir/repomap.md" docs/repomap.md
printf 'Updated docs/repomap.md\n'
