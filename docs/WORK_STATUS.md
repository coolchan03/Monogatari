# Monogatari live work checkpoint

- Active batch: Stage A/B, Steps 3-5 of 74 (CI release build, app identity, visible branding)
- Current operation: Step 3 - R8 release CI and keep rules patched; checking diff and pushing isolated commit for CI verification
- Tunnel: Access verified; only Tunnel to Surface may edit or push GitHub code
- Working branch: batch-03-05-safety-identity
- Starting commit: 29ed10076
- Last verified: Local repo and existing release workflow inspected; original untracked .release-temp/ and source-audit/ left untouched
- Tests: Not yet run for this batch
- Last verified remote state: devel points to Step 2 commit 29ed10076 (before this batch)
- Next operation: Validate Step 3 diff; commit, push and observe CI; then prepare Step 4 identity change without installing the APK until backup
- Device-dependent gates: Back up existing app before installing a new package ID; signed release requires signing credentials
- Interruption note: This file reflects the latest checkpoint written, not an exact timeout moment. Review git status and this file before resuming.

## Session rules
1. On every new user request to work on Monogatari, request Tunnel to Surface access as the first tool action. If not authorized, request permission before any other task.
2. Do not edit repositories through the GitHub connector. All reads/writes, commits, merges and pushes must happen through Tunnel to Surface.
3. Combine compatible original master-guide steps into meaningful work batches while preserving separate task statuses, validations and recovery commits.
4. State the exact active step, file/operation, completed operations, and test status in user-visible updates; update this checkpoint before long or risky operations.
5. Never claim a passing build, successful push, live device test or completed stage unless it was actually verified.
