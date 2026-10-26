# Aider repository map

[repomap.md](repomap.md) is a generated overview of selected files and symbols.
It helps a coding agent choose which source files to read next. It is not a
complete class list, call graph, or verified explanation of behavior.

## Generate

Install [uv](https://docs.astral.sh/uv/getting-started/installation/) if needed,
then run from the repository root:

```sh
uv tool install aider-chat==0.86.2
bash scripts/generate-repomap.sh
```

Ensure uv's executable directory is on your PATH (`uv tool update-shell` can
configure this). The script requires Bash, Git, and standard Unix tools, as
available on macOS and Linux.

The export uses Aider's `--show-repo-map`; no model request or API key is needed.
`gpt-4o` selects the tokenizer and context settings for the export, not a model
that is called. Aider may download metadata or tokenizer data on first use.
Analytics and update checks are disabled for this command.

The map has a suggested budget of 4,096 tokens with automatic expansion disabled.
It includes selected excerpts from tracked source and test files; relevance is
ranked without a task-specific chat. Paths are printed at a wider terminal width
to avoid splitting long Java package names. `.aiderignore` excludes the generated
map itself. Local histories and parser caches stay outside version control.

Regenerate after switching branches or making relevant code changes. Aider reads
working-tree contents; the header records HEAD before generation, so commit or
check your source changes when sharing a snapshot. The checked-in map is a sample
for this experiment, not something CI keeps synchronized.

## Use with an agent

Give the agent the map as orientation, then have it open the relevant source and
tests before deciding what to change. Missing entries do not mean code is absent.
Method names and signatures do not prove what the implementation actually does.
An exported map has no automatic effect on OpenCode or another coding agent; it
must be explicitly read or supplied as context.

For this experiment, compare a concrete task with and without the map: does the
agent locate the responsible classes faster and reuse existing code? Keep it only
if that benefit justifies the extra context and snapshot maintenance.
