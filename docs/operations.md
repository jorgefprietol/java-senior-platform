# Local operation and failure boundaries

The stack is intentionally loopback-only. Start it from the repository root after bootstrap. Allow up to three minutes for identity initialization on an empty database. Application readiness is checked by Compose; the verification script also waits for an authenticated request before beginning.

## Diagnosing failures

| Symptom | Check | Recovery |
| --- | --- | --- |
| Browser cannot sign in | `docker compose logs --tail 80 identity` | Check identity database health and that localhost:8180 is free. |
| Account API unavailable | `docker compose ps` and ledger logs | Correct configuration; restart ledger. Do not delete volumes to fix a transient failure. |
| Ledger works but audit is delayed | Rabbit health, publisher warnings, audit logs | Restore broker/consumer; pending outbox rows are retried. |
| Concurrent request returns 409 | Account version changed or key reused | Retry the same request with its original Idempotency-Key; changed payload requires a new key. |
| Poison event reaches dead queue | Inspect schema version and consumer exception | Correct the cause, then replay under an operator procedure; do not blindly loop retries. |

The publisher uses a short, bounded batch, database row locks and publisher confirms. It can run in multiple replicas, but holds a database transaction while waiting for RabbitMQ. Broker latency therefore consumes a connection. Size the pool and batch against measured throughput before scaling.

The audit projection is eventually consistent and idempotent by event ID. It preserves individual events and does not assume arrival order. It is not the source of truth for spending decisions. Movements and their resulting balances are immutable.

## Data retention and recovery

`docker compose down` preserves data. `docker compose down -v` is an explicit destructive reset of this project's named volumes. Bootstrap secrets must be retained securely with local data: changing passwords in .env does not rotate existing PostgreSQL users or imported Keycloak users.

Before production, define RPO/RTO, schedule encrypted PostgreSQL backups and perform isolated restoration drills. Back up the ledger source of truth and identity data; document whether audit data is restored or rebuilt. This local senior example does not claim a tested production disaster recovery plan. The architecture examples cover that separately.

Health endpoints and ECS logs are available inside the service network. Management endpoints are not proxied publicly. There is no production metrics, tracing or on-call platform configured in this example. Real deployment requires request/error/latency metrics, outbox age and dead-queue alerts, and retention policies.

## Security updates

CI fails on HIGH/CRITICAL findings in application runtime images and on high npm audit findings. Reports are artifacts of the specific run, not a permanent claim that an image is safe. Rebuild regularly: image tags and vulnerability databases evolve. The Java runtime updates Alpine packages during build. Explicit Tomcat and RabbitMQ client overrides address findings that preceded an updated Spring Boot dependency BOM; review and remove overrides once the BOM includes equivalent fixes.

Third-party infrastructure images, the identity configuration, TLS termination and deployment infrastructure need their own patch policy and scan review. OWASP ASVS mapping in security.md records implementation evidence and remaining deployment responsibilities.
