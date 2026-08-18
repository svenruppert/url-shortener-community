# Legacy import fixtures

`LegacyImportFixtureIT` verifies that **real exports from the old system** still
import cleanly through the current importers.

Fixtures are **not** stored here — they are read from the repo-root `_backup/`
tree (which is git-ignored, so real URLs/statistics never enter VCS), e.g.:

```
_backup/prod/v00.15.00/urlshortener-export-<ts>.zip              # links
_backup/prod/v00.15.00/urlshortener-statistics-export-<ts>.zip   # statistics
```

The test walks up to the repo-root `_backup/`, picks the **newest** matching
export per type (by timestamped filename), and imports it against a fresh
in-process server. If no backup is present, each test **skips itself** (JUnit
assumption), so CI and clean checkouts stay green.
