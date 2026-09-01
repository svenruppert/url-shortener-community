# Contributing to URL-Shortener Community Edition

Thank you for considering a contribution!

## License and dual licensing

The Community Edition is licensed under the **European Union Public Licence
v1.2 (EUPL-1.2)**. The project is developed under an **open-core /
dual-licensing model**: jCustos Ltd additionally offers the code base under
commercial license terms (e.g. for the Enterprise Edition).

## Developer Certificate of Origin (DCO)

Every contribution must be signed off against the
[Developer Certificate of Origin v1.1](https://developercertificate.org/).
Add a `Signed-off-by` line to each commit (use `git commit -s`):

```
Signed-off-by: Your Name <your.email@example.com>
```

By signing off you certify, per DCO v1.1, that you have the right to submit
the contribution under the EUPL-1.2.

## Additional license grant (dual licensing)

Because of the dual-licensing model, by submitting a contribution you
additionally grant jCustos Ltd a perpetual, worldwide, irrevocable,
royalty-free right to relicense and distribute your contribution under other
license terms, including commercial licenses. Your contribution always also
remains available under the EUPL-1.2. If you cannot or do not want to grant
this right, please say so in the pull request — the contribution can then not
be merged.

## Practical notes

- Source code, identifiers, comments, and commit messages are written in
  American English; commit messages follow the `<prefix>(V…/NNN): summary`
  convention used throughout the history.
- Tests use real implementations (no mock frameworks); run
  `./mvnw clean verify` before submitting.
- Existing views are evolved, never deleted and rebuilt.
