# backend/

Quarkus 3 · Kotlin · Java 21 · Gradle (Kotlin DSL) multi-module.

## Modules

| Module | Purpose | Depends on |
|---|---|---|
| `api` | JAX-RS resources (REST) | `service`, `security` |
| `data` | JPA entities + Panache repos + Flyway migrations | — |
| `service` | Use-case logic | `data`, `security`, all adapter modules |
| `security` | JWT, OIDC, LDAP, CryptoService (envelope encryption) | — |
| `jobs` | Quartz scheduled + DB-queued jobs | `service` |
| `ai` | `AiTranslator` port + per-vendor adapters (BYOK) | — |
| `mt` | `MachineTranslator` port + adapters (DeepL, Google, AWS) | — |
| `storage` | S3-compatible object storage | — |
| `email` | Mailer + Qute templates | — |
| `webhooks` | Outgoing webhook delivery (HMAC, retries) | — |
| `cdn` | JSON bundle export → S3 signed URLs | — |
| `audit` | Immutable append-only audit log | — |
| `app` | Quarkus main + wiring + OpenAPI gen + ArchUnit tests | everything |

Module boundaries are enforced as tests in `:backend:app` via ArchUnit — see [.kiro/steering/architecture.md](../.kiro/steering/architecture.md).

## Common tasks

```bash
./gradlew projects                              # list the module tree
./gradlew build                                 # compile + test all modules
./gradlew :backend:app:quarkusDev               # run the app in dev mode (hot reload)
./gradlew :backend:data:test                    # test one module
./gradlew ktlintCheck detekt                    # lint + static analysis
./gradlew jacocoTestReport                      # coverage reports
```

## Package layout

Every module writes its Kotlin code under `src/main/kotlin/io/translately/<module>/`. Test code under `src/test/kotlin/io/translately/<module>/`. Resources (application.yml, migrations, Qute templates) under `src/main/resources/`.

Only `:backend:data` uses `src/main/resources/db/migration/` (Flyway SQL) and `:backend:app` uses `src/main/resources/application.yml`.
