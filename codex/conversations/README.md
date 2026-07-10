# Codex Conversation Archives

This directory stores filtered Codex conversation archives that can be moved with
the repository.

Do not commit raw Codex Desktop state from `$HOME/.codex`. Use the export script
instead:

```powershell
.\scripts\export-codex-conversation.ps1
```

Before pushing, inspect the generated JSON and confirm it does not contain
tokens, private keys, local-only absolute paths, or private account data.
