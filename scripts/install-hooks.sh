#!/bin/sh
# Point this repo's git at .githooks so pre-commit (tests) run automatically.
# Run from repo root: ./scripts/install-hooks.sh

cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
echo "Hooks installed. Pre-commit will run unit tests (./gradlew test)."
