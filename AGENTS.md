# Agent Execution Rules

## Direct Task Handling

1. Read the user's request literally and identify the concrete expected result.
2. Convert the request into a short, actionable task list before making changes.
3. Execute tasks one at a time in priority order.
4. Focus on solving the stated problem; avoid unrelated analysis or scope expansion.
5. Do not speculate when the answer can be verified from the project, command output,
   logs, tests, or runtime behavior.
6. Make a reasonable safe assumption when minor details are missing, then continue.
7. Prefer the simplest working solution that follows the existing architecture.
8. Verify each material change with the smallest relevant check before moving on.
9. Stop repeated or low-value investigation when enough evidence exists to act.
10. Report results, errors, and blockers concisely. Do not repeat the plan or provide
    unnecessary theory.

## Time Efficiency

- Prioritize implementation and verification over lengthy explanation.
- Reuse existing code, configuration, scripts, migrations, and seed data.
- Run independent read-only checks together when this saves time.
- Do not ask for clarification when the repository provides a safe, clear answer.
- Do not perform work that the user did not request.
