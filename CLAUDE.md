# CLAUDE.md

Antes de qualquer alteração neste repositório, leia:

1. [`AGENTS.md`](AGENTS.md) — instruções e regras do projeto para agentes de IA.
2. [`.agents/skills/`](.agents/skills/) — skills específicas do projeto (revisão de código, TDD, desenvolvimento Java, design mobile/web, ADRs, etc.).
3. [`.cursor/skills/ui-ux-pro-max`](.cursor/skills/ui-ux-pro-max) — skill UI/UX Pro Max (design system / recomendações visuais).
   - [`.agents/skills/impeccable`](.agents/skills/impeccable) — skill Impeccable (design, crítica, auditoria e polimento de UI web/mobile); symlink em `.claude/skills/impeccable`, versionada em `skills-lock.json`.
4. OpenSpec: [`.cursor/skills/openspec-*`](.cursor/skills/) e [`.claude/skills/openspec-*`](.claude/skills/); specs em [`openspec/`](openspec/). Comece uma mudança com `/openspec-propose`.
5. pode fazer commit,mas nunca fazer push ou pr sem aprocacao humana.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. When the answer is under a Markdown heading, `graphify section "<heading>"` prints that section's text, so the .md need not be opened. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.

## Project map (token skill — graphify)
`graphify-out/graph.json` in this project is a code map. Goal: don't re-scan the project, spend fewer tokens.
- For code/architecture questions (where is it, what calls it, how does the flow go), BEFORE the first grep/read: `bash ~/.claude/skills/token/scripts/token.sh query "<question>" .` (it updates a stale map first, so answers never point at old names or lines). For a relationship use `graphify path "A" "B"`, for a single concept `graphify explain "X"`. Open raw files only for the lines the query points to. Pass this rule to every sub-agent that explores code.
- NEVER read/cat `graphify-out/graph.json` as a whole (hundreds of thousands of tokens). Use `GRAPH_REPORT.md` only for a high-level architecture overview.
- After changing code: `bash ~/.claude/skills/token/scripts/token.sh update .` (AST, no LLM, seconds). If the map is missing or "STALE", run `update` first.
- If the map looks wrong/incomplete, tell the user; don't silently fall back to raw scanning.


# >>> token-skill (auto-generated; do not edit) >>>
## Project map (token skill — graphify)
`graphify-out/graph.json` in this project is a code map. Goal: don't re-scan the project, spend fewer tokens.
- For code/architecture questions (where is it, what calls it, how does the flow go), BEFORE the first grep/read: `bash ~/.claude/skills/token/scripts/token.sh query "<question>" .` (it updates a stale map first, so answers never point at old names or lines). For a relationship use `graphify path "A" "B"`, for a single concept `graphify explain "X"`. Open raw files only for the lines the query points to. Pass this rule to every sub-agent that explores code.
- NEVER read/cat `graphify-out/graph.json` as a whole (hundreds of thousands of tokens). Use `GRAPH_REPORT.md` only for a high-level architecture overview.
- After changing code: `bash ~/.claude/skills/token/scripts/token.sh update .` (AST, no LLM, seconds). If the map is missing or "STALE", run `update` first.
- If the map looks wrong/incomplete, tell the user; don't silently fall back to raw scanning.
# <<< token-skill <<<
