# AGENTS.md instructions

Set up this workspace to use the installed token-saving MCP servers efficiently.

Use Serena as the default code-navigation MCP for semantic symbol search, edits, references, and project memory. Activate the current project with Serena first, then read Serena's instructions/manual before doing any coding work.

Use CocoIndex/ccc for fast codebase indexing and broad code search when I ask to "search the codebase," "find related code," or when repository-wide discovery would otherwise require reading many files.

Use jCodeMunch as the heavier structured retrieval MCP when Serena/CocoIndex are not enough, especially for assembling focused task context, ranked context bundles, file outlines, import/reference maps, and symbol source retrieval. Keep jCodeMunch lean: use core tools, compact schemas, local embeddings/signature fallback, no remote summarizer, no OpenAI key, no telemetry/session journal.

Token policy:
1. Prefer MCP retrieval over reading whole files.
2. Start with symbol/file outlines before opening source.
3. Pull only the smallest relevant snippets needed for the task.
4. Use ranked/context-bundle tools before broad manual searches.
5. Avoid full-repo scans unless a narrower MCP query fails.
6. Summarize retrieved context briefly before editing.
7. Do not use full jCodeMunch mode unless I explicitly ask.

When working in a repo, follow this order:
1. Serena activate_project.
2. Serena onboarding/memory check if needed.
3. Serena symbol search for targeted code work.
4. CocoIndex/ccc for broader search.
5. jCodeMunch for structured task context when the problem spans multiple files or dependencies.
6. Only then read full files as needed.

Confirm which MCPs are available, then proceed with the requested task using the lowest-token workflow.
