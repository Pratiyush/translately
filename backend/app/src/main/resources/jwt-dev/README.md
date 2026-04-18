# JWT keypair — DEV / TEST ONLY

These PEM files are committed to the repo for local development and CI
convenience. They are **not** secret and **must not** be used in production.

- `private.pem` — RSA 2048 private key, PKCS#8 unencrypted
- `public.pem` — matching public key

The `%prod` Quarkus profile in `application.yml` overrides these with paths
provided by the operator. See `infra/.env.prod.example` for the env vars.

## Generating a fresh prod keypair

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out private.pem
openssl rsa -pubout -in private.pem -out public.pem
chmod 600 private.pem
```

Distribute `private.pem` **only** to the backend hosts (never check in). The
public key can be published — it's the client-side JWT verifier's input too,
via the `/q/openapi` spec or a `.well-known` endpoint in a later phase.
