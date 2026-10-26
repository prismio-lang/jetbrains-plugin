## `graphify-out/` is tracked but never committed on refresh

`graphify-out/` (`graph.json`, `graph.html`, `GRAPH_REPORT.md`, `manifest.json`) is tracked by git, but a
refreshed one is **never staged or committed until the maintainer says so**: `graph.json` and `graph.html`
change on every `graphify update .`, and a commit per refresh would bloat the history. Run
`graphify update .` after a change to keep the working copy current, and leave `graphify-out/` out of every
commit. Commit by naming paths, never `git add -A` / `git commit -a`.

`.githooks/pre-commit` refuses a staged `graphify-out/` (`git config core.hooksPath .githooks` enables it;
`PRISMIO_ALLOW_GRAPH_COMMIT=1` overrides, only on the maintainer's word). This is the same rule as in the
compiler repository (`../prismio/CLAUDE.md`).

## graphify

This project has a knowledge graph at `graphify-out/`. For codebase questions, run
`graphify query "<question>"` first when `graphify-out/graph.json` exists; use `graphify path "<A>" "<B>"` and
`graphify explain "<concept>"` for relationships and focused concepts.
