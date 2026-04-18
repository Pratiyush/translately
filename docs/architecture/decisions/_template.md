---
title: ADR template (not an ADR)
parent: ADRs
grand_parent: Architecture
nav_exclude: true
---

# NNNN — Title of decision

- **Status:** Proposed | Accepted | Superseded by `NNNN-…` | Deprecated
- **Date:** YYYY-MM-DD
- **Deciders:** Pratiyush
- **Context link:** `https://github.com/Pratiyush/translately/issues/<N>` (fill in on adoption)

## Context and problem statement

What forced this decision? Describe the pressure: a requirement, a constraint, an incident, a performance bound. One or two paragraphs; no solution yet.

## Decision drivers

- Constraint A (e.g. MIT-only dependencies)
- Constraint B (e.g. Java 21 LTS)
- Non-functional goal C (e.g. < 50 ms p95 on the hot path)

## Considered options

1. Option 1 — one-line description
2. Option 2 — one-line description
3. Option 3 — one-line description

## Decision outcome

**Chosen option:** *Option N*, because *one-sentence rationale.*

### Consequences

- **Good:** benefit 1, benefit 2.
- **Neutral:** implication 1, implication 2.
- **Bad:** drawback 1, drawback 2 — mitigated by *...*.

### Implementation notes

- Touched modules: `backend/foo`, `webapp/src/bar`.
- Migration: *how existing data / callers are moved over.*
- Rollback: *what reverting looks like, if viable.*

## Links

- Referenced PR: `https://github.com/Pratiyush/translately/pull/<N>`
- [Relevant docs](../README.md)
