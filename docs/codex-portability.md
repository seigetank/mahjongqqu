# Codex Conversation Portability

This project is intended to move across multiple computers. The repository should
contain source code, project documentation, and filtered Codex handoff archives,
but not raw local Codex state.

## Path Policy

Committed files should use repository-relative paths or environment-derived
paths.

Allowed examples:

- `./docs/project-plan.md`
- `codex/conversations/`
- `$HOME`
- `$PSScriptRoot`
- `git rev-parse --show-toplevel`
- Environment variables

Do not commit:

- Machine-specific Windows user-home absolute paths
- Android SDK absolute paths
- JDK absolute paths
- Codex internal absolute paths
- Local keystore paths
- Personal tokens or credential files

## Archive Policy

Raw Codex Desktop session files can include internal instructions, tool payloads,
tool outputs, local paths, and logs. Do not commit them directly.

Use:

```powershell
.\scripts\export-codex-conversation.ps1
```

The export script stores only user-visible user and assistant messages and
replaces the repository root with `${PROJECT_ROOT}`.

## Before Moving Computers

```powershell
.\scripts\export-codex-conversation.ps1
.\scripts\check-portable-paths.ps1
git status
```

Commit the latest filtered JSON under `codex/conversations/`.

## Resume On Another Computer

```powershell
git clone <github-repository-url>
cd <repository-directory>
```

Then review:

- `AGENTS.md`
- `docs/project-plan.md`
- Latest `codex/conversations/*-visible-conversation.json`

This does not restore the Codex UI session directly. It preserves enough visible
project context to continue safely.
