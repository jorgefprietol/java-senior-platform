# Security verification scope

Reference: OWASP ASVS 5.0, https://owasp.org/www-project-application-security-verification-standard/ .

ASVS is a verification standard, not a library or a guarantee. The table maps applicable control families to implementation and tests. No external assessment or full ASVS certification is claimed. Infrastructure and organizational requirements remain outside the local demo.

| Area | Implemented control | Verification |
| --- | --- | --- |
| Authentication | Keycloak, short-lived signed JWTs, PKCE, password grant disabled | Browser login; anonymous and forged-token API cases |
| Authorization | Operator role and signed-subject ownership | Two independent QA identities; denied reads and writes |
| Token validation | Fixed issuer and expected ledger-api audience | Resource-server configuration; negative token test |
| Input validation | DTO constraints, size/precision limits, UUID parsing | Invalid name, currency, UUID, header, amount cases |
| Injection | JPA/JDBC bound parameters | Source review of all SQL statements |
| Browser execution | Angular interpolation, CSP, no innerHTML binding | Security headers; accessibility/browser tests |
| Business integrity | Exact decimals, optimistic version, DB checks, idempotency | Duplicate/mismatched requests, concurrent withdrawals |
| Event integrity | Durable queue, mandatory routing, confirms, transactional outbox, idempotent insert | Broker outage and duplicate-delivery exercises |
| Error handling | Problem Details; unexpected errors expose only an incident ID | Invalid and rejected API request cases |
| Secrets | Cryptographic local generation; ignored .env and realm import; no personal keys | Repository exclusions and secret scan |
| Resource protection | Body/header limits, bounded queries/pools, edge rate limits, memory caps | Configuration review; request/response tests |
| Dependencies | Locked npm dependencies and pinned toolchain versions | npm audit, dependency/image scan pipeline |
| Auditability | Immutable movement log, separate read model, structured service errors | Ledger/audit reconciliation |

## Required before public or production deployment

Use trusted HTTPS certificates and production Keycloak mode. Enforce MFA according to risk and organization policy. Remove QA service clients. Use separate database principals with migration privileges separated from runtime DML. Manage secrets through an external secret store with rotation, protect backups and test restoration. Add external tamper-resistant audit retention and alerting, service-to-service transport encryption and network policy. Audit data retention and export against the applicable business rules. Validate abuse controls behind the actual trusted proxy chain. Test the complete ASVS requirement set applicable to the deployed architecture.

The local Compose must not be exposed to the Internet. Loopback bindings limit the demo entry points, but local applications with access to the generated secrets can use the services. No claim of compliance with financial regulation is made.
