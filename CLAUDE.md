# CLAUDE.md — BabakCast

## Project Overview

BabakCast is an Android podcast player application built with Kotlin and Jetpack Compose. It provides a clean, modern interface for podcast discovery, playback, and management.

- **Language / Runtime**: Kotlin 2.x
- **Framework**: Jetpack Compose, Android SDK
- **Architecture**: Clean Architecture + MVVM
- **Build**: Gradle (Kotlin DSL)
- **Package / Namespace**: `com.cocode.babakcast`

---

## Required Skills — ALWAYS Invoke These

| Situation | Skill |
|-----------|-------|
| Before any new feature or screen | `superpowers:brainstorming` |
| Planning multi-step changes | `superpowers:writing-plans` |
| Writing or fixing core logic | `superpowers:test-driven-development` |
| First sign of a bug or failure | `superpowers:systematic-debugging` |
| Before completing a feature branch | `superpowers:requesting-code-review` |
| Before claiming any task done | `superpowers:verification-before-completion` |
| Working on UI / frontend | `frontend-design:frontend-design` |
| After implementing — reviewing quality | `simplify` |

---

## Architecture

```
BabakCast/
├── app/
│   └── src/main/
│       ├── java/com/cocode/babakcast/
│       │   ├── data/       ← Data sources, repositories
│       │   ├── domain/     ← Business logic, use cases
│       │   └── ui/         ← Compose screens, ViewModels
│       └── res/            ← Resources
├── build.gradle.kts        ← App build config
└── version.txt             ← Semantic version
```

### Layer Rules
- `ui/` may only import from `domain/`
- `domain/` must never import from `ui/` or `data/`
- `data/` implements `domain/` interfaces

---

## Coding Conventions

- [ ] All models are **immutable** — use `copy()` for mutations
- [ ] Functions are **pure** where possible
- [ ] State is a single source of truth per feature
- [ ] No hardcoded strings — use string resources
- [ ] Strict Kotlin typing everywhere

---

## Engineering Principles

### File Size
- **200-line maximum per file**

### DRY · SOLID · KISS · YAGNI
- Extract shared logic into named utilities
- Single Responsibility per class/function
- Don't add features not yet needed

### TDD
- Write failing test first, then make it pass

### Commit hygiene
- Conventional Commits enforced by commit-msg hook

---

## Build Commands

```bash
./gradlew buildSmoke --no-daemon          # Smoke check (CI + pre-commit)
./gradlew assembleDebug --no-daemon       # Build debug APK
./gradlew testDebugUnitTest --no-daemon   # Unit tests
./gradlew lintDebug --no-daemon           # Lint
```

---

## Key Files

| File | Purpose |
|------|---------|
| `CLAUDE.md` | This file |
| `version.txt` | Semantic version (MAJOR.MINOR.PATCH) |
| `.github/workflows/` | CI, release APK, Pages |
| `.githooks/` | Pre-commit and commit-msg hooks |
| `scripts/install-hooks.sh` | One-time hook installer |
| `scripts/setup-repo.sh` | Branch protection setup (run once) |

---

## Starting a New Session

1. Read this file
2. Run `./gradlew buildSmoke --no-daemon` to confirm everything passes
3. Invoke `superpowers:brainstorming` before touching any feature
4. Follow the Required Skills table — every skill is mandatory
