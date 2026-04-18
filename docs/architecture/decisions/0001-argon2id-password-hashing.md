---
title: '0001 — Argon2id for password hashing'
parent: ADRs
grand_parent: Architecture
nav_order: 1
---

# 0001 — Argon2id for password and token hashing

- **Status:** Accepted
- **Date:** 2026-04-18
- **Deciders:** Pratiyush
- **Context link:** [T105 — Argon2id password hasher](https://github.com/Pratiyush/translately/issues/132)

## Context and problem statement

Translately authenticates users by email + password (T103) and issues two classes of server-verified token: API keys (T110, per-project) and personal access tokens (T110, per-user). All three carry a secret that the server must verify on every request, and none of them may be recoverable from the database if the DB is dumped.

We need a password-hashing scheme that is (a) slow enough to frustrate offline brute force on a stolen `secret_hash` column, (b) tunable over time as CPU/GPU cost drops, (c) side-channel resistant, (d) available in a mature JVM library with an acceptable license, and (e) not so slow that the `verify` path becomes a request-latency problem.

## Decision drivers

- **MIT-compatible licensing only.** The whole project is MIT (CLAUDE.md rule #4); dependencies must match.
- **Java 21 LTS runtime** (Quarkus). No native-image gotchas.
- **OWASP current guidance** — the scheme must be a recommendation in the current Password Storage Cheat Sheet, not a legacy workaround.
- **Parameter upgradeability.** The stored hash must embed its own parameters so we can harden over time without a schema migration.
- **Latency budget.** Targeted verify path: **30–60 ms** on modern server CPU. Above ~150 ms and the login endpoint becomes a DoS amplifier; below ~10 ms and the cost curve for an attacker is too gentle.

## Considered options

1. **bcrypt** — widely deployed, mature Java libraries (Spring Security, Bouncy Castle). Parameters embedded. Downside: max password length is 72 bytes (silently truncates); no memory-hardness, so GPU/ASIC attacks are cheap.
2. **scrypt** — memory-hard, predates Argon2. Works, but no longer the OWASP recommendation; fewer maintained JVM libraries; parameter tuning is finicky.
3. **PBKDF2** — FIPS-approved, simplest, broadly available. Not memory-hard; even high iteration counts do not stop a well-resourced attacker with GPUs. OWASP lists it as acceptable only when Argon2 / scrypt are unavailable.
4. **Argon2id** — winner of the [2015 Password Hashing Competition](https://password-hashing.net/); OWASP's current top recommendation; memory-hard; hybrid of data-independent (side-channel resistant) and data-dependent (brute-force resistant) variants.

## Decision outcome

**Chosen option: Argon2id**, because it's the OWASP-recommended default, provides memory-hardness that bcrypt lacks, and has a mature MIT-licensed Java binding (`de.mkammerer:argon2-jvm`) backed by the reference C implementation.

### Parameters

Following the [OWASP "m=64 MiB, t=3, p=4"](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) Argon2id guideline:

- **iterations (t):** 3
- **memory (m):** 65 536 KiB (64 MiB)
- **parallelism (p):** 4
- **salt length:** 16 bytes (library default)
- **output length:** 32 bytes (library default)

These values give ~30–60 ms per hash on a modern server CPU — well inside our latency budget and well outside an attacker's comfort zone for offline brute force.

### Storage

The `argon2-jvm` output string is self-describing — it embeds algorithm, version, parameters, salt, and hash in a single compact form:

```
$argon2id$v=19$m=65536,t=3,p=4$<salt>$<hash>
```

We store the full string in a single `VARCHAR(256)` column (`users.password_hash`, `api_keys.secret_hash`, `personal_access_tokens.secret_hash`). This means:

- **Parameter upgrades require no schema migration.** Future hashes use new parameters; old hashes still verify because `verify` reads them from the encoded string.
- **No separate salt column.** The salt rides in the encoded output.

### Consequences

- **Good** — state-of-the-art defence against stolen-DB brute force; side-channel resistance; single `VARCHAR` column; parameter upgrades without schema migrations; OWASP-current.
- **Neutral** — Each hash allocates 64 MiB transiently. On a server sized for Quarkus this is fine; on a 512 MiB container it's worth configuring login concurrency limits.
- **Bad** — `argon2-jvm` depends on a native library (`libargon2`) bundled as a JAR resource. Container images must not strip this. A pure-Java fallback (slower) exists and would be the first fallback if we ever needed to build a true statically-linked native image.

### Implementation notes

- Touched modules: `:backend:security` (`io.translately.security.password.PasswordHasher`).
- Shared by: user login (T103), API key verification (T110), PAT verification (T110), password-reset and email-verification token hashing (T103).
- Migration: none required — this is the first password hasher Translately ships.
- Rollback: drop the `argon2-jvm` dependency and replace with bcrypt. Every encoded hash carries its algorithm prefix, so a `verify` could dispatch to bcrypt for `$2a$`-prefixed rows and Argon2 for `$argon2id$` rows during a rollout.

### Progressive hardening

`PasswordHasher.verify` can return `true` **and** signal "this hash used weaker parameters than our current default" to the caller. On a successful login we then re-hash with current parameters and write back — users with old hashes transparently upgrade over time. Implementation of the signal is deferred until we first raise the defaults.

## Links

- OWASP Password Storage Cheat Sheet: <https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html>
- Argon2 RFC 9106: <https://datatracker.ietf.org/doc/html/rfc9106>
- `argon2-jvm` library: <https://github.com/phxql/argon2-jvm> (MIT)
- [Auth architecture](../auth.md)
- [PasswordHasher source](https://github.com/Pratiyush/translately/blob/master/backend/security/src/main/kotlin/io/translately/security/password/PasswordHasher.kt)
