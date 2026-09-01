# Monorepo migration — commands to run when you're ready

**Nothing here has been run.** No git history has been touched. This is a handover so the decision and the timing stay yours.

## Where things stand

| | Repo root | Remote | State |
|---|---|---|---|
| Backend | `backend/.git` | `CarlosEMenezes/BugeTracker` | on `main`, had ~10 uncommitted deletions before the restructure |
| Frontend | `frontend/.git` | `Kauakb/BugeTracker-frontEnd` | on `main`, clean before the restructure |
| Root | *not a repo* | — | — |

Both remotes carry the "Buge" typo (`BugeTracker`, not `BudgetTracker`).

The restructure **moved each `.git` directory along with its code** — `BackEnd/.git` → `backend/.git`, `FrontEnd/.git` → `frontend/.git` — so both histories and both remotes are intact. Each repo will show the restructure as a large set of renames plus deletions; run `git -C backend status` and `git -C frontend status` to see it.

## Option A — one monorepo at the root (recommended)

Back up first. This discards the two nested histories locally.

```bash
cd "c:/Users/Cadu/Desktop/Personal Projects/BudgetTracker"

# 1. Back up both histories somewhere outside the project.
cp -r backend/.git  ../BudgetTracker-backend-history.git
cp -r frontend/.git ../BudgetTracker-frontend-history.git

# 2. Drop the nested repos.
rm -rf backend/.git frontend/.git

# 3. One repo at the root.
git init -b main
git add .
git commit -m "Restructure into a monorepo and fix the build toolchain"

# 4. Point at a single new remote.
git remote add origin https://github.com/CarlosEMenezes/BudgetTracker.git
git push -u origin main
```

## Option B — keep both histories, joined with subtree

Preserves every commit, at the cost of a more tangled graph.

```bash
cd "c:/Users/Cadu/Desktop/Personal Projects/BudgetTracker"

# Commit the restructure inside each repo first, so the subtree merges have something to point at.
git -C backend  add -A && git -C backend  commit -m "Restructure for monorepo"
git -C frontend add -A && git -C frontend commit -m "Restructure for monorepo"

mv backend  ../bt-backend-tmp
mv frontend ../bt-frontend-tmp

git init -b main
git add . && git commit -m "Root: CLAUDE.md, ADRs, CI, gitignore"

git subtree add --prefix=backend  ../bt-backend-tmp  main
git subtree add --prefix=frontend ../bt-frontend-tmp main

rm -rf ../bt-backend-tmp ../bt-frontend-tmp
```

## Option C — leave the two repos separate

Nothing to run. The cost is that CI must be configured twice, and a change spanning both sides cannot be one atomic commit or one pull request. `.github/workflows/ci.yml` at the root assumes a single repo, so under this option it has to be split and moved into each.

## Note on the old root `.gitignore`

It contained `**/*`, which ignored every file in the project. It has been replaced. If you ever `git init` at the root against a stale copy of that file, `git add .` will silently stage nothing.
