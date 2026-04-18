# Architecture Decision Records (ADRs)

This directory captures non-trivial technical decisions made during Translately's development. Each record is immutable once accepted — supersede rather than edit.

Format: [MADR 3.0](https://adr.github.io/madr/) (Markdown Any Decision Record). See [`_template.md`](_template.md) to start a new one.

## Index

| # | Title | Status | Date |
|---|---|---|---|
| [0001](0001-argon2id-password-hashing.md) | Argon2id for password and token hashing | Accepted | 2026-04-18 |

## Numbering

- Four-digit, monotonic, zero-padded: `0001-`, `0002-`, ...
- Filename: `NNNN-<kebab-case-title>.md`.
- Reserved: `0000-` is not used (avoid confusion with the template).

## When to write one

- Swapping a locked-in library or framework.
- Changing the auth / authorization / tenancy model.
- Introducing a new storage backend or index structure.
- Picking an algorithm with non-obvious trade-offs (crypto, search ranking, diff).
- Any performance or scale decision that rules out a simpler alternative.

When in doubt, write one — they're cheap to read and the audit trail is the whole point.
