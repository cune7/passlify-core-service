# Claude memory — portable setup

This folder is the **canonical, version-controlled copy** of Claude Code's project
memory. It travels with the repo via git so the same context is available on any machine.

Claude Code auto-loads memory from a machine-local, path-derived folder:

    ~/.claude/projects/<absolute-repo-path-with-slashes-as-dashes>/memory

To make that machine-local folder point at this repo copy, symlink it (one-time per machine):

```bash
# from the repo root
REPO="$(pwd)"
SLUG=$(echo "$REPO" | sed 's#/#-#g')      # absolute path, '/' -> '-'
MEMDIR="$HOME/.claude/projects/$SLUG/memory"

mkdir -p "$(dirname "$MEMDIR")"
rm -rf "$MEMDIR"                          # remove any local memory dir first
ln -s "$REPO/passlify-spec/memory" "$MEMDIR"
```

After this, edits Claude makes to memory land here in the repo — commit + push to sync.

Note: raw chat transcripts (`*.jsonl`) are intentionally NOT stored here — they stay
machine-local. Only curated memory facts live in the repo.