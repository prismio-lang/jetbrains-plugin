## graphify

This project has a knowledge graph at `graphify-out/`. It is **local and untracked** (`.gitignore`): nothing in
`graphify-out/` is ever committed, and `graphify update .` after a change keeps the working copy current. For
codebase questions, run `graphify query "<question>"` first when `graphify-out/graph.json` exists; use
`graphify path "<A>" "<B>"` and `graphify explain "<concept>"` for relationships and focused concepts.
