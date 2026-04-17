# Security policy

Thanks for helping keep Translately and its users safe.

## Supported versions

Translately is pre-1.0. During the `v0.x` window, only the latest released minor version receives security fixes. Once `v1.0.0` ships, the policy will expand to the current major and the previous major for a 6-month overlap.

| Version | Supported |
|---|---|
| `0.x` (latest minor) | ✅ |
| `0.x` (older minor) | ❌ |

## Reporting a vulnerability

**Please do not file a public GitHub issue for security reports.**

Use one of the following private channels:

1. **GitHub Security Advisories** (preferred) — open a private report at
   <https://github.com/Pratiyush/translately/security/advisories/new>.
2. **Email** — send details to **pratiyush1@gmail.com** with the subject line
   `[translately-security] <short summary>`.

### What to include

- A clear description of the issue and its impact.
- Steps to reproduce (proof-of-concept, curl commands, screenshots).
- Affected version (`git rev-parse HEAD` or release tag).
- Your suggested remediation, if any.
- Whether you want public credit (and how you'd like to be credited).

## Response expectations

| Milestone | Target |
|---|---|
| Acknowledgement | within **3 business days** |
| Triage + severity assessment | within **7 business days** |
| Fix released (critical / high) | within **30 days** |
| Fix released (medium / low) | within **90 days** |
| Public advisory | after fix is released and users have had reasonable time to upgrade |

We follow **coordinated disclosure** and will credit reporters in the published advisory unless they request otherwise.

## Scope

In scope:

- Remote code execution, authentication bypass, privilege escalation
- SQL injection, command injection, SSRF, XXE
- Stored/reflected XSS, CSRF
- Insecure-by-default configuration shipped in the repo or Docker images
- Secrets or keys leaked by the platform itself
- Cryptographic weaknesses in how the platform encrypts AI keys, tokens, or PII

Out of scope:

- Vulnerabilities in **your own deployment's** third-party services (your IdP, your SMTP, your cloud).
- Missing security headers on self-hosted deployments the operator has control of.
- Social-engineering or physical-security attacks against project maintainers.
- Denial of service that requires privileged access.

## Hardening guidance

See [docs/self-hosting/hardening.md](docs/self-hosting/hardening.md) (published at v0.1.0) for the current hardening checklist — reverse proxy, TLS, rate limits, secret storage, backup/restore, audit log retention.

## Safe harbor

Good-faith security research performed within the scope above is welcome. We will not pursue legal action against researchers who:

- Do not access, modify, or exfiltrate data that is not their own.
- Do not degrade the availability of the service.
- Give us reasonable time to respond before public disclosure.

Thanks for making Translately safer.
